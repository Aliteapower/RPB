package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class WorkSummaryService {
 private final WorkforceAccess access;private final WorkforceDirectory directory;private final SchedulingRepository schedules;private final ApprovalRepository repository;private final ApprovalSources sources;
 public WorkSummaryService(WorkforceAccess access,WorkforceDirectory directory,SchedulingRepository schedules,ApprovalRepository repository,ApprovalSources sources){this.access=access;this.directory=directory;this.schedules=schedules;this.repository=repository;this.sources=sources;}
 public Summary summary(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID company){
  access.enter(actor,store);access.manage(actor,store);ScheduleService.range(from,to);if(company!=null)access.company(actor,company);
  var ids=repository.allScheduleIds(actor.tenantId(),company==null?store:null,company,from,to);if(ids.size()>50000)throw WorkforceException.invalid();
  var scoped=new HashMap<UUID,SchedulingRepository.ScopedSchedule>();for(int i=0;i<ids.size();i+=500)for(var s:schedules.scoped(actor.tenantId(),ids.subList(i,Math.min(i+500,ids.size()))))scoped.put(s.schedule().id(),s);
  var required=new TreeSet<UUID>();if(company!=null)directory.stores(actor.tenantId()).stream().filter(s->company.equals(s.companyId())).map(s->s.id()).forEach(required::add);
  for(var id:ids)required.add(scoped.get(id).store());for(var shop:required){access.enter(actor,shop);access.manage(actor,shop);}
  var names=new HashMap<UUID,String>();directory.stores(actor.tenantId()).forEach(s->names.put(s.id(),s.name()));var result=new LinkedHashMap<String,SummaryRow>();
  var sourceMap=new HashMap<UUID,com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalSource>();var latest=new HashMap<UUID,com.rpb.reservation.workforce.approval.ApprovalDtos.Approval>();
  var byStore=new HashMap<UUID,List<com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule>>();scoped.values().forEach(s->byStore.computeIfAbsent(s.store(),k->new ArrayList<>()).add(s.schedule()));for(var part:byStore.entrySet())for(int i=0;i<part.getValue().size();i+=500){var chunk=part.getValue().subList(i,Math.min(i+500,part.getValue().size()));sourceMap.putAll(sources.readAll(actor.tenantId(),part.getKey(),chunk));latest.putAll(repository.latest(actor.tenantId(),chunk.stream().map(s->s.id()).toList()));}
  for(var id:ids){var s=scoped.get(id).schedule();if(!s.status().equals("scheduled"))continue;UUID shop=scoped.get(id).store();var old=latest.get(id);var row=sources.row(shop,sourceMap.get(id),old);
   if(Boolean.TRUE.equals(row.exempt()))continue;
   boolean valid=old!=null&&!row.requiresReview()&&row.issues().isEmpty();String key=s.employeeId()+":"+s.companyId()+":"+shop;
   var before=result.getOrDefault(key,new SummaryRow(s.employeeId(),s.employeeName(),s.companyId(),shop,names.getOrDefault(shop,shop.toString()),0,0,0,0,0,0));
   result.put(key,new SummaryRow(before.employeeId(),before.employeeName(),before.companyId(),shop,before.storeName(),before.schedules()+1,before.approved()+(valid?1:0),before.pending()+(valid?0:1),before.workSeconds()+(valid?old.workSeconds():0),before.paidLeaveSeconds()+(valid?old.paidLeaveSeconds():0),before.unpaidLeaveSeconds()+(valid?old.unpaidLeaveSeconds():0)));
  }
  return new Summary(List.copyOf(result.values()));
 }
}
