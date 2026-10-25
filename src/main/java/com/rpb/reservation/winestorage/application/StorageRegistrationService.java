package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;
import static com.rpb.reservation.winestorage.persistence.StorageRepository.at;

/** Staff receipt entry; never constructs a customer principal or claims customer self-service consent. */
@Service
public class StorageRegistrationService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;private final StorageApplicationService applications;private final Clock clock;
 public StorageRegistrationService(StorageAccess access,StorageRepository r,StorageCommands commands,StorageApplicationService applications,Clock clock){this.access=access;this.r=r;this.commands=commands;this.applications=applications;this.clock=clock;}
 @Transactional public Application register(StoreScope scope,CurrentActor actor,Registration command){
  access.staff(scope,actor,"wine_storage.receive",true);
  if(command==null||!command.rulesConfirmed()||command.customerId()==null||command.lines()==null||command.lines().isEmpty()||command.lines().size()>50||command.lines().stream().anyMatch(Objects::isNull)||StorageApplicationService.text(command.reason()).isBlank()||command.reason().length()>2000)throw StorageException.invalid();
  access.customerId(scope,command.customerId());
  return commands.run(scope,actor.actorId(),"staff","register",command.idempotencyKey(),command,Application.class,()->{
   UUID id=UUID.randomUUID();var now=clock.instant();var originals=new ArrayList<Line>();var receipts=new ArrayList<ReceiptLine>();
   for(var line:command.lines()){
    UUID lineId=UUID.randomUUID();
    originals.add(new Line(lineId,line.productId(),line.nameZh(),line.nameEn(),line.quantity(),line.opened(),line.source(),List.of(),StorageApplicationService.text(line.note())));
    receipts.add(new ReceiptLine(lineId,line.productId(),line.categoryId(),line.nameZh(),line.nameEn(),line.quantity(),line.opened(),line.source(),line.ruleId(),line.photos(),line.note(),line.location(),line.label()));
   }
   // The pending row exists only inside this transaction; any receive failure rolls everything back.
   var original=new Apply(originals,false,null,null,command.idempotencyKey());
   r.update(scope,"insert into wine_storage_applications(tenant_id,store_id,id,customer_id,status,original,created_at,updated_at) values(?,?,?,?,'pending',?::jsonb,?,?)",id,command.customerId(),r.json(original),at(now),at(now));
   for(var line:originals)r.update(scope,"insert into wine_storage_application_lines(tenant_id,store_id,id,customer_id,application_id,original) values(?,?,?,?,?,?::jsonb)",line.id(),command.customerId(),id,r.json(line));
   r.event(scope,command.customerId(),id,"staff.registered",actor.actorId(),Map.of("rulesConfirmed",true,"origin","staff","reason",command.reason()),now);
   return applications.receive(scope,actor,id,new Receive(receipts,command.reason(),UUID.randomUUID().toString()));
  });
 }
}
