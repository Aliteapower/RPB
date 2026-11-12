package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;import java.util.function.Predicate;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class WorkSummaryService {
 private final WorkforceAccess access;private final WorkforceDirectory directory;private final SchedulingRepository schedules;private final ApprovalRepository repository;private final ApprovalSources sources;private final com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService daily;private final com.rpb.reservation.workforce.attendance.daily.AttendanceDailyRepository days;private final com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository employees;
 public WorkSummaryService(WorkforceAccess access,WorkforceDirectory directory,SchedulingRepository schedules,ApprovalRepository repository,ApprovalSources sources){this(access,directory,schedules,repository,sources,null,null,null);}
 @org.springframework.beans.factory.annotation.Autowired public WorkSummaryService(WorkforceAccess access,WorkforceDirectory directory,SchedulingRepository schedules,ApprovalRepository repository,ApprovalSources sources,com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService daily,com.rpb.reservation.workforce.attendance.daily.AttendanceDailyRepository days,com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository employees){this.daily=daily;this.days=days;this.employees=employees;this.access=access;this.directory=directory;this.schedules=schedules;this.repository=repository;this.sources=sources;}
 public Summary summary(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID company){
  access.enter(actor,store);access.manage(actor,store);ScheduleService.range(from,to);if(company!=null)access.company(actor,company);
  if(daily==null)return scheduled(actor,store,from,to,company,s->true,false);
  if(company!=null)return effective(actor,store,from,to,company,Set.of());
  UUID tenant=actor.tenantId();
  var transferred=new HashSet<SchedulingRepository.TransferDay>();
  for(var day:schedules.transferDays(tenant,store,from,to)){
   if(daily.clockMode(tenant,day.company(),day.employee(),day.date()))transferred.add(day);
  }
  var regular=effective(actor,store,from,to,null,transferred);
  if(transferred.isEmpty())return regular;
  var segments=scheduled(actor,store,from,to,null,
      s->transferred.contains(new SchedulingRepository.TransferDay(s.employeeId(),s.companyId(),s.workDate())),true);
  var combined=new LinkedHashMap<List<UUID>,SummaryRow>();
  for(var part:List.of(regular,segments))for(var row:part.items())
   combined.merge(List.of(row.employeeId(),row.companyId(),row.storeId()),row,this::add);
  return new Summary(List.copyOf(combined.values()));
 }
 private SummaryRow add(SummaryRow a,SummaryRow b){
  return new SummaryRow(a.employeeId(),a.employeeName(),a.companyId(),a.storeId(),a.storeName(),
      a.schedules()+b.schedules(),a.approved()+b.approved(),a.pending()+b.pending(),
      a.workSeconds()+b.workSeconds(),a.paidLeaveSeconds()+b.paidLeaveSeconds(),a.unpaidLeaveSeconds()+b.unpaidLeaveSeconds());
 }
 private Summary scheduled(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID company,
      Predicate<SchedulingDtos.Schedule> included,boolean allowSystemValid){
  var ids=repository.allScheduleIds(actor.tenantId(),company==null?store:null,company,from,to);if(ids.size()>50000)throw WorkforceException.invalid();
  var scoped=new HashMap<UUID,SchedulingRepository.ScopedSchedule>();for(int i=0;i<ids.size();i+=500)for(var s:schedules.scoped(actor.tenantId(),ids.subList(i,Math.min(i+500,ids.size()))))scoped.put(s.schedule().id(),s);
  var required=new TreeSet<UUID>();if(company!=null)directory.stores(actor.tenantId()).stream().filter(s->company.equals(s.companyId())).map(s->s.id()).forEach(required::add);
  for(var id:ids)required.add(scoped.get(id).store());for(var shop:required){access.enter(actor,shop);access.manage(actor,shop);}
  var names=new HashMap<UUID,String>();directory.stores(actor.tenantId()).forEach(s->names.put(s.id(),s.name()));var result=new LinkedHashMap<String,SummaryRow>();
  var sourceMap=new HashMap<UUID,com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalSource>();var latest=new HashMap<UUID,com.rpb.reservation.workforce.approval.ApprovalDtos.Approval>();
  var byStore=new HashMap<UUID,List<com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule>>();scoped.values().forEach(s->byStore.computeIfAbsent(s.store(),k->new ArrayList<>()).add(s.schedule()));for(var part:byStore.entrySet())for(int i=0;i<part.getValue().size();i+=500){var chunk=part.getValue().subList(i,Math.min(i+500,part.getValue().size()));sourceMap.putAll(sources.readAll(actor.tenantId(),part.getKey(),chunk));latest.putAll(repository.latest(actor.tenantId(),chunk.stream().map(s->s.id()).toList()));}
  var systemValidated=new HashMap<SchedulingRepository.TransferDay,Boolean>();
  for(var id:ids){var s=scoped.get(id).schedule();if(!s.status().equals("scheduled")||!included.test(s))continue;UUID shop=scoped.get(id).store();var old=latest.get(id);var row=sources.row(shop,sourceMap.get(id),old);
   if(Boolean.TRUE.equals(row.exempt()))continue;
   boolean valid=row.issues().isEmpty()&&(old!=null&&!row.requiresReview()
       ||allowSystemValid&&old==null&&systemValidated.computeIfAbsent(
           new SchedulingRepository.TransferDay(s.employeeId(),s.companyId(),s.workDate()),day->
               daily.capture(actor.tenantId(),day.company(),day.employee(),day.date(),day.date()).getFirst().state().equals("system_valid")));
   String key=s.employeeId()+":"+s.companyId()+":"+shop;
   var before=result.getOrDefault(key,new SummaryRow(s.employeeId(),s.employeeName(),s.companyId(),shop,names.getOrDefault(shop,shop.toString()),0,0,0,0,0,0));
   result.put(key,new SummaryRow(before.employeeId(),before.employeeName(),before.companyId(),shop,before.storeName(),before.schedules()+1,before.approved()+(valid?1:0),before.pending()+(valid?0:1),before.workSeconds()+(valid?(old==null?row.workSeconds():old.workSeconds()):0),before.paidLeaveSeconds()+(valid?(old==null?row.paidLeaveSeconds():old.paidLeaveSeconds()):0),before.unpaidLeaveSeconds()+(valid?(old==null?row.unpaidLeaveSeconds():old.unpaidLeaveSeconds()):0)));
  }
  return new Summary(List.copyOf(result.values()));
 }
 private Summary effective(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID company,Set<SchedulingRepository.TransferDay> transferred){var shops=directory.stores(actor.tenantId()).stream().filter(s->company==null?s.id().equals(store):company.equals(s.companyId())).toList();var names=new HashMap<UUID,String>();directory.stores(actor.tenantId()).forEach(s->names.put(s.id(),s.name()));var result=new LinkedHashMap<String,SummaryRow>();var seen=new HashSet<String>();for(var shop:shops){access.enter(actor,shop.id());access.manage(actor,shop.id());var ids=company==null?days.employees(actor.tenantId(),shop.id(),from,to):days.companyEmployees(actor.tenantId(),company,from,to);if(ids.size()>500)throw WorkforceException.invalid();var facts=employees.findAll(actor.tenantId(),ids);for(var employee:ids){String identity=shop.companyId()+":"+employee;if(!seen.add(identity))continue;var emp=facts.get(employee);for(var day:daily.capture(actor.tenantId(),shop.companyId(),employee,from,to)){if(day.state().equals("outside_employment")||transferred.contains(new SchedulingRepository.TransferDay(employee,shop.companyId(),day.workDate())))continue;var sourceStores=new TreeSet<UUID>();if(day.storeId()!=null)sourceStores.add(day.storeId());if(day.snapshot().get("storeIds") instanceof List<?> stores)stores.forEach(x->sourceStores.add(UUID.fromString(x.toString())));if(company==null&&!sourceStores.isEmpty()&&!sourceStores.contains(store))continue;for(var sourceStore:sourceStores){access.enter(actor,sourceStore);access.manage(actor,sourceStore);}UUID owner=day.storeId()==null?shop.id():day.storeId();String key=identity+":"+owner;var old=result.getOrDefault(key,new SummaryRow(employee,emp.name(),shop.companyId(),owner,names.getOrDefault(owner,owner.toString()),0,0,0,0,0,0));boolean valid=com.rpb.reservation.workforce.attendance.daily.DailySourceState.payrollReady(day);result.put(key,new SummaryRow(employee,emp.name(),shop.companyId(),owner,old.storeName(),old.schedules()+1,old.approved()+(valid?1:0),old.pending()+(valid?0:1),old.workSeconds()+(valid&&day.workSeconds()!=null?day.workSeconds():0),old.paidLeaveSeconds()+(valid?day.paidLeaveSeconds():0),old.unpaidLeaveSeconds()+(valid?day.unpaidLeaveSeconds():0)));}}}return new Summary(List.copyOf(result.values()));}
}
