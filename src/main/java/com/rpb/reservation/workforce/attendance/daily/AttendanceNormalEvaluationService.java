package com.rpb.reservation.workforce.attendance.daily;

import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.approval.ApprovalRepository;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceNormalEvaluationRepository.*;

@Service
public class AttendanceNormalEvaluationService {
 private final AttendanceNormalEvaluationRepository repository;private final AttendanceDailyRepository days;private final AttendanceDailyService daily;private final ApprovalRepository approvals;private final WorkforceEmployeeRepository employees;private final AppGateService gate;private final AuditLogRepositoryPort audit;private final WorkforceJson json;private final Clock clock;
 public AttendanceNormalEvaluationService(AttendanceNormalEvaluationRepository repository,AttendanceDailyRepository days,AttendanceDailyService daily,ApprovalRepository approvals,WorkforceEmployeeRepository employees,AppGateService gate,AuditLogRepositoryPort audit,WorkforceJson json,Clock clock){this.repository=repository;this.days=days;this.daily=daily;this.approvals=approvals;this.employees=employees;this.gate=gate;this.audit=audit;this.json=json;this.clock=clock;}
 @Transactional public int evaluate(Scope scope){
  if(!gate.evaluateCommercial("workforce",scope.tenant(),scope.store()).allowed())return 0;
  repository.lockSources(scope.tenant());approvals.calendarLock(scope.tenant(),scope.store());employees.lock(scope.employee());
  LocalDate today=clock.instant().atZone(ZoneId.of(scope.timezone())).toLocalDate();int count=0;
  for(LocalDate date=today.minusDays(7);!date.isAfter(today);date=date.plusDays(1)){
   if(!repository.adopted(scope,date))continue;
   var next=daily.evaluateForSystem(scope.tenant(),scope.company(),scope.employee(),date);
   if(next.storeId()!=null&&!scope.store().equals(next.storeId())||!finalized(next,today))continue;
   var old=days.latest(scope.tenant(),scope.company(),scope.employee(),date).orElse(null);
   if(!changed(old,next))continue;
   repository.save(scope,next);
   var metadata=new LinkedHashMap<String,Object>();metadata.put("initiator","scheduler");metadata.put("employeeId",scope.employee());metadata.put("workDate",date);metadata.put("state",next.state());metadata.put("sourceFingerprint",next.sourceFingerprint());metadata.put("version",next.version());
   audit.append(new StoreScope(new TenantId(scope.tenant()),scope.store()),new AuditLog(UUID.randomUUID(),"workforce.attendance.evaluate","workforce",next.sourceId(),"system","system",null,json.write(metadata)));count++;
  }return count;
 }
 public static boolean changed(EffectiveDay old,EffectiveDay next){return old==null||!old.sourceFingerprint().equals(next.sourceFingerprint())||(!old.state().equals("human_valid")&&!old.state().equals(next.state()));}
 public static boolean finalized(EffectiveDay next,LocalDate today){
  if(next.workDate().isAfter(today)||Set.of("future","outside_employment","unarranged").contains(next.state()))return false;
  Object phase=next.snapshot().get("phase");if(phase!=null)return phase.equals("completed");
  return next.workDate().isBefore(today)&&Set.of("rest","not_applicable","pending").contains(next.state());
 }
}
