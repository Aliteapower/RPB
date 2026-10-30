package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforcePayrollConfigurationPostgresTest extends WorkforcePostgresTest {
    PayrollAccess payrollAccess;
    PayrollConfigurationRepository configuration;
    PayrollConfigurationService payroll;
    UUID employee;
    @BeforeEach void payrollSetup(){
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        configuration=new PayrollConfigurationRepository(jdbc,json);payrollAccess=new PayrollAccess(access,directory,jdbc);
        payroll=proxy(new PayrollConfigurationService(access,payrollAccess,configuration,repository,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),clock));
        employee=UUID.randomUUID();var seed=command(0,List.of(store,sibling),company,null,null);employees.save(admin,store,employee,new WorkforceDtos.EmployeeWrite(0L,"CFG001",seed.name(),seed.phone(),seed.email(),seed.jobTitle(),seed.hireDate(),null,seed.status(),company,seed.companyEffectiveDate(),store,seed.storeIds(),null,null,null));clearInvocations(logs);
    }
    Rule rule(String rate){return new Rule("hourly",rate,"8.00","44.00",true,"1.50",List.of(new Item("Meal","reimbursement","10.00")));}
    CpfProfile cpf(){return new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");}
    SalaryTemplate template(){return payroll.saveTemplate(admin,store,UUID.randomUUID(),new TemplateWrite(0L,company,"Service",rule("12.00"),true));}
    ProfileWrite profile(long version,SalaryTemplate t){return new ProfileWrite(version,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),cpf());}
    CurrentActor manager(boolean payrollGrant,Set<UUID> shops){
        UUID id=account("store_manager",shops,Set.of("workforce.view"));admins.save(admin,store,id,new WorkforceDtos.AdministratorWrite(0L,!payrollGrant,payrollGrant?company:null));
        return new CurrentActor(tenant,id,"staff",Set.of("store_manager"),Set.of("workforce.view"),shops);
    }
    @Test void employeeManagementDoesNotGrantSalaryAccessButExplicitPayrollDoes(){
        var ordinary=manager(false,Set.of(store,sibling));assertThat(payroll.home(ordinary,store).companies()).isEmpty();
        assertThatThrownBy(()->payroll.listTemplates(ordinary,store,company)).isInstanceOf(WorkforceException.class);
        var delegated=manager(true,Set.of(store,sibling));assertThat(access.managedStores(delegated)).isEmpty();
        assertThat(payroll.home(delegated,store).companies()).extracting(WorkforceDtos.Option::id).containsExactly(company);
        assertThat(payroll.saveTemplate(delegated,store,UUID.randomUUID(),new TemplateWrite(0L,company,"Delegate",rule("10.00"),true)).rule().rate()).isEqualTo("10.00");
        assertThatThrownBy(()->payroll.listTemplates(delegated,store,otherCompany)).isInstanceOf(WorkforceException.class);
    }
    @Test void delegationCannotBypassStoreCoverageSourceStoresOrAppGate(){
        var limited=manager(true,Set.of(store));assertThatThrownBy(()->payrollAccess.enter(limited,store,company)).isInstanceOf(WorkforceException.class);
        var delegated=manager(true,Set.of(store,sibling));assertThatThrownBy(()->payrollAccess.enter(delegated,store,company,List.of(otherStore))).isInstanceOf(WorkforceException.class);
        doAnswer(c->{com.rpb.reservation.appgate.application.AppGateAccessRequest r=c.getArgument(0);return r.storeId().equals(sibling)?com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_EXPIRED):com.rpb.reservation.appgate.domain.AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());}).when(gate).evaluate(any());
        assertThatThrownBy(()->payroll.listTemplates(delegated,store,company)).isInstanceOf(WorkforceException.class).hasMessage("STORE_APP_EXPIRED");
    }
    @Test void templateCopyAndCreationReplayDoNotMutatePersonalPlan(){
        var t=template();UUID id=UUID.randomUUID();var c=profile(0,t);var p=payroll.saveProfile(admin,store,id,c);
        assertThat(payroll.saveProfile(admin,store,id,c)).isEqualTo(p);
        payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(1L,company,t.name(),rule("18.00"),true));
        assertThat(configuration.findProfile(tenant,id).orElseThrow().rule().rate()).isEqualTo("12.00");assertThat(p.templateVersion()).isEqualTo(1);
        assertThat(payroll.saveProfile(admin,store,id,c)).isEqualTo(p);
        assertThatThrownBy(()->payroll.saveProfile(admin,store,id,new ProfileWrite(0L,company,employee,c.effectiveFrom(),t.id(),rule("20.00"),cpf()))).isInstanceOf(WorkforceException.class);
    }
    @Test void historicalEmployerCanStillHaveAPlanAfterCurrentCompanyTransfer(){
        var t=template();var old=repository.find(tenant,employee).orElseThrow();var c=command(1,List.of(store,sibling),otherCompany,null,null);
        employees.save(admin,store,employee,c);
        var p=payroll.saveProfile(admin,store,UUID.randomUUID(),profile(0,t));assertThat(p.companyId()).isEqualTo(company);
        assertThat(payroll.profiles(admin,store,company).employees()).extracting(WorkforceDtos.Option::id).contains(employee);
        assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),profile(0,t))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void missingForeignEmployeeAndTemplateReferencesAreRejected(){
        var t=template();
        assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,UUID.randomUUID(),LocalDate.of(2026,10,1),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),UUID.randomUUID(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        UUID foreign=UUID.randomUUID(),shop=UUID.randomUUID();jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Foreign','active')",foreign,"WF-"+foreign);
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,?,'Foreign','active')",shop,foreign,"C-"+shop);
        assertThatThrownBy(()->payroll.listTemplates(admin,store,shop)).isInstanceOf(WorkforceException.class);
    }
    @Test void ruleAndCpfValidationRejectsUnclearOrInvalidFinancialInputs(){
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,UUID.randomUUID(),new TemplateWrite(0L,company,"Invalid",rule("1e3"),true))).isInstanceOf(WorkforceException.class);
        var t=template();
        for(var c:List.of(new CpfProfile("unknown",null,null,"GG",""),new CpfProfile("citizen",LocalDate.of(2030,1,1),null,"GG",""),new CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),"FG","")))
            assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),c))).isInstanceOf(WorkforceException.class);
    }
    @Test void confirmedProfileIsImmutableUntilItsUnpublishedBatchIsVoided(){
        var t=template();UUID id=UUID.randomUUID();var p=payroll.saveProfile(admin,store,id,profile(0,t));UUID batch=UUID.randomUUID();
        jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,confirmed_at) values(?,?,?,'Company',?,'confirmed',1,?::jsonb,?,?,now())",batch,tenant,company,LocalDate.of(2026,10,1),"{\"lines\":[{\"profile\":{\"id\":\""+id+"\"}}]}",adminId,adminId);
        var changed=new ProfileWrite(1L,company,employee,p.effectiveFrom(),t.id(),rule("20.00"),cpf());
        assertThatThrownBy(()->payroll.saveProfile(admin,store,id,changed)).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_PAYROLL_PROFILE_LOCKED");
        assertThat(payroll.saveProfile(admin,store,id,profile(0,t))).isEqualTo(p);
        var future=new ProfileWrite(0L,company,employee,LocalDate.of(2026,11,1),t.id(),rule("20.00"),cpf());
        assertThat(payroll.saveProfile(admin,store,UUID.randomUUID(),future).effectiveFrom()).isEqualTo(future.effectiveFrom());
        jdbc.update("update workforce_pay_batches set status='voided' where id=? and tenant_id=?",batch,tenant);
        assertThat(payroll.saveProfile(admin,store,id,changed).version()).isEqualTo(2);
    }
    @Test void effectiveDateCannotPrecedeEmploymentOrMoveAnExistingPlan(){
        var t=template();
        assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,9,30),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        var p=payroll.saveProfile(admin,store,UUID.randomUUID(),profile(0,t));
        assertThatThrownBy(()->payroll.saveProfile(admin,store,p.id(),new ProfileWrite(1L,company,employee,LocalDate.of(2026,11,1),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
    }
    @Test void auditFailureRollsBackBothConfigurationWrites(){
        UUID id=UUID.randomUUID();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,id,new TemplateWrite(0L,company,"Audit",rule("10.00"),true))).isInstanceOf(IllegalStateException.class);assertThat(configuration.findTemplate(tenant,id)).isEmpty();
        reset(logs);var t=template();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        UUID p=UUID.randomUUID();assertThatThrownBy(()->payroll.saveProfile(admin,store,p,profile(0,t))).isInstanceOf(IllegalStateException.class);assertThat(configuration.findProfile(tenant,p)).isEmpty();
    }
}
