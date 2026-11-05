package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

/** A customer-issued code already authorizes exact bottles. Scanning is the staff handover command. */
@Service
public class StorageScanDeliveryService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;private final StorageWithdrawalService withdrawals;
 public StorageScanDeliveryService(StorageAccess access,StorageRepository r,StorageCommands commands,StorageWithdrawalService withdrawals){this.access=access;this.r=r;this.commands=commands;this.withdrawals=withdrawals;}
 @Transactional public Intent deliver(StoreScope scope,CurrentActor actor,ScanDelivery c){
  access.staff(scope,actor,"wine_storage.deliver",false);
  if(c==null||c.code()==null||!c.code().matches("WS\\.[A-Za-z0-9_-]{43}"))throw StorageException.invalid();
  // Replay before resolving the code: the previous successful command has already invalidated it.
  return commands.run(scope,actor.actorId(),"staff","scan.deliver",c.idempotencyKey(),c,Intent.class,()->{
   var identified=r.byCode(scope,StorageCommands.digest(c.code()));
   if(!identified.origin().equals("customer"))throw StorageException.conflict("请扫描顾客生成的取酒码");
   var done=withdrawals.deliver(scope,actor,identified.id(),new Key(UUID.randomUUID().toString(),"顾客出码，商户扫码核销"));
   r.event(scope,done.customerId(),done.id(),"scan.delivered",actor.actorId(),Map.of("selections",done.selections()),done.completedAt());return done;
  });
 }
}
