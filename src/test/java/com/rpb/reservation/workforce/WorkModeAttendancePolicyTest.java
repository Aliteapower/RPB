package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.workmode.*;
import com.rpb.reservation.workforce.attendancepolicy.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkModeAttendancePolicyTest {
    @Test void independentNoPunchModesDisableActionsWithoutChangingLocationHistory() {
        var tenant=UUID.randomUUID(); var employee=UUID.randomUUID(); var store=UUID.randomUUID();
        var date=LocalDate.of(2026,11,2);
        var location=mock(WorkModeResolver.class); var policies=mock(AttendancePolicyResolver.class);
        when(location.resolve(tenant,employee,store,date)).thenReturn(new WorkModeDtos.ClockPolicy("onsite",null,1,null,null,0));
        var service=new WorkModeClock(mock(WorkModeRepository.class),location,Clock.systemUTC(),policies);
        for(var mode:new String[]{"default_normal","contract","free"}){
            when(policies.resolveForEmployee(tenant,employee,date)).thenReturn(new AttendancePolicyDtos.EffectiveAttendancePolicy(mode,UUID.randomUUID(),1,store,"personal",null,"Asia/Singapore","explicit_assignment"));
            assertThat(service.exempt(tenant,employee,store,date,null)).isTrue();
            assertThat(service.current(tenant,employee,store,date).mode()).isEqualTo("free");
        }
        verify(location,never()).resolve(tenant,employee,store,date);
    }
}
