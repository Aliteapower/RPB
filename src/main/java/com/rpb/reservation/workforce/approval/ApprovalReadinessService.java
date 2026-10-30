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
 private final PayrollAccess access;private final ApprovalBoardRepository repository;private final SchedulingRepository schedules;private final ApprovalRepository approvals;private final ApprovalSources sources;
 public ApprovalReadinessService(PayrollAccess access,ApprovalBoardRepository repository,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources){this.access=access;this.repository=repository;this.schedules=schedules;this.approvals=approvals;this.sources=sources;}
 public Readiness check(CurrentActor actor,UUID store,UUID company,YearMonth month){
  access.enter(actor,store,company);if(month==null)throw WorkforceException.invalid();var ids=repository.companySchedules(actor.tenantId(),company,month.atDay(1),month.atEndOfMonth());if(ids.size()>50000)throw WorkforceException.invalid();
  var byStore=new TreeMap<UUID,List<com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule>>();for(int i=0;i<ids.size();i+=500)for(var s:schedules.scoped(actor.tenantId(),ids.subList(i,Math.min(i+500,ids.size()))))byStore.computeIfAbsent(s.store(),k->new ArrayList<>()).add(s.schedule());access.enter(actor,store,company,byStore.keySet());
  var result=new ArrayList<StoreProgress>();int total=0,current=0;
  for(var entry:byStore.entrySet()){UUID shop=entry.getKey();var rows=entry.getValue();int valid=0;long work=0,paid=0,unpaid=0;
   for(int i=0;i<rows.size();i+=500){var chunk=rows.subList(i,Math.min(i+500,rows.size()));var data=sources.readAll(actor.tenantId(),shop,chunk);var latest=approvals.latest(actor.tenantId(),chunk.stream().map(s->s.id()).toList());for(var s:chunk){var old=latest.get(s.id());var row=sources.row(shop,data.get(s.id()),old);if(Boolean.TRUE.equals(row.exempt())){valid++;continue;}if(old!=null&&!row.requiresReview()&&row.issues().isEmpty()){valid++;work+=old.workSeconds();paid+=old.paidLeaveSeconds();unpaid+=old.unpaidLeaveSeconds();}}}
   total+=rows.size();current+=valid;result.add(new StoreProgress(shop,repository.storeName(actor.tenantId(),shop),rows.size(),valid,rows.size()-valid,work,paid,unpaid));
  }return new Readiness(company,month.toString(),total==current,total,current,total-current,result);
 }
}
