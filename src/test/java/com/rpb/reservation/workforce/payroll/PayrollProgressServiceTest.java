package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayrollProgressServiceTest {
    WorkforceAccess gate=mock(WorkforceAccess.class);PayrollAccess access=mock(PayrollAccess.class);
    PayrollRepository repository=mock(PayrollRepository.class);PayrollSources sources=mock(PayrollSources.class);
    PayrollProgressService progress=new PayrollProgressService(gate,access,repository,sources,mock(PayrollCalculationService.class));
    UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),id=UUID.randomUUID(),company=UUID.randomUUID();
    CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store));
    PayrollRepository.State batch=new PayrollRepository.State(id,tenant,company,"Company",YearMonth.of(2026,10),"draft",2,null,actor.actorId(),null,Instant.EPOCH);
    Comparison last=new Comparison(true,"2026-10",1L,"0","0","0",List.of());
    @Test void comparisonRetainedHistoricalStoresAreAuthorizedBeforeAnySalaryProjection(){
        UUID historic=UUID.randomUUID();when(repository.find(tenant,id)).thenReturn(Optional.of(batch));
        when(repository.computation(tenant,id)).thenReturn(Optional.of(new PayrollComputation.State(null,last,0,0,2,Set.of(historic))));
        doThrow(WorkforceException.forbidden()).when(access).enter(actor,store,company,Set.of(historic));
        assertThatThrownBy(()->progress.changes(actor,store,id)).isInstanceOf(WorkforceException.class);
        verifyNoInteractions(sources);verify(repository,never()).computation(org.mockito.ArgumentMatchers.any(PayrollRepository.State.class),org.mockito.ArgumentMatchers.any(PayrollComputation.State.class));
    }
    @Test void missingHistoricalPermissionEvidenceFailsClosed(){
        when(repository.find(tenant,id)).thenReturn(Optional.of(batch));
        when(repository.computation(tenant,id)).thenReturn(Optional.of(new PayrollComputation.State(null,last,0,0,2,null)));
        assertThatThrownBy(()->progress.changes(actor,store,id)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).status()).isEqualTo(403);
        verifyNoInteractions(sources);
    }
    @Test void missingAndCrossTenantBatchCannotCaptureOrExposeSources(){
        when(repository.find(tenant,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->progress.changes(actor,store,id)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).status()).isEqualTo(404);
        verifyNoInteractions(sources,access);
    }
}
