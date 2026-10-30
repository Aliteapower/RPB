package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.positions.PositionRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;import java.time.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class PositionSalaryDefaultsService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollConfigurationRepository repository;private final WorkforceEmployeeRepository employees;private final PositionRepository positions;private final WorkforceAudit audit;
 public PositionSalaryDefaultsService(WorkforceAccess gate,PayrollAccess access,PayrollConfigurationRepository repository,WorkforceEmployeeRepository employees,PositionRepository positions,WorkforceAudit audit){this.gate=gate;this.access=access;this.repository=repository;this.employees=employees;this.positions=positions;this.audit=audit;}
 public List<PositionSalaryDefault> list(CurrentActor actor,UUID store,UUID company,UUID position){access.enter(actor,store,company);positions.find(actor.tenantId(),position).orElseThrow(WorkforceException::missing);return repository.defaults(actor.tenantId(),company,position);}
 public ProfilePreview preview(CurrentActor actor,UUID store,UUID company,UUID employee,LocalDate on,UUID template){
  access.enter(actor,store,company);access.employee(actor,company,employee);if(on==null)throw WorkforceException.invalid();
  var e=employees.find(actor.tenantId(),employee).orElseThrow(WorkforceException::missing);PositionSalaryDefault mapping=null;
  if(template==null&&e.positionId()!=null){mapping=repository.defaults(actor.tenantId(),company,e.positionId()).stream().filter(d->!d.effectiveFrom().isAfter(on)).reduce((a,b)->b).orElse(null);if(mapping==null||!mapping.enabled())return new ProfilePreview(null,null);template=mapping.templateId();}
  if(template==null)return new ProfilePreview(null,null);
  repository.assertTemplateAccess(actor.tenantId(),store,template,false,on);var t=repository.findTemplate(actor.tenantId(),template).orElseThrow(WorkforceException::missing);if(!t.companyId().equals(company)||!t.enabled())throw WorkforceException.invalid();return new ProfilePreview(t,mapping);
 }
 @Transactional public PositionSalaryDefault save(CurrentActor actor,UUID store,UUID id,DefaultWrite c){
  if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.positionId()==null||c.effectiveFrom()==null||c.templateId()==null||c.enabled()==null)throw WorkforceException.invalid();
  access.enter(actor,store,c.companyId());gate.sourceWrite(actor);repository.lockDefaults(actor.tenantId(),c.companyId());positions.lock(actor.tenantId());repository.lock(c.templateId());repository.lock(id);
  var position=positions.find(actor.tenantId(),c.positionId()).orElseThrow(WorkforceException::missing);if(!position.enabled())throw WorkforceException.invalid();
  repository.assertTemplateAccess(actor.tenantId(),store,c.templateId(),false,c.effectiveFrom());var t=repository.findTemplate(actor.tenantId(),c.templateId()).orElseThrow(WorkforceException::missing);if(!t.companyId().equals(c.companyId())||!t.enabled())throw WorkforceException.invalid();
  var old=repository.findDefault(actor.tenantId(),id).orElse(null);
  if(old!=null&&(!old.companyId().equals(c.companyId())||!old.positionId().equals(c.positionId())||!old.effectiveFrom().equals(c.effectiveFrom())))throw WorkforceException.forbidden();
  if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.templateId().equals(c.templateId())&&old.enabled()==c.enabled())return old;
  if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
  if(repository.defaults(actor.tenantId(),c.companyId(),c.positionId()).stream().anyMatch(d->!d.id().equals(id)&&d.effectiveFrom().equals(c.effectiveFrom())))throw WorkforceException.conflict();
  var next=new PositionSalaryDefault(id,c.companyId(),c.positionId(),c.effectiveFrom(),t.id(),t.version(),c.enabled(),c.expectedVersion()+1);repository.saveDefault(actor.tenantId(),next);audit.changed(actor,store,"position-salary-default.save",id,old,next);return next;
 }
}
