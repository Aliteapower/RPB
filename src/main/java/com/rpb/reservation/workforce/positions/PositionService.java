package com.rpb.reservation.workforce.positions;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.positions.PositionDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class PositionService {
 private final WorkforceAccess access;private final PositionRepository repository;private final WorkforceAudit audit;
 public PositionService(WorkforceAccess access,PositionRepository repository,WorkforceAudit audit){this.access=access;this.repository=repository;this.audit=audit;}
 public List<Position> list(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);return repository.list(actor.tenantId());}
 @Transactional public Position save(CurrentActor actor,UUID store,UUID id,PositionWrite c){
  access.sourceWrite(actor);access.enter(actor,store);access.headOffice(actor);
  if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.enabled()==null||c.name()==null||c.name().trim().isEmpty()||c.name().trim().length()>80)throw WorkforceException.invalid();
  repository.lock(actor.tenantId());var old=repository.find(actor.tenantId(),id).orElse(null);String name=c.name().trim();String mode=c.attendanceMode()==null?(old==null?null:old.attendanceMode()):com.rpb.reservation.workforce.workmode.WorkModeRules.mode(c.attendanceMode());
  if(repository.deleted(actor.tenantId(),id).isPresent())throw WorkforceException.missing();
  if(old!=null&&old.name().equals(name)&&old.enabled()==c.enabled()&&Objects.equals(old.attendanceMode(),mode)&&(c.expectedVersion()==old.version()||c.expectedVersion()==old.version()-1))return old;
  if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
  if(repository.list(actor.tenantId()).stream().anyMatch(p->!p.id().equals(id)&&p.name().equalsIgnoreCase(name)))throw WorkforceException.conflict();
  var saved=new Position(id,name,c.enabled(),c.expectedVersion()+1,mode);repository.save(actor.tenantId(),saved,old==null);audit.changed(actor,store,"position.save",id,old,saved);return saved;
 }
 @Transactional public Deleted delete(CurrentActor actor,UUID store,UUID id,DeleteWrite c){
  access.sourceWrite(actor);access.enter(actor,store);access.headOffice(actor);
  if(c==null||c.expectedVersion()==null||c.expectedVersion()<1)throw WorkforceException.invalid();
  repository.lock(actor.tenantId());var gone=repository.deleted(actor.tenantId(),id).orElse(null);
  if(gone!=null){if(gone.version()==c.expectedVersion()+1)return gone;throw WorkforceException.conflict();}
  var old=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();
  if(repository.used(actor.tenantId(),id))throw new WorkforceException("WORKFORCE_POSITION_IN_USE",409);
  var deleted=new Deleted(id,true,old.version()+1);repository.delete(actor.tenantId(),id,deleted.version());audit.changed(actor,store,"position.delete",id,old,deleted);return deleted;
 }
}
