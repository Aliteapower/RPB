package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import com.rpb.reservation.workforce.payroll.PayrollAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.SchedulingRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ApprovalReadinessService {
 private final PayrollAccess access;private final ApprovalBoardRepository repository;private final SchedulingRepository schedules;private final ApprovalRepository approvals;private final ApprovalSources sources;private final com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService daily;private final com.rpb.reservation.workforce.payroll.PayrollRepository people;
 public ApprovalReadinessService(PayrollAccess access,ApprovalBoardRepository repository,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources){this(access,repository,schedules,approvals,sources,null,null);}
 @org.springframework.beans.factory.annotation.Autowired public ApprovalReadinessService(PayrollAccess access,ApprovalBoardRepository repository,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources,com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService daily,com.rpb.reservation.workforce.payroll.PayrollRepository people){this.daily=daily;this.people=people;this.access=access;this.repository=repository;this.schedules=schedules;this.approvals=approvals;this.sources=sources;}
 public Readiness check(CurrentActor actor,UUID store,UUID company,YearMonth month){
  access.enter(actor,store,company);if(month==null)throw WorkforceException.invalid();if(daily!=null)return effective(actor,store,company,month);var ids=repository.companySchedules(actor.tenantId(),company,month.atDay(1),month.atEndOfMonth());if(ids.size()>50000)throw WorkforceException.invalid();
  var byStore=new TreeMap<UUID,List<com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule>>();for(int i=0;i<ids.size();i+=500)for(var s:schedules.scoped(actor.tenantId(),ids.subList(i,Math.min(i+500,ids.size()))))byStore.computeIfAbsent(s.store(),k->new ArrayList<>()).add(s.schedule());access.enter(actor,store,company,byStore.keySet());
  var result=new ArrayList<StoreProgress>();int total=0,current=0;
  for(var entry:byStore.entrySet()){UUID shop=entry.getKey();var rows=entry.getValue();int valid=0;long work=0,paid=0,unpaid=0;
   for(int i=0;i<rows.size();i+=500){var chunk=rows.subList(i,Math.min(i+500,rows.size()));var data=sources.readAll(actor.tenantId(),shop,chunk);var latest=approvals.latest(actor.tenantId(),chunk.stream().map(s->s.id()).toList());for(var s:chunk){var old=latest.get(s.id());var row=sources.row(shop,data.get(s.id()),old);if(Boolean.TRUE.equals(row.exempt())){valid++;continue;}if(old!=null&&!row.requiresReview()&&row.issues().isEmpty()){valid++;work+=old.workSeconds();paid+=old.paidLeaveSeconds();unpaid+=old.unpaidLeaveSeconds();}}}
   total+=rows.size();current+=valid;result.add(new StoreProgress(shop,repository.storeName(actor.tenantId(),shop),rows.size(),valid,rows.size()-valid,work,paid,unpaid));
  }return new Readiness(company,month.toString(),total==current,total,current,total-current,result);
 }
 private Readiness effective(CurrentActor actor,UUID store,UUID company,YearMonth month){var ids=people.employeeIds(actor.tenantId(),company,month);if(ids.size()>500)throw WorkforceException.invalid();var grouped=new TreeMap<UUID,List<com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay>>();var required=new TreeSet<UUID>();for(var employee:ids)for(var day:daily.capture(actor.tenantId(),company,employee,month.atDay(1),month.atEndOfMonth())){if(day.state().equals("outside_employment"))continue;UUID shop=day.storeId()==null?store:day.storeId();required.add(shop);if(day.snapshot().get("storeIds") instanceof List<?> shops)shops.forEach(x->required.add(UUID.fromString(x.toString())));grouped.computeIfAbsent(shop,k->new ArrayList<>()).add(day);}access.enter(actor,store,company,required);var result=new ArrayList<StoreProgress>();int total=0,current=0;for(var entry:grouped.entrySet()){int valid=0;long work=0,paid=0,unpaid=0;for(var day:entry.getValue())if(com.rpb.reservation.workforce.attendance.daily.DailySourceState.payrollReady(day)){valid++;if(day.workSeconds()!=null)work+=day.workSeconds();paid+=day.paidLeaveSeconds();unpaid+=day.unpaidLeaveSeconds();}int count=entry.getValue().size();total+=count;current+=valid;result.add(new StoreProgress(entry.getKey(),repository.storeName(actor.tenantId(),entry.getKey()),count,valid,count-valid,work,paid,unpaid));}return new Readiness(company,month.toString(),total==current,total,current,total-current,result);}
}
