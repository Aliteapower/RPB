package com.rpb.reservation.workforce.workmode;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;
class WorkModePayrollTest {
 final LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,31);
 Assignment mode(LocalDate date,String mode){return new Assignment(UUID.randomUUID(),UUID.randomUUID(),date,mode,"personal",null,0,1);}
 @Test void freeRequiresFixedPayAndOrdinaryModeCannotSilentlyUseFreePay(){assertThat(WorkModePayroll.issues(List.of(mode(from,"free")),from,to,false)).contains("WORK_MODE_PAYROLL_MISMATCH");assertThat(WorkModePayroll.issues(List.of(mode(from,"onsite")),from,to,true)).contains("WORK_MODE_PAYROLL_MISMATCH");assertThat(WorkModePayroll.issues(List.of(mode(from,"free")),from,to,true)).isEmpty();assertThat(WorkModePayroll.issues(List.of(),from,to,true)).isEmpty();}
 @Test void monthlySwitchBetweenFreeAndAttendanceNeedsSeparateReview(){assertThat(WorkModePayroll.issues(List.of(mode(from,"field"),mode(from.plusDays(15),"free")),from,to,true)).contains("WORK_MODE_MONTH_SPLIT");assertThat(WorkModePayroll.issues(List.of(mode(from,"onsite"),mode(from.plusDays(15),"home")),from,to,false)).isEmpty();}
}
