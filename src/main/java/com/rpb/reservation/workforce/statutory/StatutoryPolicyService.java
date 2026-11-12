package com.rpb.reservation.workforce.statutory;

import static com.rpb.reservation.workforce.statutory.StatutoryPolicies.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class StatutoryPolicyService {
 private final WorkforceAccess access;private final StatutoryPolicyRepository repository;private final WorkforceAudit audit;private final com.rpb.reservation.workforce.persistence.WorkforceDirectory directory;
 public StatutoryPolicyService(WorkforceAccess access,StatutoryPolicyRepository repository,WorkforceAudit audit,com.rpb.reservation.workforce.persistence.WorkforceDirectory directory){this.access=access;this.repository=repository;this.audit=audit;this.directory=directory;}
 private void enter(CurrentActor actor,UUID store){access.enter(actor,store);access.headOffice(actor);var stores=directory.stores(actor.tenantId());if(stores.isEmpty())throw WorkforceException.forbidden();for(var option:stores){if(!actor.canAccessStore(option.id()))throw WorkforceException.forbidden();access.enter(actor,option.id());}}
 public Page list(CurrentActor actor,UUID store){enter(actor,store);return new Page(repository.list(actor.tenantId()),true);}
 public Template template(CurrentActor actor,UUID store,int year){enter(actor,store);return OfficialStatutoryTemplates.forYear(year);}
 public List<Policy> versions(CurrentActor actor,UUID store,UUID id){enter(actor,store);if(repository.list(actor.tenantId()).stream().noneMatch(p->p.id().equals(id)))throw WorkforceException.missing();return repository.versions(actor.tenantId(),id);}
 @Transactional public Policy save(CurrentActor actor,UUID store,UUID id,Write c){
  access.sourceWrite(actor);enter(actor,store);
  if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.name()==null||c.name().isBlank()||c.name().trim().length()>100||c.year()==null||c.year()<2000||c.year()>2100||!"SGD".equals(c.currency())||c.enabled()==null||c.effectiveFrom()==null||c.effectiveTo()==null||c.effectiveFrom().getYear()!=c.year()||c.effectiveTo().getYear()!=c.year()||c.effectiveFrom().getDayOfMonth()!=1||!c.effectiveTo().equals(YearMonth.from(c.effectiveTo()).atEndOfMonth())||c.effectiveTo().isBefore(c.effectiveFrom()))throw WorkforceException.invalid();
  StatutoryPolicyConfig.validate(c.config());if(c.config().cdac()!=null&&(c.config().cdac().effectiveFrom().isBefore(c.effectiveFrom())||c.config().cdac().effectiveTo().isAfter(c.effectiveTo())))throw WorkforceException.invalid();repository.lock(actor.tenantId());var rows=repository.list(actor.tenantId());var old=rows.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);
  var next=new Policy(id,c.name().trim(),c.year(),c.currency(),c.effectiveFrom(),c.effectiveTo(),c.enabled(),c.expectedVersion()+1,c.config());
  if(old!=null&&(old.version()==c.expectedVersion()||old.version()==c.expectedVersion()+1)&&same(old,next))return old;
  if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
  if(next.enabled()&&rows.stream().anyMatch(p->!p.id().equals(id)&&p.enabled()&&!p.effectiveFrom().isAfter(next.effectiveTo())&&!p.effectiveTo().isBefore(next.effectiveFrom())))throw new WorkforceException("WORKFORCE_STATUTORY_OVERLAP",409);
  repository.save(actor.tenantId(),next);audit.changed(actor,store,"statutory-policy.save",id,old,next);return next;
 }
 private boolean same(Policy a,Policy b){return a.name().equals(b.name())&&a.year()==b.year()&&a.currency().equals(b.currency())&&a.effectiveFrom().equals(b.effectiveFrom())&&a.effectiveTo().equals(b.effectiveTo())&&a.enabled()==b.enabled()&&a.config().equals(b.config());}
}
