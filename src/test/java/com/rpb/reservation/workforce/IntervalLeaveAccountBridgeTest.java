package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.leave.account.*;
import com.rpb.reservation.workforce.leave.account.LeaveAccountDtos.*;
import com.rpb.reservation.workforce.leave.entitlement.LeaveEntitlementService;
import com.rpb.reservation.workforce.attendance.daily.*;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.IntervalException;
import com.rpb.reservation.workforce.compliance.*;
import com.rpb.reservation.workforce.compliance.ComplianceDtos.Contract;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.math.*;import java.util.*;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class IntervalLeaveAccountBridgeTest {
 UUID tenant=UUID.randomUUID(),employee=UUID.randomUUID(),company=UUID.randomUUID(),store=UUID.randomUUID(),type=UUID.randomUUID(),source=UUID.randomUUID(),contractId=UUID.randomUUID();
 CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store));
 LeaveAccountRepository accounts=mock(LeaveAccountRepository.class);ComplianceRepository contracts=mock(ComplianceRepository.class);LeaveBalanceEngine engine=mock(LeaveBalanceEngine.class);AttendanceDailyService daily=mock(AttendanceDailyService.class);CalendarRepository calendar=mock(CalendarRepository.class);LeaveEntitlementService manual=mock(LeaveEntitlementService.class);
 IntervalLeaveAccountBridge bridge=new IntervalLeaveAccountBridge(accounts,contracts,engine,daily,calendar,manual);
 LocalDate date=LocalDate.of(2026,11,2);Instant start=Instant.parse("2026-11-02T01:00:00Z"),end=start.plusSeconds(10800);
 IntervalException leave(){return new IntervalException(source,employee,company,store,date,"leave",type,"Annual",true,start,end,"reason","pending",actor.actorId(),null,null,null,1,start);}
 Account configured(){var contract=new Contract(contractId,employee,company,1,date.minusDays(30),date.minusYears(2),UUID.randomUUID(),1,new BigDecimal("6"),new BigDecimal("36"),new BigDecimal("6"),new BigDecimal("8"),new BigDecimal("44"),"non_workman","evidence",false);when(contracts.contract(tenant,employee,date)).thenReturn(Optional.of(contract));var account=new Account(UUID.randomUUID(),employee,company,2026,"annual","day",contractId,date.minusDays(1),BigDecimal.ZERO,1);when(accounts.accounts(tenant,employee,2026)).thenReturn(List.of(account));when(accounts.bindings(tenant,store)).thenReturn(List.of(new Binding(type,"annual",1)));when(daily.measurableLeaveSeconds(tenant,company,employee,date,start,end)).thenReturn(10800L);return account;}
 @Test void usesExplicitSixHourContractDayAndSharedEngine(){var account=configured();bridge.reserve(actor,leave());verify(engine).reserve(actor,account,source,"leave",date,new BigDecimal("0.500000"));verifyNoInteractions(manual);}
 @Test void missingArrangementCannotInventHoursOrConsumeBalance(){configured();when(daily.measurableLeaveSeconds(tenant,company,employee,date,start,end)).thenReturn(null);assertThrows(WorkforceException.class,()->bridge.reserve(actor,leave()));verifyNoInteractions(engine);}
 @Test void absentTypeMappingDoesNotGuessByAnnualName(){configured();when(accounts.bindings(tenant,store)).thenReturn(List.of());assertThrows(WorkforceException.class,()->bridge.reserve(actor,leave()));verifyNoInteractions(engine);}
 @Test void releaseOnlyOnFinalCancellationAndRetryUsesExistingHold(){var account=configured();when(accounts.holds(tenant,source)).thenReturn(List.of(new Hold(source,"leave",account.id(),UUID.randomUUID(),new BigDecimal("0.500000"),"reserved")));bridge.decision(actor,leave(),"cancel_requested");verifyNoInteractions(engine);bridge.decision(actor,leave(),"approved");verify(engine).transition(actor,source,"used","Interval leave approved");verify(engine,never()).reserve(any(),any(),any(),any(),any(),any());bridge.decision(actor,leave(),"cancelled");verify(engine).transition(actor,source,"released","Interval leave cancelled");}
 @Test void withoutContractChecksManualAllowanceInstead(){when(contracts.contract(tenant,employee,date)).thenReturn(Optional.empty());bridge.reserve(actor,leave());verify(manual).validateReservation(tenant,company,employee,type,date,source,start,end);verifyNoInteractions(engine);}

 @Test void crossYearRequestReservesSeparateYearsUsingOneSource(){
  date=LocalDate.of(2026,12,31);start=Instant.parse("2026-12-31T15:00:00Z");end=start.plusSeconds(7200);var first=configured();
  var nextDate=LocalDate.of(2027,1,1);var nextContract=contracts.contract(tenant,employee,date).orElseThrow();when(contracts.contract(tenant,employee,nextDate)).thenReturn(Optional.of(nextContract));
  var second=new Account(UUID.randomUUID(),employee,company,2027,"annual","day",contractId,nextDate,BigDecimal.ZERO,1);when(accounts.accounts(tenant,employee,2027)).thenReturn(List.of(second));
  var boundary=Instant.parse("2026-12-31T16:00:00Z");when(daily.measurableLeaveSeconds(tenant,company,employee,date,start,boundary)).thenReturn(3600L);when(daily.measurableLeaveSeconds(tenant,company,employee,date,boundary,end)).thenReturn(3600L);
  bridge.reserve(actor,leave());verify(engine).reserve(actor,first,source,"leave",date,new BigDecimal("0.166667"));verify(engine).reserve(actor,second,source,"leave",nextDate,new BigDecimal("0.166667"));
 }
 @Test void changedArrangementCannotConsumeStaleReservedUnits(){var account=configured();when(accounts.holds(tenant,source)).thenReturn(List.of(new Hold(source,"leave",account.id(),UUID.randomUUID(),BigDecimal.ONE,"reserved")));assertThrows(WorkforceException.class,()->bridge.decision(actor,leave(),"approved"));verifyNoInteractions(engine);}
}
