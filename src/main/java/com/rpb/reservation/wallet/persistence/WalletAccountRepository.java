package com.rpb.reservation.wallet.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletAccountRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WalletAccountRepository(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public Optional<Program> program(StoreScope scope) {
        return jdbc.query("select p.* from wallet_programs p join wallet_program_stores s on s.program_id=p.id and s.tenant_id=p.tenant_id where s.tenant_id=? and s.store_id=?",
            (rs,n)->program(rs),scope.tenantId().value(),scope.storeId().value()).stream().findFirst();
    }
    public Program requireProgram(StoreScope scope,boolean active) {
        var p=program(scope).orElseThrow(WalletException::missing);
        if(active && (!"active".equals(p.status()) || !Boolean.TRUE.equals(jdbc.queryForObject("select enabled from wallet_program_stores where program_id=? and tenant_id=? and store_id=?",Boolean.class,p.id(),scope.tenantId().value(),scope.storeId().value()))))
            throw WalletException.conflict("门店钱包计划已停用");
        return p;
    }
    public Program configure(StoreScope scope,UUID issuer,List<String> channels,Integer version) {
        if(issuer==null || channels==null || channels.isEmpty() || channels.size()>30 || new HashSet<>(channels).size()!=channels.size()) throw WalletException.invalid();
        channels.forEach(WalletMoney::channel);
        try {
            boolean alreadyAttached=program(scope).isPresent();
            if(alreadyAttached && version==null) throw WalletException.conflict("请提供当前钱包设置版本");
            int created=jdbc.update("insert into wallet_programs(tenant_id,operating_entity_id,channels) values(?,?,?::jsonb) on conflict(tenant_id,operating_entity_id,currency) do nothing",
                scope.tenantId().value(),issuer,json.writeValueAsString(channels));
            var p=jdbc.query("select * from wallet_programs where tenant_id=? and operating_entity_id=? for update",(rs,n)->program(rs),scope.tenantId().value(),issuer).getFirst();
            if(version!=null && version!=p.version()) throw WalletException.conflict("钱包设置已更新，请刷新");
            if(created==1 || version!=null) jdbc.update("update wallet_programs set channels=?::jsonb,version=version+1,updated_at=now() where id=? and tenant_id=?",json.writeValueAsString(channels),p.id(),scope.tenantId().value());
            jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?) on conflict(program_id,store_id) do update set enabled=true,updated_at=now()",
                p.id(),scope.tenantId().value(),scope.storeId().value(),issuer);
            return requireProgram(scope,true);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
    }
    public Account open(StoreScope scope,Program program,UUID customer) {
        jdbc.update("insert into wallet_accounts(tenant_id,program_id,customer_id) values(?,?,?) on conflict(tenant_id,program_id,customer_id,currency) do nothing",scope.tenantId().value(),program.id(),customer);
        return jdbc.query("select * from wallet_accounts where tenant_id=? and program_id=? and customer_id=?",(rs,n)->account(rs),scope.tenantId().value(),program.id(),customer).getFirst();
    }
    public Account account(StoreScope scope,UUID id,boolean lock) {
        var result=queryAccount(scope,id,lock);
        if(lock)verifyIntegrity(result);
        return result;
    }
    /** External receipt/refund facts never mutate the wallet cache or ledger, even during reconciliation. */
    public Account lockForExternalRecovery(StoreScope scope,UUID id) {return queryAccount(scope,id,true);}
    private Account queryAccount(StoreScope scope,UUID id,boolean lock) {
        if(id==null) throw WalletException.invalid();
        var result=jdbc.query("select a.* from wallet_accounts a join wallet_program_stores s on s.program_id=a.program_id and s.tenant_id=a.tenant_id where a.tenant_id=? and s.store_id=? and a.id=?"+(lock?" for update of a":""),
            (rs,n)->account(rs),scope.tenantId().value(),scope.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);
        return result;
    }
    public void verifyIntegrity(Account result) {
            long reconstructed=jdbc.queryForObject("select coalesce(sum(case when kind in ('topup_credit','bill_refund') then amount_cents else -amount_cents end),0) from wallet_entries where tenant_id=? and account_id=?",Long.class,result.tenantId(),result.id());
            if(result.balanceCents()!=reconstructed) throw new WalletException("WALLET_LEDGER_MISMATCH",409,"钱包余额与资金流水不一致，资金操作已阻止，请管理员核查");
    }
    public List<Account> customerAccounts(StoreScope scope,UUID customer) {
        return jdbc.query("select a.* from wallet_accounts a join wallet_program_stores s on s.program_id=a.program_id and s.tenant_id=a.tenant_id where a.tenant_id=? and s.store_id=? and a.customer_id=? order by a.created_at",(rs,n)->account(rs),scope.tenantId().value(),scope.storeId().value(),customer);
    }
    public List<UUID> programStores(UUID tenant,UUID program) {return jdbc.query("select store_id from wallet_program_stores where tenant_id=? and program_id=? order by store_id",(rs,n)->rs.getObject("store_id",UUID.class),tenant,program);}
    public void update(Account account,long balance,long reserved) {
        updateComponents(account,balance,0,reserved,0);
    }
    public void updateComponents(Account account,long principal,long bonus,long reservedP,long reservedG) {
        long balance=Math.addExact(principal,bonus),reserved=Math.addExact(reservedP,reservedG);
        if(balance<0 || reserved<0 || reserved>balance || balance>WalletMoney.MAX_CENTS) throw WalletException.conflict("钱包余额不允许透支或超限");
        int n=jdbc.update("update wallet_accounts set balance_cents=?,reserved_cents=?,principal_balance_cents=?,bonus_balance_cents=?,principal_reserved_cents=?,bonus_reserved_cents=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",balance,reserved,principal,bonus,reservedP,reservedG,account.tenantId(),account.id(),account.version());
        if(n!=1) throw WalletException.conflict("钱包余额已更新，请刷新");
    }
    public void requireActive(Account account) {
        if(!"active".equals(account.status())) throw WalletException.conflict("钱包已冻结或关闭");
    }
    public boolean retainsCustomer(com.rpb.reservation.common.scope.TenantScope scope,UUID customer) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from wallet_accounts where tenant_id=? and customer_id=?)",Boolean.class,scope.tenantId().value(),customer));}
    private Program program(ResultSet rs) throws SQLException {
        try {return new Program(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),rs.getObject("operating_entity_id",UUID.class),json.readValue(rs.getString("channels"),new TypeReference<List<String>>(){}),rs.getString("status"),rs.getInt("version"),json.readValue(rs.getString("channel_labels"),new TypeReference<Map<String,String>>(){}));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private Account account(ResultSet rs) throws SQLException {
        return new Account(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),rs.getObject("program_id",UUID.class),rs.getObject("customer_id",UUID.class),rs.getLong("balance_cents"),rs.getLong("reserved_cents"),rs.getString("status"),rs.getInt("version"),rs.getLong("principal_balance_cents"),rs.getLong("bonus_balance_cents"),rs.getLong("principal_reserved_cents"),rs.getLong("bonus_reserved_cents"));
    }
}
