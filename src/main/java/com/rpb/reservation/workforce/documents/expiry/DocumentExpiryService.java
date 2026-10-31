package com.rpb.reservation.workforce.documents.expiry;
import com.rpb.reservation.workforce.application.WorkforceAccess;import com.rpb.reservation.workforce.domain.WorkforceException;import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.Set;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
@Service
public class DocumentExpiryService {
 private final WorkforceAccess access;private final DocumentExpiryRepository repository;private final Clock clock;
 public DocumentExpiryService(WorkforceAccess access,DocumentExpiryRepository repository,Clock clock){this.access=access;this.repository=repository;this.clock=clock;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public DocumentExpiryDtos.Page list(CurrentActor actor,java.util.UUID store,int days,String status,int limit,int offset){return list(actor,store,days,status,limit,offset,null);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public DocumentExpiryDtos.Page list(CurrentActor actor,java.util.UUID store,int days,String status,int limit,int offset,java.util.UUID employee){access.enter(actor,store);access.manage(actor,store);if(days<1||days>365||!Set.of("all","due","expired").contains(status==null?"":status)||limit<1||limit>100||offset<0)throw WorkforceException.invalid();String timezone=repository.timezone(actor.tenantId(),store);LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(timezone)));return repository.list(actor.tenantId(),store,access.managedStores(actor),timezone,today,days,status,limit,offset,employee);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public DocumentExpiryDtos.EmployeePage employeeCounts(CurrentActor actor,java.util.UUID store,int days,java.util.List<java.util.UUID> ids){access.enter(actor,store);access.manage(actor,store);if(days<1||days>365||ids==null||ids.isEmpty()||ids.size()>100||ids.stream().anyMatch(java.util.Objects::isNull))throw WorkforceException.invalid();String timezone=repository.timezone(actor.tenantId(),store);LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(timezone)));return repository.employeeCounts(actor.tenantId(),store,access.managedStores(actor),timezone,today,days,ids.stream().distinct().sorted().toList());}
}
