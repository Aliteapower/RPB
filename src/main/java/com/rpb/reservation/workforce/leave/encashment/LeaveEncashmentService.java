package com.rpb.reservation.workforce.leave.encashment;

import static com.rpb.reservation.workforce.leave.encashment.LeaveEncashmentDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.compliance.*;
import com.rpb.reservation.workforce.leave.account.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.governance.*;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

/** Creates a valued source and an ordinary wage-change approval; never approves its own request. */
@Service @Transactional(readOnly=true)
public class LeaveEncashmentService {
    private final WorkforceAccess gate;
    private final PayrollAccess access;
    private final WorkforceEmployeeRepository employees;
    private final LeaveAccountRepository accounts;
    private final LeaveBalanceEngine balances;
    private final ComplianceRepository policies;
    private final LeaveEncashmentRepository repo;
    private final PayrollConfigurationRepository configs;
    private final PayChangeService changes;
    private final SupplementalService supplemental;
    private final WorkforceAudit audit;
    public LeaveEncashmentService(WorkforceAccess gate,PayrollAccess access,WorkforceEmployeeRepository employees,
            LeaveAccountRepository accounts,LeaveBalanceEngine balances,ComplianceRepository policies,
            LeaveEncashmentRepository repo,PayrollConfigurationRepository configs,PayChangeService changes,
            SupplementalService supplemental,WorkforceAudit audit) {
        this.gate=gate;this.access=access;this.employees=employees;this.accounts=accounts;this.balances=balances;
        this.policies=policies;this.repo=repo;this.configs=configs;this.changes=changes;this.supplemental=supplemental;this.audit=audit;
    }
    private WorkforceDtos.Employee enter(CurrentActor a,UUID store,UUID employee) {
        var e=employees.find(a.tenantId(),employee).orElseThrow(WorkforceException::missing);
        access.enter(a,store,e.companyId()); access.employee(a,e.companyId(),employee);return e;
    }
    public List<Document> list(CurrentActor a,UUID store,UUID employee) {enter(a,store,employee);return repo.list(a.tenantId(),employee);}
    public Context context(CurrentActor a,UUID store,UUID employee,int year){var e=enter(a,store,employee);if(year<2000||year>2100)throw WorkforceException.invalid();return new Context(e.id(),e.name(),accounts.accounts(a.tenantId(),e.id(),year).stream().filter(x->x.companyId().equals(e.companyId())&&Set.of("annual","company").contains(x.kind())).map(x->balances.balance(a.tenantId(),x)).toList(),PayChangeDrafts.effective(configs.employeeProfiles(a.tenantId(),e.companyId(),e.id()),balances.today()));}
    @Transactional public Document create(CurrentActor a,UUID store,UUID id,Write c) {
        if(id==null)throw WorkforceException.invalid();validate(c);
        gate.sourceWrite(a);var e=enter(a,store,c.employeeId());
        if(a.actorId().equals(e.accountId()))throw WorkforceException.forbidden();
        employees.lock(e.id());policies.lock(a.tenantId(),"encashment:"+id);
        var old=repo.find(a.tenantId(),id).orElse(null);
        if(old!=null){if(!old.request().equals(c)||!old.creatorId().equals(a.actorId()))throw WorkforceException.conflict();return old;}
        var account=accounts.find(a.tenantId(),c.accountId()).orElseThrow(WorkforceException::missing);
        if(!account.employeeId().equals(e.id())||!account.companyId().equals(e.companyId())||!Set.of("annual","company").contains(account.kind())
                ||c.valuationOn().getYear()!=account.year()||c.valuationOn().isBefore(account.openingOn()))throw WorkforceException.invalid();
        var valuation=value(a,e,account,c);
        if(c.expectedValuation()!=null&&!c.expectedValuation().equals(valuation))throw new WorkforceException("LEAVE_ENCASHMENT_QUOTE_CHANGED",409);
        var amount=valuation.amount();
        var month=YearMonth.from(c.payableOn());
        if(c.route().equals("monthly")&&c.originMonth()!=null&&!month.toString().equals(c.originMonth()))throw WorkforceException.invalid();
        // One independently payable encashment award; its evidence fixes AW and payable month.
        var event=new WageEvents.Evidence(id,"leave_encashment","AW",LocalDate.of(account.year(),1,1),c.valuationOn(),c.valuationOn(),c.payableOn(),month.toString(),month.toString(),c.evidence(),"Separately approved unused leave encashment; payable month recorded");
        var item=new PayrollDtos.Item("未休年假折现","wage_event",amount,null,id,null,event);
        balances.reserve(a,account,id,"encashment",c.valuationOn(),c.quantity());
        UUID requestId=UUID.randomUUID();
        var document=new Document(id,e.id(),e.companyId(),account.id(),a.actorId(),c,valuation,item,"reserved",requestId,null);
        repo.create(a.tenantId(),document);
        PayChangeDtos.Request request;
        if(c.route().equals("monthly")) {
            var prior=changes.monthlyBaseline(a,store,e.companyId(),e.id(),month.toString());
            var items=new ArrayList<>(prior.items());items.add(item);
            var input=new PayrollDtos.InputWrite(prior.version(),prior.workingDays(),prior.payableDays(),prior.unitsReviewed(),prior.specialDaysReviewed(),items,prior.note().isBlank()?"Leave encashment approval "+id:prior.note(),prior.cdacReview());
            request=changes.save(a,store,requestId,new PayChangeDtos.Write(0L,e.companyId(),e.id(),c.valuationOn(),null,null,List.of(new PayChangeDtos.MonthWrite(month.toString(),input)),null,c.evidence()));
        } else {
            // Additional wages belong to their payable contribution month, even when settling an old leave-year balance.
            var source=supplemental.source(a,store,e.companyId(),e.id(),month.toString());
            if(!source.issues().isEmpty())throw new WorkforceException(source.issues().getFirst(),409);
            var proposal=new SupplementalDtos.Proposal(id,month.toString(),month.toString(),source.source(),List.of(item),c.evidence());
            request=changes.save(a,store,requestId,new PayChangeDtos.Write(0L,e.companyId(),e.id(),c.valuationOn(),null,null,List.of(),null,c.evidence(),null,List.of(proposal)));
        }
        // A save can reuse an existing open request; source must bind to its actual persisted identity.
        document=new Document(id,e.id(),e.companyId(),account.id(),a.actorId(),c,valuation,item,"reserved",request.id(),null);
        repo.save(a.tenantId(),document);
        changes.action(a,store,request.id(),new PayChangeDtos.Action(UUID.randomUUID(),request.revision(),request.hash(),"submit",c.evidence()));
        audit.changed(a,store,"leave-encashment.submit",id,null,document);return repo.find(a.tenantId(),id).orElseThrow();
    }
    private Valuation value(CurrentActor a,WorkforceDtos.Employee e,LeaveAccountDtos.Account account,Write c) {
        var contract=balances.contract(a.tenantId(),account);var policy=balances.policy(a.tenantId(),account);
        var profile=PayChangeDrafts.effective(configs.employeeProfiles(a.tenantId(),e.companyId(),e.id()),c.valuationOn());
        if(profile==null||configs.effective().context(a.tenantId(),e.companyId(),YearMonth.from(c.valuationOn())).profile(profile)==null)
            throw new WorkforceException("WORKFORCE_PAYROLL_PROFILE_MISSING",409);
        var r=profile.rule();var rate=new BigDecimal(r.rate());
        var monthlyBasic=switch(r.basis()){case "monthly"->rate;case "daily"->rate.multiply(contract.weeklyDays()).multiply(new BigDecimal("52")).divide(new BigDecimal("12"),12,RoundingMode.HALF_UP);case "hourly"->rate.multiply(contract.weeklyHours()).multiply(new BigDecimal("52")).divide(new BigDecimal("12"),12,RoundingMode.HALF_UP);default->throw WorkforceException.invalid();};
        boolean partFour=ComplianceRules.partFour(policy.rules(),contract.category(),monthlyBasic);
        if(c.purpose().equals("termination")) {
            if(e.leaveDate()==null||!e.leaveDate().equals(c.valuationOn()))throw new WorkforceException("LEAVE_TERMINATION_EVIDENCE_REQUIRED",409);
        } else if(c.purpose().equals("company_extra")?(!account.kind().equals("company")||!policy.rules().allowCompanyExtraEncashment()):(!account.kind().equals("annual")||partFour||!policy.rules().allowContractualEncashment()))
            throw new WorkforceException("LEAVE_ENCASHMENT_NOT_ALLOWED",409);
        // Embedded leave in hourly wages is a different arrangement and is never inferred here.
        var classifications=c.classifications()==null?List.<Classification>of():c.classifications();
        var selected=new HashMap<UUID,Classification>();
        for(var x:classifications)if(x==null||x.itemId()==null||x.reason()==null||x.reason().isBlank()||x.reason().length()>500||selected.put(x.itemId(),x)!=null)throw WorkforceException.invalid();
        BigDecimal included=BigDecimal.ZERO;
        for(var item:r.items()) {
            var x=item.itemId()==null?null:selected.remove(item.itemId());
            if(x==null)throw new WorkforceException("LEAVE_GROSS_CLASSIFICATION_REQUIRED",409);
            if(x.included()) {
                // Only separately reviewed ordinary fixed allowances may enter gross; excluded kinds cannot be overridden.
                if(!item.kind().equals("ow_allowance"))throw new WorkforceException("LEAVE_GROSS_COMPONENT_EXCLUDED",409);
                included=included.add(new BigDecimal(item.amount()));
            }
        }
        if(!selected.isEmpty())throw WorkforceException.invalid();
        var monthlyGross=monthlyBasic.add(included);
        BigDecimal unitRate;
        String formula;
        if(account.unit().equals("day")) {
            unitRate=monthlyGross.multiply(new BigDecimal("12")).divide(contract.weeklyDays().multiply(new BigDecimal("52")),12,RoundingMode.HALF_UP);
            formula="12 × eligible monthly gross ÷ (52 × contracted days/week) × leave days";
        } else {
            unitRate=monthlyGross.multiply(new BigDecimal("12")).divide(contract.weeklyHours().multiply(new BigDecimal("52")),12,RoundingMode.HALF_UP);
            formula="12 × eligible monthly gross ÷ (52 × contracted hours/week) × leave hours";
        }
        String amount=unitRate.multiply(c.quantity()).setScale(2,RoundingMode.HALF_UP).toPlainString();
        return new Valuation(profile.id(),profile.version(),contract.id(),policy.id(),policy.version(),r.basis(),account.unit(),unitRate.toPlainString(),formula,List.copyOf(classifications),amount);
    }
    private void validate(Write c) {
        if(c==null||c.employeeId()==null||c.accountId()==null||c.quantity()==null||c.quantity().signum()<=0
                ||c.quantity().scale()>6||c.valuationOn()==null||c.payableOn()==null||c.payableOn().isBefore(c.valuationOn())
                ||c.valuationOn().isAfter(balances.today())||c.evidence()==null||c.evidence().isBlank()||c.evidence().length()>1000
                ||!Set.of("termination","contractual","company_extra").contains(Objects.toString(c.purpose(),""))
                ||!Set.of("monthly","supplemental").contains(Objects.toString(c.route(),"")))throw WorkforceException.invalid();
    }
    public Quote preview(CurrentActor a,UUID store,Write c) {
        validate(c);var e=enter(a,store,c.employeeId());if(a.actorId().equals(e.accountId()))throw WorkforceException.forbidden();
        var account=accounts.find(a.tenantId(),c.accountId()).orElseThrow(WorkforceException::missing);
        if(!account.employeeId().equals(e.id())||!account.companyId().equals(e.companyId())||!Set.of("annual","company").contains(account.kind())||c.valuationOn().getYear()!=account.year()||c.valuationOn().isBefore(account.openingOn()))throw WorkforceException.invalid();
        var balance=balances.balance(a.tenantId(),account);return new Quote(value(a,e,account,c),balance.available(),balance.issues());
    }
}
