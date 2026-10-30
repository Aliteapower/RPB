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
    public List<SalaryTemplate> listTemplates(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);return repository.listTemplates(actor.tenantId(),company);}
    public ProfilePage profiles(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);return new ProfilePage(repository.profiles(actor.tenantId(),company),repository.employees(actor.tenantId(),company),repository.employeeBirthDates(actor.tenantId(),company),repository.employeePrDocuments(actor.tenantId(),company));}
    @Transactional public SalaryTemplate saveTemplate(CurrentActor actor,UUID store,UUID id,TemplateWrite c){
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.enabled()==null)throw WorkforceException.invalid();
        access.enter(actor,store,c.companyId());String name=text(c.name(),100,true);Rule rule=rule(c.rule());gate.sourceWrite(actor);repository.lock(id);
        var old=repository.findTemplate(actor.tenantId(),id).orElse(null);
        if(old!=null&&!old.companyId().equals(c.companyId()))throw WorkforceException.forbidden();
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.name().equals(name)&&old.rule().equals(rule)&&old.enabled()==c.enabled())return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        var next=new SalaryTemplate(id,c.companyId(),name,rule,c.enabled(),c.expectedVersion()+1);repository.saveTemplate(actor.tenantId(),next);audit.changed(actor,store,"salary-template.save",id,old,next);return next;
    }
    @Transactional public PayProfile saveProfile(CurrentActor actor,UUID store,UUID id,ProfileWrite c){
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.employeeId()==null||c.effectiveFrom()==null||c.templateId()==null)throw WorkforceException.invalid();
        access.enter(actor,store,c.companyId());Rule rule=rule(c.rule());gate.sourceWrite(actor);employees.lock(c.employeeId());repository.lock(id);access.employee(actor,c.companyId(),c.employeeId());
        var employee=employees.find(actor.tenantId(),c.employeeId()).orElseThrow(WorkforceException::missing);
        CpfProfile cpf=cpf(c.cpf(),employee.birthDate());
        if(cpf.prDocumentId()!=null){var evidence=repository.prDocument(actor.tenantId(),employee.id(),cpf.prDocumentId()).orElseThrow(WorkforceException::missing);if(!evidence.prEffectiveOn().equals(cpf.prDate()))throw WorkforceException.invalid();}
        var old=repository.findProfile(actor.tenantId(),id).orElse(null);
        var payment=PaymentPreferences.validate(c.payment()==null&&old!=null?old.payment():c.payment());
        var cdac=CdacPayroll.profile(c.cdac()==null&&old!=null?old.cdac():c.cdac(),cpf,rule);
        if(old!=null&&(!old.companyId().equals(c.companyId())||!old.employeeId().equals(c.employeeId())||!old.effectiveFrom().equals(c.effectiveFrom())))throw WorkforceException.forbidden();
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.templateId().equals(c.templateId())&&old.rule().equals(rule)&&old.cpf().equals(cpf)&&Objects.equals(old.payment(),payment)&&Objects.equals(old.cdac(),cdac))return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        if(old!=null&&repository.lockedProfile(actor.tenantId(),id))throw new WorkforceException("WORKFORCE_PAYROLL_PROFILE_LOCKED",409);
        if(c.effectiveFrom().isBefore(employee.hireDate()))throw WorkforceException.invalid();
        var template=repository.findTemplate(actor.tenantId(),c.templateId()).orElseThrow(WorkforceException::missing);
        if(!template.companyId().equals(c.companyId())||(!template.enabled()&&(old==null||!old.templateId().equals(template.id()))))throw WorkforceException.invalid();
        var next=new PayProfile(id,c.companyId(),employee.id(),employee.name(),c.effectiveFrom(),template.id(),template.version(),rule,cpf,c.expectedVersion()+1,payment,cdac);
        repository.saveProfile(actor.tenantId(),next);audit.changed(actor,store,"pay-profile.save",id,old,next);return next;
    }
    private Rule rule(Rule r){
        if(r==null||!Set.of("monthly","daily","hourly").contains(r.basis()==null?"":r.basis())||r.items()==null||r.items().size()>20)throw WorkforceException.invalid();
        BigDecimal rate=decimal(r.rate()),day=decimal(r.dayHours()),week=decimal(r.weekHours()),multiplier=decimal(r.overtimeMultiplier());
        if(day.signum()<=0||day.compareTo(BigDecimal.valueOf(r.overtimeEnabled()?12:24))>0||week.signum()<=0||week.compareTo(BigDecimal.valueOf(r.overtimeEnabled()?44:168))>0||multiplier.signum()<=0||multiplier.compareTo(BigDecimal.TEN)>0||(r.overtimeEnabled()&&multiplier.compareTo(new BigDecimal("1.50"))<0))throw WorkforceException.invalid();
        var generation=PayrollGeneration.validate(r);
        var items=r.items().stream().map(i->{if(i==null||!PayItemKinds.supported(i.kind()))throw WorkforceException.invalid();return new Item(text(i.label(),100,true),i.kind(),decimal(i.amount()).toPlainString(),PayrollGeneration.proration(i));}).toList();
        return new Rule(r.basis(),rate.toPlainString(),day.toPlainString(),week.toPlainString(),r.overtimeEnabled(),multiplier.toPlainString(),items,generation);
    }
    private BigDecimal decimal(String value){
        if(value==null||!value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))throw WorkforceException.invalid();
        return new BigDecimal(value).setScale(2,RoundingMode.UNNECESSARY);
    }
    private CpfProfile cpf(CpfProfile p,LocalDate employeeBirthDate){
        if(p!=null&&!"foreign".equals(p.citizenship())&&employeeBirthDate!=null)p=new CpfProfile(p.citizenship(),employeeBirthDate,p.prDate(),p.prMode(),p.electionReference(),p.prDocumentId());
        if(p==null||!Set.of("citizen","spr","foreign").contains(p.citizenship()==null?"":p.citizenship())||!Set.of("GG","FG","FF").contains(p.prMode()==null?"":p.prMode()))throw WorkforceException.invalid();
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
        if((!p.citizenship().equals("foreign")&&p.birthDate()==null)||(p.birthDate()!=null&&p.birthDate().isAfter(today)))throw WorkforceException.invalid();
        String note=text(p.electionReference(),200,false);
        if(p.citizenship().equals("spr")){
            if(p.prDate()==null||p.prDate().isAfter(today)||p.prDate().isBefore(p.birthDate())||(!p.prMode().equals("GG")&&note.isBlank()))throw WorkforceException.invalid();
        }else if(p.prDate()!=null||p.prDocumentId()!=null)throw WorkforceException.invalid();
        return new CpfProfile(p.citizenship(),p.birthDate(),p.prDate(),p.prMode(),note,p.prDocumentId());
    }
    private String text(String s,int max,boolean required){var value=s==null?"":s.trim();if(value.length()>max||(required&&value.isBlank()))throw WorkforceException.invalid();return value;}
}
