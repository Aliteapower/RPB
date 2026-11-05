package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.rpb.reservation.winestorage.application.StorageReportDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StorageReportQuery {
 private final StorageAccess access;private final StorageReportRepository reports;private final StorageRepository records;private final Clock clock;
 public StorageReportQuery(StorageAccess access,StorageReportRepository reports,StorageRepository records,Clock clock){this.access=access;this.reports=reports;this.records=records;this.clock=clock;}
 private void authorize(StoreScope scope,CurrentActor actor){access.staff(scope,actor,"wine_storage.view",false);access.staffEntry(scope,actor);}
 static void kind(String value){if(value==null||!Set.of("deposit","pickup").contains(value))throw StorageException.invalid();}
 public Page list(StoreScope scope,CurrentActor actor,String kind,String query,String status,String from,String to,int page,int size){
  authorize(scope,actor);kind(kind);
  var statuses=kind.equals("deposit")?Set.of("all","stored","pending","cancelled","rejected"):Set.of("all","completed","authorized","awaiting_authorization","cancelled","expired");
  if(query==null||query.length()>120||status==null||!statuses.contains(status)||page<0||page>100000||size<1||size>100)throw StorageException.invalid();
  try{
   var zone=ZoneId.of(access.timezone(scope));LocalDate first=from==null||from.isBlank()?null:LocalDate.parse(from),last=to==null||to.isBlank()?null:LocalDate.parse(to);
   if(first!=null&&last!=null&&first.isAfter(last))throw StorageException.invalid();
   return reports.list(scope,kind,query.trim(),status,first==null?null:first.atStartOfDay(zone).toInstant(),last==null?null:last.plusDays(1).atStartOfDay(zone).toInstant(),page,size,clock.instant());
  }catch(DateTimeException e){throw StorageException.invalid();}
 }
 public Detail detail(StoreScope scope,CurrentActor actor,String kind,UUID id){
  authorize(scope,actor);kind(kind);var now=clock.instant();
  if(kind.equals("deposit")){
   var app=records.application(scope,id,false);var items=records.itemsForApplication(scope,id,now);
   var sources=new HashSet<UUID>();sources.add(id);items.forEach(i->sources.add(i.id()));
   return new Detail(app,null,items,records.events(scope,app.customerId()).stream().filter(e->sources.contains(e.sourceId())).toList());
  }
  var intent=records.intent(scope,id,false);var items=intent.selections().stream().map(l->records.item(scope,l.itemId(),false,now)).toList();
  var sources=new HashSet<UUID>();sources.add(id);items.forEach(i->sources.add(i.id()));
  return new Detail(null,intent,items,records.events(scope,intent.customerId()).stream().filter(e->sources.contains(e.sourceId())).toList());
 }
}
