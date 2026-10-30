package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PayrollBulkServiceTest {
    PayrollBatchService batches=mock(PayrollBatchService.class);PayrollProgressRepository repository=mock(PayrollProgressRepository.class);
    WorkforceAccess gate=mock(WorkforceAccess.class);PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),command=UUID.randomUUID(),one=UUID.randomUUID(),two=UUID.randomUUID();
    CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store));PayrollBulkService bulk;
    @BeforeEach void setup(){when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());bulk=new PayrollBulkService(batches,repository,gate,manager);when(repository.complete(any(),any(),any(),any(),any(),any())).thenAnswer(i->i.getArgument(5));when(repository.withOperationLock(any(),any(),any())).thenAnswer(i->((java.util.function.Supplier<?>)i.getArgument(2)).get());}
    BulkWrite write(){return new BulkWrite("calculate","Checked",List.of(new BulkEntry(one,1L),new BulkEntry(two,1L)));}
    BatchDetail detail(){return new BatchDetail(null,List.of(),List.of(),null,false,List.of());}
    @Test void partialBusinessFailuresHaveNoSalaryAndDoNotAbortOtherEntries(){
        when(batches.action(eq(actor),eq(store),eq(one),any(),any())).thenReturn(detail());
        when(batches.action(eq(actor),eq(store),eq(two),any(),any())).thenThrow(WorkforceException.forbidden());
        var result=bulk.execute(actor,store,command,write());
        assertThat(result.results()).extracting(BulkItem::status).containsExactly("success","failed");
        assertThat(result.results().get(1).batch()).isNull();assertThat(result.results().get(1).code()).isEqualTo("WORKFORCE_FORBIDDEN");
        verify(batches,times(2)).action(actor,store,one,PayrollBulkService.childCommand(tenant,command,one),new ActionWrite(1L,"calculate","Checked"));
    }
    @Test void unknownFailureLeavesParentUncompletedAndRetryUsesSameChildCommands(){
        when(batches.action(eq(actor),eq(store),eq(one),any(),any())).thenReturn(detail());
        when(batches.action(eq(actor),eq(store),eq(two),any(),any())).thenThrow(new IllegalStateException("database"));
        assertThatThrownBy(()->bulk.execute(actor,store,command,write())).isInstanceOf(IllegalStateException.class);
        verify(repository,never()).complete(any(),any(),any(),any(),any(),any());
        when(batches.action(eq(actor),eq(store),eq(two),any(),any())).thenReturn(detail());
        bulk.execute(actor,store,command,write());
        verify(batches,times(3)).action(actor,store,one,PayrollBulkService.childCommand(tenant,command,one),new ActionWrite(1L,"calculate","Checked"));
    }
    @Test void completedReplayRechecksEverySuccessfulBatchBeforeReturning(){
        when(repository.prepare(any(),any(),any(),any(),any())).thenReturn(new BulkResult(command,"calculate",List.of(new BulkItem(one,"success",null,null))));
        when(batches.action(eq(actor),eq(store),eq(one),any(),any())).thenThrow(WorkforceException.forbidden());
        assertThatThrownBy(()->bulk.execute(actor,store,command,write())).isInstanceOf(WorkforceException.class);
        verify(batches).action(actor,store,one,PayrollBulkService.childCommand(tenant,command,one),new ActionWrite(1L,"calculate","Checked"));
    }
    @Test void boundedValidationAndDeterministicChildrenAreTenantScoped(){
        assertThatThrownBy(()->PayrollBulkService.validate(new BulkWrite("publish","x",write().entries()))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->PayrollBulkService.validate(new BulkWrite("calculate","x",List.of(new BulkEntry(one,1L),new BulkEntry(one,1L))))).isInstanceOf(WorkforceException.class);
        var entries=new ArrayList<BulkEntry>();for(int i=0;i<21;i++)entries.add(new BulkEntry(UUID.randomUUID(),1L));
        assertThatThrownBy(()->PayrollBulkService.validate(new BulkWrite("calculate","x",entries))).isInstanceOf(WorkforceException.class);
        assertThat(PayrollBulkService.childCommand(tenant,command,one)).isNotEqualTo(PayrollBulkService.childCommand(UUID.randomUUID(),command,one));
    }
}
