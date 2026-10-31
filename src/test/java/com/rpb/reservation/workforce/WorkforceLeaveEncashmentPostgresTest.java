package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.leave.encashment.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.governance.*;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkforceLeaveEncashmentPostgresTest extends WorkforceLeaveAccountPostgresTest {
    LeaveEncashmentRepository encashRepo;
    LeaveEncashmentBridge encashBridge;
    LeaveEncashmentService encash;
    PayChangeService changes;
    CurrentActor reviewer;
    SupplementalService supplements;
    @BeforeEach void encashSetup() {
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());
        var pa=new PayrollAccess(access,directory,jdbc);
        encashRepo=new LeaveEncashmentRepository(jdbc,json);
        encashBridge=new LeaveEncashmentBridge(encashRepo,balances,policies);
        changes=payChanges();changes.encashments(encashBridge);sources.encashments(encashBridge);batches.encashments(encashBridge);
        supplements=proxy(new SupplementalService(access,pa,new SupplementalRepository(jdbc,json),repository,audit,jdbc,json,time));
        supplements.encashments(encashBridge);
        encash=proxy(new LeaveEncashmentService(access,pa,repository,accounts,balances,policies,encashRepo,configs,changes,supplements,audit));
        var reviewerId=account("tenant_admin",Set.of(store,sibling,otherStore),Set.of("workforce.view","tenant.admin.manage","workforce.admin.manage"));
        reviewer=new CurrentActor(tenant,reviewerId,"staff",Set.of("tenant_admin"),Set.of("workforce.view","tenant.admin.manage","workforce.admin.manage"),Set.of(store,sibling,otherStore));
    }
    LeaveEncashmentDtos.Document prepare(){return prepare("monthly",LocalDate.of(2026,11,3));}
    LeaveEncashmentDtos.Document prepare(String route,LocalDate payable) {
        setupAccounts("2","14","60");
        var r=new PayrollDtos.Rule("hourly","10","8","40",false,"1.5",List.of());
        legacyProfile(new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Synthetic leave employee",LocalDate.of(2026,1,1),template,1,r,new CpfProfile("foreign",LocalDate.of(1990,1,1),null,"GG",""),1));
        jdbc.update("update workforce_employees set leave_date='2026-11-02',status='left' where tenant_id=? and id=?",tenant,employee);
        time.at("2026-11-03T00:00:00Z");
        return encash.create(admin,store,UUID.randomUUID(),new LeaveEncashmentDtos.Write(employee,balance("annual",2026).account().id(),n("1"),LocalDate.of(2026,11,2),payable,"termination","Ordinary termination; no misconduct or notice offset; reviewed contract",List.of(),route,YearMonth.from(payable).toString()));
    }
    @Test void encashmentUsesExistingApprovalThenFormalPayrollConsumesExactlyOnce() {
        var d=prepare();assertThat(d.valuation().amount()).isEqualTo("80.00");
        assertThat(balance("annual",2026).reserved()).isEqualByComparingTo("1");
        var r=changes.detail(admin,store,d.requestId());
        var selfRequest=r;assertThatThrownBy(()->changes.action(admin,store,selfRequest.id(),new PayChangeDtos.Action(UUID.randomUUID(),selfRequest.revision(),selfRequest.hash(),"approve","Self approval"))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
        var ownReviewer=new CurrentActor(tenant,employeeAccount,"staff",Set.of("tenant_admin"),Set.of("workforce.view","tenant.admin.manage","workforce.admin.manage"),Set.of(store,sibling,otherStore));assertThatThrownBy(()->changes.action(ownReviewer,store,selfRequest.id(),new PayChangeDtos.Action(UUID.randomUUID(),selfRequest.revision(),selfRequest.hash(),"approve","Cannot review own leave cash"))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
        r=changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Independent eligibility and gross rate verified"));
        assertThat(r.status()).isEqualTo("approved");assertThat(encashRepo.find(tenant,d.id()).orElseThrow().status()).isEqualTo("approved");
        var input=r.content().months().getFirst().after();
        // Explicit zero-hour review reuses the existing wage-change chain; the cash source remains immutable.
        var request=changes.save(admin,store,UUID.randomUUID(),new PayChangeDtos.Write(0L,company,employee,LocalDate.of(2026,11,3),null,null,List.of(new PayChangeDtos.MonthWrite("2026-11",new PayrollDtos.InputWrite(input.version(),null,null,true,true,input.items(),"No attendance wages due; termination cash only"))),null,"Reconcile zero attendance"));
        request=changes.action(admin,store,request.id(),new PayChangeDtos.Action(UUID.randomUUID(),request.revision(),request.hash(),"submit","Submit"));
        changes.action(reviewer,store,request.id(),new PayChangeDtos.Action(UUID.randomUUID(),request.revision(),request.hash(),"approve","Reconciled zero attendance"));
        // Remove an unrelated inherited synthetic employee from this test month, without touching any real data.
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);
        time.at("2026-12-02T00:00:00Z");
        var b=batches.create(admin,store,UUID.randomUUID(),new PayrollDtos.BatchWrite(company,"2026-11"));
        b=action(b,"calculate");assertThat(b.calculation().issues()).isEmpty();assertThat(b.calculation().totalGross()).isEqualTo("80.00");
        b=action(b,"confirm");assertThat(balance("annual",2026).encashed()).isEqualByComparingTo("1");
        var formal=b;assertThatThrownBy(()->action(formal,"void")).hasMessage("LEAVE_ENCASHMENT_FORMAL_CORRECTION_REQUIRED");
        var snapshot=b.calculation();b=action(b,"publish");assertThat(b.calculation()).isEqualTo(snapshot);
        assertThat(payslips.mineDetail(worker,store,b.batch().id()).line().components()).anyMatch(c->c.label().equals("未休年假折现")&&c.amount().equals("80.00"));
        assertThat(jdbc.queryForObject("select count(*) from workforce_leave_encashment_consumption where tenant_id=? and source_id=?",Integer.class,tenant,d.id())).isEqualTo(1);
        assertThat(encashRepo.find(tenant,d.id()).orElseThrow().status()).isEqualTo("consumed");
    }
    @Test void rejectedEncashmentReleasesBalanceAndRetryDoesNotReserveAgain() {
        var d=prepare();assertThat(encash.create(admin,store,d.id(),d.request())).isEqualTo(d);
        var r=changes.detail(admin,store,d.requestId());changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"reject","Unsupported termination evidence"));
        assertThat(balance("annual",2026).available()).isEqualByComparingTo("2");assertThat(balance("annual",2026).reserved()).isZero();
        assertThat(encashRepo.find(tenant,d.id()).orElseThrow().status()).isEqualTo("released");
    }

    @Test void finalTerminationSegmentCanSettleEncashmentWithoutMonthEndDuplication(){
        var d=prepare();var r=changes.detail(admin,store,d.requestId());changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Independent review"));
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());var segments=proxy(new com.rpb.reservation.workforce.payroll.segment.SegmentService(access,new PayrollAccess(access,directory,jdbc),new com.rpb.reservation.workforce.payroll.segment.SegmentRepository(jdbc,json),payRepository,sources,new PayrollCalculationService(time),repository,audit,json,jdbc,time));segments.encashments(encashBridge);
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);
        var s=segments.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.segment.SegmentDtos.Write(0L,company,employee,"2026-11",LocalDate.of(2026,11,2),null,null,true,true,"Reviewed termination settlement with zero attendance"));
        s=segments.action(admin,store,s.id(),UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.segment.SegmentDtos.Action(s.version(),"calculate","Calculate"));assertThat(s.calculation().issues()).isEmpty();assertThat(s.calculation().delta().gross()).isEqualTo("80.00");
        s=segments.action(admin,store,s.id(),UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.segment.SegmentDtos.Action(s.version(),"confirm","Confirm"));assertThat(balance("annual",2026).encashed()).isEqualByComparingTo("1");
        var month=sources.capture(tenant,company,YearMonth.of(2026,11),List.of());assertThat(month.lines()).noneMatch(l->l.employee().id().equals(employee));
        assertThat(jdbc.queryForObject("select count(*) from workforce_wage_event_consumptions where tenant_id=? and event_id=? and active",Integer.class,tenant,d.id())).isEqualTo(1);
        var annual=new com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository(jdbc,json,time).capture(tenant,company,employee,2026);assertThat(annual.months().stream().filter(m->m.month().equals("2026-11")).findFirst().orElseThrow().events()).hasSize(1);
    }
    @Test void postExitEncashmentUsesIndependentSupplementAndRetainsOriginalLeaveYear(){
        var d=prepare("supplemental",LocalDate.of(2026,12,3));var r=changes.detail(admin,store,d.requestId());r=changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Independent review"));
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);time.at("2026-12-04T00:00:00Z");
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());var statutory=proxy(new com.rpb.reservation.workforce.statutory.settlement.SettlementService(access,new PayrollAccess(access,directory,jdbc),directory,new com.rpb.reservation.workforce.statutory.settlement.SettlementRepository(jdbc,json),new SupplementalRepository(jdbc,json),jdbc,json,audit,time));
        statutory.save(admin,store,company,employee,"2026-12",new com.rpb.reservation.workforce.statutory.settlement.SettlementDtos.Evidence(UUID.randomUUID(),0L,"Reviewed zero original deductions and liabilities for post-employment payable month",null,"0","0",new com.rpb.reservation.workforce.statutory.settlement.SettlementDtos.Amounts("0","0",null,"0"),null,"0",new PayrollDtos.CdacProfile("not_applicable","Foreign employee"),null,"0",null,null));
        statutory.company(admin,store,company,"2026-12",new com.rpb.reservation.workforce.statutory.settlement.SettlementDtos.CompanyEvidence(UUID.randomUUID(),0L,"Reviewed whole-company zero original December SDL",List.of(employee),"0","0"));
        var b=supplements.save(admin,store,UUID.randomUUID(),new SupplementalDtos.Write(UUID.randomUUID(),0L,company,employee,r.id(),r.revision(),r.hash(),d.id()));
        b=supplements.action(admin,store,b.batch().id(),new SupplementalDtos.Action(UUID.randomUUID(),b.batch().version(),"calculate",null,"Calculate"));assertThat(b.batch().calculation().issues()).isEmpty();assertThat(b.batch().calculation().grossDelta()).isEqualTo("80.00");
        b=supplements.action(admin,store,b.batch().id(),new SupplementalDtos.Action(UUID.randomUUID(),b.batch().version(),"confirm",b.batch().calculation().sourceFingerprint(),"Confirm"));assertThat(balance("annual",2026).encashed()).isEqualByComparingTo("1");assertThat(b.batch().originMonth()).isEqualTo("2026-12");assertThat(b.batch().proposal().items().getFirst().eventEvidence().serviceTo()).isEqualTo(LocalDate.of(2026,11,2));
    }

    @Test void confirmationFailureRollsBackBothLeaveBankAndUniqueSourceConsumption(){var d=prepare();var r=changes.detail(admin,store,d.requestId());changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Reviewed"));assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{encashBridge.consume(admin,company,employee,UUID.randomUUID(),"monthly",List.of(d.item()));throw new IllegalStateException("Injected failure after source consumption");})).hasMessage("Injected failure after source consumption");assertThat(balance("annual",2026).reserved()).isEqualByComparingTo("1");assertThat(balance("annual",2026).encashed()).isZero();assertThat(encashRepo.find(tenant,d.id()).orElseThrow().status()).isEqualTo("approved");assertThat(jdbc.queryForObject("select count(*) from workforce_leave_encashment_consumption where tenant_id=? and source_id=?",Integer.class,tenant,d.id())).isZero();assertThatThrownBy(()->encashBridge.validate(tenant,otherCompany,employee,List.of(d.item()),true)).hasMessage("LEAVE_ENCASHMENT_SOURCE_CHANGED");assertThatThrownBy(()->encash.list(worker,store,employee)).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);}
    @Test void staleApprovalReleasesReservedLeaveAndReplayCannotLockItAgain(){var d=prepare();var r=changes.detail(admin,store,d.requestId());jdbc.update("update workforce_employees set phone='synthetic-change',version=version+1 where tenant_id=? and id=?",tenant,employee);var action=new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Recheck source");var stale=changes.action(reviewer,store,r.id(),action);assertThat(stale.status()).isEqualTo("stale");assertThat(changes.action(reviewer,store,r.id(),action)).isEqualTo(stale);assertThat(encashRepo.find(tenant,d.id()).orElseThrow().status()).isEqualTo("released");assertThat(balance("annual",2026).reserved()).isZero();assertThat(balance("annual",2026).available()).isEqualByComparingTo("2");}

    @Test void citizenEncashmentReusesAnnualCpfSdlAndAutomaticCdac(){
        setupAccounts("2","14","60");time.at("2026-12-01T00:00:00Z");var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());var statutes=new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json);for(var old:statutes.list(tenant))if(old.enabled())statutes.save(tenant,new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(old.id(),old.name(),old.year(),old.currency(),old.effectiveFrom(),old.effectiveTo(),false,old.version()+1,old.config()));var seed=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYearWithAw(2026);var policy=new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(UUID.randomUUID(),seed.name(),2026,"SGD",seed.effectiveFrom(),seed.effectiveTo(),true,1,seed.config());statutes.save(tenant,policy);var cpf=new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");var rule=new PayrollDtos.Rule("monthly","1000","8","40",false,"1.5",List.of());legacyProfile(new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Citizen synthetic",LocalDate.of(2026,11,1),template,1,rule,cpf,1,null,new PayrollDtos.CdacProfile("applicable","Employee declaration verified")));
        jdbc.update("update workforce_employees set leave_date='2026-11-30',status='left' where tenant_id=? and id=?",tenant,employee);jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);
        var annualRepo=new com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository(jdbc,json,time);var years=proxy(new com.rpb.reservation.workforce.statutory.annual.AnnualWageService(access,new PayrollAccess(access,directory,jdbc),directory,annualRepo,statutes,repository,audit,time));var zero=new ArrayList<com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.OpeningMonth>();for(int m=1;m<=10;m++)zero.add(new com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.OpeningMonth(YearMonth.of(2026,m).toString(),"known-zero","0",cpf,policy.id(),1L,"0","0","Reviewed synthetic prior-month zero wages, not inferred",List.of()));years.save(admin,store,company,employee,2026,new com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.Write(UUID.randomUUID(),0L,zero,"Reconciled complete annual source"));
        var d=encash.create(admin,store,UUID.randomUUID(),new LeaveEncashmentDtos.Write(employee,balance("annual",2026).account().id(),n("1"),LocalDate.of(2026,11,30),LocalDate.of(2026,11,30),"termination","Ordinary termination without notice offset or misconduct",List.of(),"monthly","2026-11"));assertThat(d.valuation().amount()).isEqualTo("46.15");var r=changes.detail(admin,store,d.requestId());r=changes.action(reviewer,store,r.id(),new PayChangeDtos.Action(UUID.randomUUID(),r.revision(),r.hash(),"approve","Annual leave value verified"));var input=r.content().months().getFirst().after();var units=changes.save(admin,store,UUID.randomUUID(),new PayChangeDtos.Write(0L,company,employee,LocalDate.of(2026,11,30),null,null,List.of(new PayChangeDtos.MonthWrite("2026-11",new PayrollDtos.InputWrite(input.version(),"21","21",true,true,input.items(),"Complete month units independently reviewed"))),null,"Review salary units"));units=changes.action(admin,store,units.id(),new PayChangeDtos.Action(UUID.randomUUID(),units.revision(),units.hash(),"submit","Submit"));changes.action(reviewer,store,units.id(),new PayChangeDtos.Action(UUID.randomUUID(),units.revision(),units.hash(),"approve","Units confirmed"));
        var b=batches.create(admin,store,UUID.randomUUID(),new PayrollDtos.BatchWrite(company,"2026-11"));b=action(b,"calculate");assertThat(b.calculation().issues()).isEmpty();var line=b.calculation().lines().getFirst();assertThat(line.grossPay()).isEqualTo("1046.15");assertThat(line.aw().cpfAW()).isEqualTo("46.15");assertThat(line.employeeCpf()).isEqualTo("209.00");assertThat(line.employerCpf()).isEqualTo("178.00");assertThat(line.cdacCalculation().amount()).isEqualTo("0.50");assertThat(line.netPay()).isEqualTo("836.65");b=action(b,"confirm");assertThat(balance("annual",2026).encashed()).isEqualByComparingTo("1");
    }
}
