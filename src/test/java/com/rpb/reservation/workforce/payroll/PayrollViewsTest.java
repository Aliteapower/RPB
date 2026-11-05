package com.rpb.reservation.workforce.payroll;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayrollViewsTest {
    @Test
    void futureDaysWithoutAStoreDoNotBreakTheStoreScopeOfACrossStorePayrollSnapshot() {
        UUID originalStore=UUID.randomUUID(), targetStore=UUID.randomUUID();
        var date=LocalDate.of(2026,11,5);
        var sources=List.of(
            new SourceRef(UUID.randomUUID(),originalStore,date,1,"original",79,0,0,true),
            new SourceRef(UUID.randomUUID(),targetStore,date,1,"target",8,0,0,true),
            new SourceRef(UUID.randomUUID(),null,date.plusDays(1),0,"future",0,0,0,true)
        );
        var line=mock(PayrollLine.class);
        when(line.sources()).thenReturn(sources);
        var snapshot=mock(PayrollSnapshot.class);
        when(snapshot.lines()).thenReturn(List.of(line));

        assertThat(PayrollViews.stores(snapshot)).containsExactlyInAnyOrder(originalStore,targetStore);
        assertThat(line.sources()).containsExactlyElementsOf(sources);
    }
}
