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
    SalaryTemplate legacyTemplate(String name,Rule rule){var t=new SalaryTemplate(UUID.randomUUID(),company,name,PayrollRules.normalize(rule),true,1);var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());jdbc.update("insert into workforce_pay_templates(id,tenant_id,company_id,name,snapshot,enabled,version) values(?,?,?,?,?::jsonb,true,1)",t.id(),tenant,company,t.name(),json.write(t));jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) values(?,?,?,'legacy-valid','template',?,1,?::jsonb)",UUID.randomUUID(),tenant,company,t.id(),json.write(t));return t;}
    SalaryTemplate template(){return legacyTemplate("Service",rule("12.00"));}
    PayProfile approvedProfile(CurrentActor actor,UUID shop,UUID id,ProfileWrite c){try{return payroll.saveProfile(actor,shop,id,c);}catch(WorkforceException e){if(!e.code().equals("WORKFORCE_PAYROLL_APPROVAL_REQUIRED"))throw e;}var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var r=new com.rpb.reservation.workforce.payroll.governance.PayChangeRepository(jdbc,json);var pubs=new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository(jdbc,json,clock);var a=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());var service=proxy(new com.rpb.reservation.workforce.payroll.governance.PayChangeService(access,payrollAccess,r,configuration,pubs,repository,a,new com.rpb.reservation.workforce.payroll.governance.PayChangeDrafts(r,configuration,pubs,repository,clock),clock));var declaration=c.cdac();if(declaration==null&&!c.cpf().citizenship().equals("foreign")&&c.rule().items().stream().noneMatch(i->i.kind().equals("cdac"))&&configuration.employeeProfiles(tenant,c.companyId(),c.employeeId()).isEmpty())declaration=new CdacProfile("not_applicable","Explicit synthetic applicability declaration");var proposed=new ProfileWrite(c.expectedVersion(),c.companyId(),c.employeeId(),c.effectiveFrom(),c.templateId(),c.rule(),c.cpf(),c.payment(),declaration,c.expectedTemplateVersion(),null,null);var request=service.save(actor,shop,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Write(0L,c.companyId(),c.employeeId(),c.effectiveFrom(),null,proposed,List.of(),null,"Explicit test salary change"));request=service.action(actor,shop,request.id(),new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Action(UUID.randomUUID(),request.revision(),request.hash(),"submit",""));request=service.action(admin,shop,request.id(),new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Action(UUID.randomUUID(),request.revision(),request.hash(),"approve","Test approval"));assertThat(request.status()).isEqualTo("approved");return request.content().profile();}
    PayProfile installedProfile(UUID id,ProfileWrite c){var p=new PayProfile(id,c.companyId(),c.employeeId(),"Alice",c.effectiveFrom(),c.templateId(),1,PayrollRules.normalize(c.rule()),c.cpf(),1);var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());jdbc.update("insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,version,snapshot) values(?,?,?,?,?,?,1,1,?::jsonb)",id,tenant,p.companyId(),p.employeeId(),p.effectiveFrom(),p.templateId(),json.write(p));jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) values(?,?,?,'legacy-valid','profile',?,1,?::jsonb)",UUID.randomUUID(),tenant,p.companyId(),id,json.write(p));return p;}
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
        assertThatThrownBy(()->payroll.saveTemplate(delegated,store,UUID.randomUUID(),new TemplateWrite(0L,company,"Delegate",rule("10.00"),true))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
        assertThatThrownBy(()->payroll.listTemplates(delegated,store,otherCompany)).isInstanceOf(WorkforceException.class);
    }
    @Test void delegationCannotBypassStoreCoverageSourceStoresOrAppGate(){
        var limited=manager(true,Set.of(store));assertThatThrownBy(()->payrollAccess.enter(limited,store,company)).isInstanceOf(WorkforceException.class);
        var delegated=manager(true,Set.of(store,sibling));assertThatThrownBy(()->payrollAccess.enter(delegated,store,company,List.of(otherStore))).isInstanceOf(WorkforceException.class);
        doAnswer(c->{com.rpb.reservation.appgate.application.AppGateAccessRequest r=c.getArgument(0);return r.storeId().equals(sibling)?com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_EXPIRED):com.rpb.reservation.appgate.domain.AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());}).when(gate).evaluate(any());
        assertThatThrownBy(()->payroll.listTemplates(delegated,store,company)).isInstanceOf(WorkforceException.class).hasMessage("STORE_APP_EXPIRED");
    }
    @Test void templateCopyAndCreationReplayDoNotMutatePersonalPlan(){
        var t=template();UUID requested=UUID.randomUUID();var c=profile(0,t);var p=approvedProfile(admin,store,requested,c);UUID id=p.id();
        assertThat(approvedProfile(admin,store,p.id(),c)).isEqualTo(p);
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(1L,company,t.name(),rule("18.00"),true))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
        assertThat(configuration.findProfile(tenant,id).orElseThrow().rule().rate()).isEqualTo("12.00");assertThat(p.templateVersion()).isEqualTo(1);
        assertThat(approvedProfile(admin,store,p.id(),c)).isEqualTo(p);
        assertThatThrownBy(()->approvedProfile(admin,store,p.id(),new ProfileWrite(0L,company,employee,c.effectiveFrom(),t.id(),rule("20.00"),cpf()))).isInstanceOf(WorkforceException.class);
    }
    @Test void historicalEmployerCanStillHaveAPlanAfterCurrentCompanyTransfer(){
        var t=template();var old=repository.find(tenant,employee).orElseThrow();var c=command(1,List.of(store,sibling),otherCompany,null,null);
        employees.save(admin,store,employee,c);
        var p=approvedProfile(admin,store,UUID.randomUUID(),profile(0,t));assertThat(p.companyId()).isEqualTo(company);
        assertThat(payroll.profiles(admin,store,company).employees()).extracting(WorkforceDtos.Option::id).contains(employee);
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),profile(0,t))).hasMessage("WORKFORCE_CONFLICT");assertThat(configuration.profiles(tenant,company)).containsExactly(p);
    }
    @Test void missingForeignEmployeeAndTemplateReferencesAreRejected(){
        var t=template();
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,UUID.randomUUID(),LocalDate.of(2026,10,1),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),UUID.randomUUID(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        UUID foreign=UUID.randomUUID(),shop=UUID.randomUUID();jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Foreign','active')",foreign,"WF-"+foreign);
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,?,'Foreign','active')",shop,foreign,"C-"+shop);
        assertThatThrownBy(()->payroll.listTemplates(admin,store,shop)).isInstanceOf(WorkforceException.class);
    }
    @Test void ruleAndCpfValidationRejectsUnclearOrInvalidFinancialInputs(){
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,UUID.randomUUID(),new TemplateWrite(0L,company,"Invalid",rule("1e3"),true))).isInstanceOf(WorkforceException.class);
        var t=template();
        for(var c:List.of(new CpfProfile("unknown",null,null,"GG",""),new CpfProfile("citizen",LocalDate.of(2030,1,1),null,"GG",""),new CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),"FG","")))
            assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),c))).isInstanceOf(WorkforceException.class);
    }
    @Test void confirmedProfileIsImmutableUntilItsUnpublishedBatchIsVoided(){
        var t=template();UUID requested=UUID.randomUUID();var p=approvedProfile(admin,store,requested,profile(0,t));UUID id=p.id();UUID batch=UUID.randomUUID();
        jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,confirmed_at) values(?,?,?,'Company',?,'confirmed',1,?::jsonb,?,?,now())",batch,tenant,company,LocalDate.of(2026,10,1),"{\"lines\":[{\"profile\":{\"id\":\""+id+"\"}}]}",adminId,adminId);
        var changed=new ProfileWrite(1L,company,employee,p.effectiveFrom(),t.id(),rule("20.00"),cpf());
        assertThatThrownBy(()->approvedProfile(admin,store,p.id(),changed)).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_PAYROLL_PROFILE_LOCKED");
        assertThat(approvedProfile(admin,store,p.id(),profile(0,t))).isEqualTo(p);
        var future=new ProfileWrite(0L,company,employee,LocalDate.of(2026,11,1),t.id(),rule("20.00"),cpf());
        assertThat(approvedProfile(admin,store,UUID.randomUUID(),future).effectiveFrom()).isEqualTo(future.effectiveFrom());
        jdbc.update("update workforce_pay_batches set status='voided' where id=? and tenant_id=?",batch,tenant);
        assertThat(approvedProfile(admin,store,p.id(),changed).version()).isEqualTo(2);
    }
    @Test void effectiveDateCannotPrecedeEmploymentOrMoveAnExistingPlan(){
        var t=template();
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,9,30),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
        var p=approvedProfile(admin,store,UUID.randomUUID(),profile(0,t));
        assertThatThrownBy(()->approvedProfile(admin,store,p.id(),new ProfileWrite(1L,company,employee,LocalDate.of(2026,11,1),t.id(),t.rule(),cpf()))).isInstanceOf(WorkforceException.class);
    }
    @Test void auditFailureRollsBackBothConfigurationWrites(){
        var existing=template();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,existing.id(),new TemplateWrite(1L,company,"Audit",existing.rule(),true))).isInstanceOf(IllegalStateException.class);assertThat(configuration.findTemplate(tenant,existing.id()).orElseThrow()).isEqualTo(existing);
        reset(logs);var t=template();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        UUID p=UUID.randomUUID();assertThatThrownBy(()->approvedProfile(admin,store,p,profile(0,t))).isInstanceOf(IllegalStateException.class);assertThat(configuration.findProfile(tenant,p)).isEmpty();
    }
    @Test void personalEditRetainsAppliedTemplateVersionAfterTemplateChanges(){
        var t=template();var p=approvedProfile(admin,store,UUID.randomUUID(),profile(0,t));
        assertThatThrownBy(()->payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(1L,company,t.name(),rule("18.00"),true))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
        var edited=approvedProfile(admin,store,p.id(),new ProfileWrite(1L,company,employee,p.effectiveFrom(),t.id(),rule("14.00"),cpf()));
        assertThat(edited.templateVersion()).isEqualTo(1);
    }    ProfileWrite applied(long version,SalaryTemplate t,String rate,PositionSalaryDefault d){return new ProfileWrite(version,company,employee,LocalDate.of(2026,10,1),t.id(),rule(rate),cpf(),null,null,t.version(),d==null?null:d.id(),d==null?null:d.version());}
    @Test void previewVersionConflictAndPersonalOverrideCaptureOriginalBaseline(){
        var t=template();var p=approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"14.00",null));
        assertThat(p.origin().templateName()).isEqualTo("Service");assertThat(p.origin().templateRule().rate()).isEqualTo("12.00");
        var newer=payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(1L,company,"Renamed source",t.rule(),true));
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"14.00",null))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CONFLICT");
        var edited=approvedProfile(admin,store,p.id(),new ProfileWrite(1L,company,employee,p.effectiveFrom(),t.id(),rule("15.00"),cpf()));
        assertThat(edited.origin().templateName()).isEqualTo(newer.name());assertThat(edited.templateVersion()).isEqualTo(2);
    }
    @Test void defaultPreviewSelectsByDateAndRejectsStaleMappingWithoutUpdatingExistingProfile(){
        var defaults=proxy(new PositionSalaryDefaultsService(access,payrollAccess,configuration,repository,new com.rpb.reservation.workforce.positions.PositionRepository(jdbc),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));
        var t=template();var position=repository.find(tenant,employee).orElseThrow().positionId();
        var d=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,position,LocalDate.of(2026,10,1),t.id(),true));
        var future=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,position,LocalDate.of(2026,11,1),t.id(),true));
        assertThat(defaults.preview(admin,store,company,employee,LocalDate.of(2026,10,31),null).defaultMapping()).isEqualTo(d);
        assertThat(defaults.preview(admin,store,company,employee,LocalDate.of(2026,11,1),null).defaultMapping()).isEqualTo(future);
        var p=approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"14.00",d));
        defaults.save(admin,store,d.id(),new DefaultWrite(1L,company,position,d.effectiveFrom(),t.id(),false));
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"14.00",d))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CONFLICT");
        assertThat(configuration.findProfile(tenant,p.id()).orElseThrow()).isEqualTo(p);
        assertThatThrownBy(()->defaults.list(manager(false,Set.of(store,sibling)),store,company,position)).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,otherCompany,position,d.effectiveFrom(),t.id(),true))).isInstanceOf(WorkforceException.class);
    }
    @Test void employeeSummarySeparatesCurrentFutureHistoryAndProtectsSalary(){
        var t=template();var p=approvedProfile(admin,store,UUID.randomUUID(),profile(0,t));
        approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,11,1),t.id(),rule("18.00"),cpf()));
        var summaries=new EmployeePaySummaryService(payrollAccess,configuration);
        var summary=summaries.summary(admin,store,employee,company,LocalDate.of(2026,10,31));
        assertThat(summary.profiles()).extracting(ProfilePeriod::state).containsExactly("current","future");
        assertThat(summary.profiles().getFirst().effectiveTo()).isEqualTo(LocalDate.of(2026,10,31));assertThat(summary.profiles().getFirst().sourceComplete()).isTrue();
        assertThat(summaries.summary(admin,store,employee,company,LocalDate.of(2026,11,1)).profiles()).extracting(ProfilePeriod::state).containsExactly("history","current");
        assertThatThrownBy(()->summaries.summary(manager(false,Set.of(store,sibling)),store,employee,company,p.effectiveFrom())).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->summaries.summary(admin,store,UUID.randomUUID(),company,p.effectiveFrom())).isInstanceOf(WorkforceException.class);
    }
    @Test void stoppedTemplatesCannotBeAppliedAndSourceReapplicationCapturesNewVersionEvenForSameRule(){
        var t=template();var p=approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"12.00",null));
        var newer=payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(1L,company,"Renamed",t.rule(),true));
        var rebased=approvedProfile(admin,store,p.id(),applied(1,newer,"12.00",null));
        assertThat(rebased.templateVersion()).isEqualTo(2);assertThat(rebased.origin().templateName()).isEqualTo("Renamed");
        var stopped=payroll.saveTemplate(admin,store,t.id(),new TemplateWrite(2L,company,"Renamed",t.rule(),false));
        assertThatThrownBy(()->approvedProfile(admin,store,UUID.randomUUID(),applied(0,stopped,"12.00",null))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_INVALID");
        assertThat(configuration.findProfile(tenant,p.id()).orElseThrow()).isEqualTo(rebased);
    }
    @Test void defaultAuditRollbackAndUniqueDateAndCompanyFkAreEnforced(){
        var defaults=proxy(new PositionSalaryDefaultsService(access,payrollAccess,configuration,repository,new com.rpb.reservation.workforce.positions.PositionRepository(jdbc),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));
        var t=template();var position=repository.find(tenant,employee).orElseThrow().positionId();var id=UUID.randomUUID();var c=new DefaultWrite(0L,company,position,LocalDate.of(2026,10,1),t.id(),true);
        var d=defaults.save(admin,store,id,c);assertThat(defaults.save(admin,store,id,c)).isEqualTo(d);
        assertThatThrownBy(()->defaults.save(admin,store,UUID.randomUUID(),c)).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CONFLICT");
        when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        assertThatThrownBy(()->defaults.save(admin,store,id,new DefaultWrite(1L,company,position,c.effectiveFrom(),t.id(),false))).isInstanceOf(IllegalStateException.class);assertThat(configuration.findDefault(tenant,id).orElseThrow()).isEqualTo(d);
        assertThatThrownBy(()->jdbc.update("insert into workforce_position_salary_defaults(id,tenant_id,company_id,position_id,effective_from,template_id,template_version,enabled,version,snapshot) values(?,?,?,?,?,?,1,true,1,'{}'::jsonb)",UUID.randomUUID(),tenant,otherCompany,position,LocalDate.of(2026,12,1),t.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void legacyTemplateSwitchClearsPreviousOriginAndOldJsonStillOmitsOrigin()throws Exception{
        var t=template();var p=approvedProfile(admin,store,UUID.randomUUID(),applied(0,t,"14.00",null));
        var other=legacyTemplate("Other",rule("20.00"));
        var switched=approvedProfile(admin,store,p.id(),new ProfileWrite(1L,company,employee,p.effectiveFrom(),other.id(),rule("21.00"),cpf()));
        assertThat(switched.origin().templateName()).isEqualTo("Other");assertThat(switched.templateVersion()).isEqualTo(other.version());
        var mapper=new ObjectMapper().findAndRegisterModules();assertThat(mapper.writeValueAsString(new PayProfile(switched.id(),switched.companyId(),switched.employeeId(),switched.employeeName(),switched.effectiveFrom(),switched.templateId(),switched.templateVersion(),switched.rule(),switched.cpf(),switched.version()))).doesNotContain("origin");
        assertThat(mapper.readValue(mapper.writeValueAsString(switched),PayProfile.class)).isEqualTo(switched);
    }
    @Test void explicitLegacySourceApplicationHonorsVersionFreezeAndSuccessfulReplay(){
        var t=template();var id=UUID.randomUUID();var legacy=installedProfile(id,profile(0,t));clearInvocations(logs);
        assertThatThrownBy(()->approvedProfile(admin,store,id,applied(0,t,"12.00",null))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CONFLICT");
        var batch=UUID.randomUUID();jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,confirmed_at) values(?,?,?,'Company',?,'confirmed',1,?::jsonb,?,?,now())",batch,tenant,company,LocalDate.of(2026,10,1),"{\"lines\":[{\"profile\":{\"id\":\""+id+"\"}}]}",adminId,adminId);
        var command=applied(1,t,"12.00",null);
        assertThatThrownBy(()->approvedProfile(admin,store,id,command)).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_PAYROLL_PROFILE_LOCKED");
        assertThat(configuration.findProfile(tenant,id).orElseThrow()).isEqualTo(legacy);
        jdbc.update("update workforce_pay_batches set status='voided' where id=? and tenant_id=?",batch,tenant);
        var saved=approvedProfile(admin,store,id,command);assertThat(saved.origin()).isNotNull();assertThat(saved.version()).isEqualTo(2);
        assertThat(approvedProfile(admin,store,id,command)).isEqualTo(saved);assertThat(configuration.findProfile(tenant,id).orElseThrow()).isEqualTo(saved);
        verify(logs,times(1)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
    }
    @Test void basisSpecificDefaultsCoexistAndDisabledDoesNotResurrectLegacy() {
        var defaults=proxy(new PositionSalaryDefaultsService(access,payrollAccess,configuration,repository,new com.rpb.reservation.workforce.positions.PositionRepository(jdbc),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));
        assertThat(defaults.options(admin,store,company)).isNotEmpty();
        var h=template();var m=legacyTemplate("Monthly",new Rule("monthly","2500.00","8.00","44.00",false,"1.50",List.of()));
        var pos=repository.find(tenant,employee).orElseThrow().positionId();var date=LocalDate.of(2026,10,1);
        var old=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,h.id(),true));
        assertThat(defaults.preview(admin,store,company,employee,date,null,"hourly").template().id()).isEqualTo(h.id());
        assertThat(defaults.preview(admin,store,company,employee,date,null,"monthly").template()).isNull();
        var md=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,m.id(),true,"monthly"));
        var hd=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,h.id(),false,"hourly"));
        assertThat(defaults.preview(admin,store,company,employee,date,null,"monthly").defaultMapping()).isEqualTo(md);
        assertThat(defaults.preview(admin,store,company,employee,date,null,"hourly").template()).isNull();
        assertThat(defaults.preview(admin,store,company,employee,date,null).defaultMapping()).isEqualTo(old);
        assertThatThrownBy(()->defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,h.id(),true,"monthly"))).hasMessage("WORKFORCE_INVALID");
        assertThat(defaults.list(admin,store,company,pos,"hourly")).containsExactly(hd);
        defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date.plusMonths(1),h.id(),true,"hourly"));
        assertThat(defaults.preview(admin,store,company,employee,date,null,"hourly").template()).isNull();
        assertThat(defaults.preview(admin,store,company,employee,date.plusMonths(1),null,"hourly").template().id()).isEqualTo(h.id());
    }

    @Test void newBasisPreviewCannotSubmitLegacyFallbackAfterANewDefaultTakesOver(){
        var defaults=proxy(new PositionSalaryDefaultsService(access,payrollAccess,configuration,repository,new com.rpb.reservation.workforce.positions.PositionRepository(jdbc),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));
        var t=template();var pos=repository.find(tenant,employee).orElseThrow().positionId();var date=LocalDate.of(2026,10,1);
        var legacy=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,t.id(),true));
        assertThat(defaults.preview(admin,store,company,employee,date,null,"hourly").defaultMapping().id()).isEqualTo(legacy.id());
        defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,pos,date,t.id(),false,"hourly"));
        var c=new ProfileWrite(0L,company,employee,date,t.id(),t.rule(),cpf(),null,new CdacProfile("not_applicable","explicit"),t.version(),legacy.id(),legacy.version(),"hourly");
        assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),c)).hasMessage("WORKFORCE_CONFLICT");
    }

}
