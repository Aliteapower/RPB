package com.rpb.reservation.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.WalletFundingService;
import com.rpb.reservation.wallet.funding.persistence.WalletFundingRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class WalletFundingMigrationPostgresTest {
 @Test void populatedLegacyBalancePendingRefundAndOriginalBillRemainPrincipalAfterCutover() throws Exception {
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(x->x.startsWith("port=")).findFirst().orElseThrow().substring(5);
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  String schema="wallet_cutover_"+UUID.randomUUID().toString().replace("-","");
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+schema);
  var ds=new DriverManagerDataSource(url+"&currentSchema="+schema+",public","postgres","");var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  try {
   List<Path> migrations;try(var files=Files.list(Path.of("src/main/resources/db/migration"))){migrations=files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList();}
   for(var file:migrations)if(file.getFileName().toString().compareTo("V059__")<0)jdbc.execute(Files.readString(file));
   UUID tenant=UUID.randomUUID(),issuer=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID(),program=UUID.randomUUID(),account=UUID.randomUUID(),topup=UUID.randomUUID(),bill=UUID.randomUUID(),refund=UUID.randomUUID(),actor=UUID.randomUUID();
   jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"切换测试");
   jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'main','主体','active')",issuer,tenant);
   jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','门店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);
   jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'C','regular','顾客','+6581112468','active')",customer,tenant);
   jdbc.update("insert into wallet_programs(id,tenant_id,operating_entity_id) values(?,?,?)",program,tenant,issuer);
   jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,store,issuer);
   jdbc.update("insert into wallet_accounts(id,tenant_id,program_id,customer_id,balance_cents,reserved_cents,version) values(?,?,?,?,8000,2000,3)",account,tenant,program,customer);
   jdbc.update("insert into wallet_topups(id,tenant_id,program_id,account_id,store_id,amount_cents,channel,status,created_by) values(?,?,?,?,?,10000,'cash','credited',?)",topup,tenant,program,account,store,actor);
   jdbc.update("insert into wallet_bills(id,tenant_id,program_id,account_id,store_id,amount_cents,wallet_paid_cents,status,wallet_applied,code_hash,created_by) values(?,?,?,?,?,2000,2000,'paid',true,?,?)",bill,tenant,program,account,store,UUID.randomUUID().toString(),actor);
   jdbc.update("insert into wallet_entries(tenant_id,program_id,account_id,store_id,actor_id,kind,source_id,amount_cents,before_cents,after_cents,account_version) values(?,?,?,?,?,'topup_credit',?,10000,0,10000,1),(?,?,?,?,?,'bill_debit',?,2000,10000,8000,2)",tenant,program,account,store,actor,topup,tenant,program,account,store,actor,bill);
   jdbc.update("insert into wallet_refunds(id,tenant_id,program_id,account_id,store_id,topup_id,amount_cents,wallet_cents,status,reason,created_by) values(?,?,?,?,?,?,2000,2000,'pending','原本金退款',?)",refund,tenant,program,account,store,topup,actor);
   for(var file:migrations)if(file.getFileName().toString().compareTo("V059__")>=0)jdbc.execute(Files.readString(file));
   var scope=new StoreScope(new TenantId(tenant),store);var repo=new WalletAccountRepository(jdbc,new ObjectMapper().findAndRegisterModules());var lots=new WalletFundingRepository(jdbc);var funding=new WalletFundingService(lots,repo,new WalletLedgerRepository(jdbc));
   var a=repo.account(scope,account,false);assertThat(a.principalBalanceCents()).isEqualTo(8000);assertThat(a.bonusBalanceCents()).isZero();assertThat(a.principalReservedCents()).isEqualTo(2000);
   assertThat(lots.lots(scope,account,false)).singleElement().satisfies(l->{assertThat(l.legacy()).isTrue();assertThat(l.initialP()).isEqualTo(8000);assertThat(l.reservedP()).isEqualTo(2000);});
   assertThat(jdbc.queryForObject("select count(*) from wallet_entry_allocations",Integer.class)).isZero();
   tx.execute(s->{funding.finish(scope,repo.account(scope,account,true),refund,topup,actor);assertThat(repo.account(scope,account,false).balanceCents()).isEqualTo(6000);assertThat(repo.account(scope,account,false).reservedCents()).isZero();assertThat(jdbc.queryForObject("select sum(principal_cents) from wallet_entry_allocations where action='topup_refund'",Long.class)).isEqualTo(2000);s.setRollbackOnly();return null;});
   tx.execute(s->{funding.release(scope,repo.account(scope,account,true),refund);return null;});
   UUID newBill=UUID.randomUUID();tx.execute(s->{funding.debit(scope,repo.account(scope,account,true),1000,newBill,actor);return null;});
   tx.execute(s->{funding.refundBill(scope,repo.account(scope,account,true),bill,UUID.randomUUID(),2000,actor);return null;});
   var restored=lots.lots(scope,account,false).getFirst();assertThat(restored.principal()).isEqualTo(9000);assertThat(restored.spentP()).isEqualTo(1000);
   tx.execute(s->{funding.refundBill(scope,repo.account(scope,account,true),newBill,UUID.randomUUID(),1000,actor);return null;});
   assertThat(repo.account(scope,account,false).balanceCents()).isEqualTo(10000);assertThat(lots.lots(scope,account,false).getFirst().spentP()).isZero();
  } finally {root.execute("drop schema "+schema+" cascade");}
 }
}
