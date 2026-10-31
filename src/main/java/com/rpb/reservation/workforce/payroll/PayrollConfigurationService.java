package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PayrollConfigurationService {
    private final WorkforceAccess gate;
    private final PayrollAccess access;
    private final PayrollConfigurationRepository repository;
    private final WorkforceEmployeeRepository employees;
    private final WorkforceAudit audit;
    private final Clock clock;
    public PayrollConfigurationService(WorkforceAccess gate,PayrollAccess access,PayrollConfigurationRepository repository,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock){this.gate=gate;this.access=access;this.repository=repository;this.employees=employees;this.audit=audit;this.clock=clock;}
    public PayrollHome home(CurrentActor actor,UUID store){
        gate.enter(actor,store);
        var policy=repository.activePolicy(actor.tenantId(),YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore"))));
        var summary=policy==null?new Policy("UNCONFIGURED",0,"0","","",List.of("No active statutory policy is configured. Payroll calculation is blocked.")):
            new Policy(policy.id()+"@"+policy.version(),policy.year(),policy.config().owCeiling(),policy.config().cpfSource(),policy.config().sdlSource(),
                List.of(policy.config().notes(),"SGD Ordinary Wages only; Additional Wages and cross-period adjustments are not supported.","Salary records do not submit CPF or transfer money."));
        return new PayrollHome(access.companies(actor,store),employees.byAccount(actor.tenantId(),actor.actorId()).map(e->e.id()).orElse(null),summary);
    }
    public List<SalaryTemplate> listTemplates(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);return repository.listTemplates(actor.tenantId(),company,store);}
    public ProfilePage profiles(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);return new ProfilePage(repository.profiles(actor.tenantId(),company),repository.employees(actor.tenantId(),company),repository.employeeBirthDates(actor.tenantId(),company),repository.employeePrDocuments(actor.tenantId(),company));}
    @Transactional public SalaryTemplate saveTemplate(CurrentActor actor,UUID store,UUID id,TemplateWrite c){
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.enabled()==null)throw WorkforceException.invalid();
        access.enter(actor,store,c.companyId());String name=text(c.name(),100,true);Rule rule=PayrollRules.normalize(c.rule());gate.sourceWrite(actor);repository.lock(id);
        var old=repository.findTemplate(actor.tenantId(),id).orElse(null);
        if(repository.managedTemplate(actor.tenantId(),id))throw new WorkforceException("WORKFORCE_MANAGED_TEMPLATE_IMMUTABLE",409);
        if(old!=null&&!old.companyId().equals(c.companyId()))throw WorkforceException.forbidden();
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.name().equals(name)&&old.rule().equals(rule)&&old.enabled()==c.enabled()){repository.effective().assertTemplate(actor.tenantId(),old);return old;}
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        var next=new SalaryTemplate(id,c.companyId(),name,rule,c.enabled(),c.expectedVersion()+1);repository.effective().unchangedTemplate(actor.tenantId(),next);repository.saveTemplate(actor.tenantId(),next);audit.changed(actor,store,"salary-template.save",id,old,next);return next;
    }
    @Transactional public PayProfile saveProfile(CurrentActor actor,UUID store,UUID id,ProfileWrite c){
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.employeeId()==null||c.effectiveFrom()==null||c.templateId()==null)throw WorkforceException.invalid();
        access.enter(actor,store,c.companyId());Rule rule=PayrollRules.normalize(c.rule());gate.sourceWrite(actor);repository.lockSubject(actor.tenantId(),c.companyId(),c.employeeId());repository.lockDefaults(actor.tenantId(),c.companyId());employees.lock(c.employeeId());repository.lock(id);access.employee(actor,c.companyId(),c.employeeId());
        var employee=employees.find(actor.tenantId(),c.employeeId()).orElseThrow(WorkforceException::missing);
        CpfProfile cpf=cpf(c.cpf(),employee.birthDate());
        if(cpf.prDocumentId()!=null){var evidence=repository.prDocument(actor.tenantId(),employee.id(),cpf.prDocumentId()).orElseThrow(WorkforceException::missing);if(!evidence.prEffectiveOn().equals(cpf.prDate()))throw WorkforceException.invalid();}
        var old=repository.findProfile(actor.tenantId(),id).orElse(null);
        repository.assertTemplateAccess(actor.tenantId(),store,c.templateId(),old!=null&&old.templateId().equals(c.templateId()),c.effectiveFrom());
        var payment=PaymentPreferences.validate(c.payment()==null&&old!=null?old.payment():c.payment());
        var cdac=CdacPayroll.profile(c.cdac()==null&&old!=null?old.cdac():c.cdac(),cpf,rule);
        if(old!=null&&(!old.companyId().equals(c.companyId())||!old.employeeId().equals(c.employeeId())||!old.effectiveFrom().equals(c.effectiveFrom())))throw WorkforceException.forbidden();
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.templateId().equals(c.templateId())&&(c.expectedTemplateVersion()==null||(old.origin()!=null&&old.templateVersion()==c.expectedTemplateVersion()&&Objects.equals(old.origin()==null?null:old.origin().defaultMappingId(),c.defaultMappingId())&&Objects.equals(old.origin()==null?null:old.origin().defaultVersion(),c.expectedDefaultVersion())))&&old.rule().equals(rule)&&old.cpf().equals(cpf)&&Objects.equals(old.payment(),payment)&&Objects.equals(old.cdac(),cdac)){repository.effective().assertProfile(actor.tenantId(),old);return old;}
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        if(old!=null&&repository.lockedProfile(actor.tenantId(),id))throw new WorkforceException("WORKFORCE_PAYROLL_PROFILE_LOCKED",409);
        if(c.effectiveFrom().isBefore(employee.hireDate()))throw WorkforceException.invalid();
        repository.lock(c.templateId());
        var template=repository.findTemplate(actor.tenantId(),c.templateId()).orElseThrow(WorkforceException::missing);
        if(!template.companyId().equals(c.companyId())||(!template.enabled()&&(old==null||!old.templateId().equals(template.id()))))throw WorkforceException.invalid();
        ProfileOrigin origin=old==null?null:old.origin();long templateVersion=old==null?template.version():old.templateVersion();
        if(c.expectedTemplateVersion()!=null){
            if(c.expectedTemplateVersion()!=template.version())throw WorkforceException.conflict();
            if(!template.enabled())throw WorkforceException.invalid();
            PositionSalaryDefault mapping=null;
            if(c.defaultMappingId()!=null){
                if(c.defaultBasis()!=null&&!c.defaultBasis().equals(template.rule().basis()))throw WorkforceException.invalid();
                repository.lock(c.defaultMappingId());mapping=repository.findDefault(actor.tenantId(),c.defaultMappingId()).orElseThrow(WorkforceException::missing);
                if(c.expectedDefaultVersion()==null||mapping.version()!=c.expectedDefaultVersion())throw WorkforceException.conflict();
                if(mapping.basis()!=null&&!mapping.basis().equals(template.rule().basis()))throw WorkforceException.invalid();
                if(!mapping.enabled()||!mapping.companyId().equals(c.companyId())||!mapping.positionId().equals(employee.positionId())||!mapping.templateId().equals(template.id())||mapping.effectiveFrom().isAfter(c.effectiveFrom()))throw WorkforceException.invalid();
                var applicable=repository.applicableDefault(actor.tenantId(),c.companyId(),employee.positionId(),c.defaultBasis()!=null?c.defaultBasis():mapping.basis(),c.effectiveFrom());
                if(applicable==null||!applicable.id().equals(mapping.id()))throw WorkforceException.conflict();
            }else if(c.expectedDefaultVersion()!=null||c.defaultBasis()!=null)throw WorkforceException.invalid();
            origin=new ProfileOrigin(template.name(),template.rule(),mapping==null?null:mapping.id(),mapping==null?null:mapping.version());templateVersion=template.version();
        }else {if(c.defaultMappingId()!=null||c.expectedDefaultVersion()!=null||c.defaultBasis()!=null)throw WorkforceException.invalid();if(old!=null&&!old.templateId().equals(template.id())){origin=null;templateVersion=template.version();}}
        var next=new PayProfile(id,c.companyId(),employee.id(),employee.name(),c.effectiveFrom(),template.id(),templateVersion,rule,cpf,c.expectedVersion()+1,payment,cdac,origin);
        repository.effective().directProfile(actor.tenantId(),store,next);repository.saveProfile(actor.tenantId(),next);audit.changed(actor,store,"pay-profile.save",id,old,next);return next;
    }
    private CpfProfile cpf(CpfProfile p,LocalDate employeeBirthDate){return PayrollProfileValidation.cpf(p,employeeBirthDate,clock);}
    private String text(String s,int max,boolean required){var value=s==null?"":s.trim();if(value.length()>max||(required&&value.isBlank()))throw WorkforceException.invalid();return value;}
}
