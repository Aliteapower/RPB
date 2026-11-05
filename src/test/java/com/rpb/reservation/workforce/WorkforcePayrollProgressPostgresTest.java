package com.rpb.reservation.workforce;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkforcePayrollProgressPostgresTest extends WorkforcePayrollPostgresTest {
    PayrollProgressService progress(){return proxy(new PayrollProgressService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time)));}
    PayrollBulkService bulk(PayrollBatchService service){return proxy(new PayrollBulkService(service,new PayrollProgressRepository(jdbc,new WorkforceJson(new ObjectMapper().findAndRegisterModules())),access,tx));}
    @Test void dedicatedOperationConnectionDoesNotExhaustSingleConnectionBusinessPool(){
        completed();var b=create();var fixture=(DriverManagerDataSource)jdbc.getDataSource();
        var properties=new DataSourceProperties();properties.setUrl(fixture.getUrl());properties.setUsername(fixture.getUsername());properties.setPassword(fixture.getPassword());
        var config=new HikariConfig();config.setJdbcUrl(properties.determineUrl());config.setUsername(properties.determineUsername());config.setPassword(properties.determinePassword());
        config.setMaximumPoolSize(1);config.setMinimumIdle(1);config.setConnectionTimeout(1000);
        try(var pool=new HikariDataSource(config)){
            var pooled=new JdbcTemplate(pool);var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
            var commands=new PayrollProgressRepository(pooled,json,properties);
            commands.withOperationLock(tenant,UUID.randomUUID(),()->{
                // The only business connection remains available while the dedicated lock session is open.
                assertThat(pooled.queryForObject("select current_schema()",String.class)).isEqualTo(SCHEMA);
                return true;
            });
            var service=new PayrollBulkService(batches,commands,access,new DataSourceTransactionManager(pool));UUID command=UUID.randomUUID();
            var payload=new BulkWrite("calculate","Single business connection",List.of(new BulkEntry(b.batch().id(),b.batch().version())));
            var result=service.execute(admin,store,command,payload);
            assertThat(result.results()).extracting(BulkItem::status).containsExactly("success");
            assertThat(service.execute(admin,store,command,payload)).isEqualTo(result);
            assertThat(pooled.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and batch_id=?",Integer.class,tenant,b.batch().id())).isEqualTo(1);
        }
    }
    @Test void instanceOperationLimitRejectsThirdCommandAndReleasesSlotsAfterFailure()throws Exception{
        var commands=new PayrollProgressRepository(jdbc,new WorkforceJson(new ObjectMapper().findAndRegisterModules()));
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->commands.withOperationLock(tenant,UUID.randomUUID(),()->{
                entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                throw new IllegalStateException("Synthetic failure");
            })));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->commands.withOperationLock(tenant,UUID.randomUUID(),()->true)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).code()).isEqualTo("WORKFORCE_PAYROLL_BUSY");
            release.countDown();for(var future:futures)assertThatThrownBy(()->future.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(commands.withOperationLock(tenant,UUID.randomUUID(),()->true)).isTrue();
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void projectionRetainsLastCalculationAcrossInputResetAndDoesNotWriteFrozenFacts(){
        completed();var b=action(create(),"calculate");
        var first=progress().changes(admin,store,b.batch().id());assertThat(first.pending()).isEmpty();assertThat(first.previousMonth().available()).isFalse();
        b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(0L,"","",true,true,List.of(new Item("Additional","ow_additional","20")),"Checked"));
        var pending=progress().changes(admin,store,b.batch().id());assertThat(pending.pending()).extracting(PendingChange::employeeId).contains(employee);
        b=action(b,"calculate");assertThat(progress().changes(admin,store,b.batch().id()).lastCalculation().available()).isTrue();
        time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");UUID id=b.batch().id();
        String before=jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,id);
        progress().changes(admin,store,id);assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,id)).isEqualTo(before);
        assertThatThrownBy(()->progress().changes(worker,store,id)).isInstanceOf(WorkforceException.class);
    }
    @Test void completedBulkHasStablePartialReceiptAndRejectsActorStoreAndPayloadChanges(){
        completed();var b=create();var service=bulk(batches);UUID id=UUID.randomUUID();
        var payload=new BulkWrite("calculate","Checked",List.of(new BulkEntry(b.batch().id(),b.batch().version()),new BulkEntry(UUID.randomUUID(),1L)));
        var first=service.execute(admin,store,id,payload);assertThat(first.results()).extracting(BulkItem::status).containsExactly("success","failed");
        assertThat(service.execute(admin,store,id,payload)).isEqualTo(first);
        assertThatThrownBy(()->service.execute(admin,sibling,id,payload)).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->service.execute(worker,store,id,payload)).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->service.execute(admin,store,id,new BulkWrite("calculate_full","Checked",payload.entries()))).isInstanceOf(WorkforceException.class);
        assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and batch_id=?",Integer.class,tenant,b.batch().id())).isEqualTo(1);
    }
    @Test void interruptedBulkResumesCommittedChildWithoutDoubleCalculation(){
        completed();var b=create();UUID command=UUID.randomUUID(),unknown=UUID.randomUUID();var interrupted=mock(PayrollBatchService.class,org.mockito.AdditionalAnswers.delegatesTo(batches));
        doThrow(new IllegalStateException("Synthetic interruption")).when(interrupted).action(eq(admin),eq(store),eq(unknown),any(),any());
        var payload=new BulkWrite("calculate","Checked",List.of(new BulkEntry(b.batch().id(),b.batch().version()),new BulkEntry(unknown,1L)));
        assertThatThrownBy(()->bulk(interrupted).execute(admin,store,command,payload)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select response is null from workforce_pay_bulk_operations where tenant_id=? and id=?",Boolean.class,tenant,command)).isTrue();
        var result=bulk(batches).execute(admin,store,command,payload);assertThat(result.results()).extracting(BulkItem::status).containsExactly("success","failed");
        assertThat(payRepository.find(tenant,b.batch().id()).orElseThrow().version()).isEqualTo(b.batch().version()+1);
        assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and batch_id=?",Integer.class,tenant,b.batch().id())).isEqualTo(1);
    }
    @Test void independentInstancesSerializeSameOperationWithoutHoldingPayrollTransactions()throws Exception{
        completed();var b=create();UUID command=UUID.randomUUID();
        var payload=new BulkWrite("calculate","Checked",List.of(new BulkEntry(b.batch().id(),b.batch().version())));
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        var firstRepository=new PayrollProgressRepository(jdbc,json);var firstService=bulk(batches);var secondService=bulk(batches);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try{
            var first=pool.submit(()->firstRepository.withOperationLock(tenant,command,()->{
                entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return true;
            }));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->secondService.execute(admin,store,command,payload)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).code()).isEqualTo("WORKFORCE_PAYROLL_BUSY");
            assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and batch_id=?",Integer.class,tenant,b.batch().id())).isZero();
            release.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();
            var response=firstService.execute(admin,store,command,payload);assertThat(secondService.execute(admin,store,command,payload)).isEqualTo(response);
            assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and batch_id=?",Integer.class,tenant,b.batch().id())).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();}
    }
}
