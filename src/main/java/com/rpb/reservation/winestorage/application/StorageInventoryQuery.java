package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.StorageInventoryRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StorageInventoryQuery {
 private final StorageAccess access;private final StorageInventoryRepository repository;private final Clock clock;
 public StorageInventoryQuery(StorageAccess access,StorageInventoryRepository repository,Clock clock){this.access=access;this.repository=repository;this.clock=clock;}
 public StorageInventoryDtos.Page list(StoreScope scope,CurrentActor actor,String query,String status,int page,int size){
  access.staff(scope,actor,"wine_storage.view",false);
  String normalized=filter(query,status);
  if(page<0||page>100000||size<1||size>100)throw StorageException.invalid();
  return repository.list(scope,normalized,status,page,size,clock.instant());
 }
 static String filter(String query,String status){if(query==null||query.length()>120||status==null||!Set.of("all","stored","expired").contains(status))throw StorageException.invalid();return query.trim();}
}
