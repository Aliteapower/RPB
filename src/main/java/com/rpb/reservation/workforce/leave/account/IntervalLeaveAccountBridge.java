package com.rpb.reservation.workforce.leave.account;

import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.IntervalException;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService;
import com.rpb.reservation.workforce.compliance.ComplianceRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.leave.entitlement.LeaveEntitlementService;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Called with source-write and employee locks, in the exception transaction. */
@Component
public class IntervalLeaveAccountBridge {
 private final LeaveAccountRepository accounts;
 private final ComplianceRepository contracts;
 private final LeaveBalanceEngine engine;
 private final AttendanceDailyService daily;
 private final CalendarRepository calendar;
 private final LeaveEntitlementService manual;
 private final com.rpb.reservation.workforce.scheduling.SchedulingRepository schedules;
 public IntervalLeaveAccountBridge(LeaveAccountRepository accounts,ComplianceRepository contracts,LeaveBalanceEngine engine,AttendanceDailyService daily,CalendarRepository calendar,LeaveEntitlementService manual){this(accounts,contracts,engine,daily,calendar,manual,null);}
 @org.springframework.beans.factory.annotation.Autowired public IntervalLeaveAccountBridge(LeaveAccountRepository accounts,ComplianceRepository contracts,LeaveBalanceEngine engine,AttendanceDailyService daily,CalendarRepository calendar,LeaveEntitlementService manual,com.rpb.reservation.workforce.scheduling.SchedulingRepository schedules){this.accounts=accounts;this.contracts=contracts;this.engine=engine;this.daily=daily;this.calendar=calendar;this.manual=manual;this.schedules=schedules;}
 public void reserve(CurrentActor actor,IntervalException leave){
  if(!leave.kind().equals("leave")||!leave.paid())return;
  ZoneId zone=schedules==null?ZoneId.of("Asia/Singapore"):ZoneId.of(schedules.timezone(actor.tenantId(),leave.storeId()));
  boolean fresh=accounts.holds(actor.tenantId(),leave.id()).isEmpty();
  var from=leave.startAt();
  while(from.isBefore(leave.endAt())){
   var date=from.atZone(zone).toLocalDate();
   var yearEnd=LocalDate.of(date.getYear()+1,1,1).atStartOfDay(zone).toInstant();
   var to=leave.endAt().isBefore(yearEnd)?leave.endAt():yearEnd;
   reservePart(actor,leave,date,from,to,fresh);from=to;
  }
 }
 private void reservePart(CurrentActor actor,IntervalException leave,LocalDate accountDate,Instant from,Instant to,boolean fresh){
  var contract=contracts.contract(actor.tenantId(),leave.employeeId(),accountDate).orElse(null);
  if(contract==null){if(accountDate.getYear()!=leave.workDate().getYear()||!from.equals(leave.startAt())||!to.equals(leave.endAt()))throw new WorkforceException("LEAVE_CROSS_YEAR_REVIEW_REQUIRED",409);manual.validateReservation(actor.tenantId(),leave.companyId(),leave.employeeId(),leave.typeId(),leave.workDate(),leave.id(),leave.startAt(),leave.endAt());return;}
  if(!contract.companyId().equals(leave.companyId()))throw new WorkforceException("LEAVE_CONTRACT_REVIEW_REQUIRED",409);
  var binding=accounts.bindings(actor.tenantId(),leave.storeId()).stream().filter(b->b.typeId().equals(leave.typeId())).findFirst().orElseThrow(()->new WorkforceException("LEAVE_TYPE_POLICY_REQUIRED",409));
  if(binding.kind().equals("unpaid"))throw WorkforceException.invalid();
  var eligible=accounts.accounts(actor.tenantId(),leave.employeeId(),accountDate.getYear()).stream().filter(a->a.companyId().equals(leave.companyId())).toList();
  var account=eligible.stream().filter(a->a.kind().equals(binding.kind())).findFirst().orElseThrow(()->new WorkforceException("LEAVE_ACCOUNT_REQUIRED",409));
  if(!account.contractId().equals(contract.id()))throw new WorkforceException("LEAVE_CONTRACT_REVIEW_REQUIRED",409);
  if(accounts.reconciledOpeningDay(actor.tenantId(),account,accountDate))throw new WorkforceException("LEAVE_OPENING_PERIOD_REVIEW_REQUIRED",409);
  if(calendar.list(actor.tenantId(),leave.storeId(),leave.workDate(),leave.workDate()).stream().anyMatch(e->e.enabled()&&Set.of("public_holiday","store_rest").contains(e.kind())))throw new WorkforceException("LEAVE_NON_WORKING_DAY_REVIEW",409);
  Long net=daily.measurableLeaveSeconds(actor.tenantId(),leave.companyId(),leave.employeeId(),leave.workDate(),from,to);
  if(net==null)throw new WorkforceException("LEAVE_UNIT_POLICY_REQUIRED",409);
  if(net<=0)throw WorkforceException.invalid();
  // The contract supplies the daily conversion; no universal eight-hour day.
  BigDecimal divisor=account.unit().equals("hour")?BigDecimal.valueOf(3600):contract.dailyHours().multiply(BigDecimal.valueOf(3600));
  BigDecimal units=BigDecimal.valueOf(net).divide(divisor,6,RoundingMode.HALF_UP);
  reserveAccount(actor,account,leave.id(),accountDate,units,fresh);
  if(binding.kind().equals("sick")){var shared=eligible.stream().filter(a->a.kind().equals("hospital")&&a.unit().equals(account.unit())&&a.contractId().equals(contract.id())).findFirst().orElseThrow(()->new WorkforceException("LEAVE_ACCOUNT_REQUIRED",409));reserveAccount(actor,shared,leave.id(),accountDate,units,fresh);}
 }
 private void reserveAccount(CurrentActor actor,LeaveAccountDtos.Account account,UUID source,LocalDate date,BigDecimal units,boolean fresh){
  var holds=accounts.holds(actor.tenantId(),source);
  if(!fresh){
   var own=holds.stream().filter(h->h.accountId().equals(account.id())).toList();
   var held=own.stream().map(LeaveAccountDtos.Hold::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
   if(own.isEmpty()||own.stream().anyMatch(h->!h.status().equals("reserved"))||held.compareTo(units)!=0)throw new WorkforceException("LEAVE_SOURCE_REVIEW_REQUIRED",409);
   return;
  }
  engine.reserve(actor,account,source,"leave",date,units);
 }
 public void decision(CurrentActor actor,IntervalException leave,String action){
  if(!leave.kind().equals("leave")||!leave.paid())return;
  if(action.equals("approved")){
   var binding=accounts.bindings(actor.tenantId(),leave.storeId()).stream().filter(b->b.typeId().equals(leave.typeId())).findFirst().orElse(null);
   if(binding!=null&&Set.of("sick","hospital").contains(binding.kind())&&contracts.contract(actor.tenantId(),leave.employeeId(),leave.workDate()).isPresent()&&(leave.medicalReference()==null||leave.medicalReference().isBlank()))throw new WorkforceException("LEAVE_MEDICAL_EVIDENCE_REQUIRED",409);
   reserve(actor,leave);engine.transition(actor,leave.id(),"used","Interval leave approved");
  }else if(Set.of("rejected","withdrawn","cancelled").contains(action))engine.transition(actor,leave.id(),"released","Interval leave "+action);
 }
}
