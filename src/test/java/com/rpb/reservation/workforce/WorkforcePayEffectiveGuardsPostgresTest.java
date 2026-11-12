package com.rpb.reservation.workforce;

import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Product writes must prove an exact effective version even when called below the controller. */
class WorkforcePayEffectiveGuardsPostgresTest extends WorkforcePayTemplatePublicationPostgresTest {
 UUID freshEmployee(){var id=UUID.randomUUID();var hire=LocalDate.of(2026,10,1);employees.save(admin,store,id,new com.rpb.reservation.workforce.application.WorkforceDtos.EmployeeWrite(0L,"S-"+id.toString().substring(0,8),"Standard employee","","","Service",hire,null,"active",company,hire,store,List.of(store),null,null,null));return id;}
 @Test void repositoryCannotCreateUnpublishedTemplate(){
  assertThatThrownBy(()->configs.saveTemplate(tenant,new SalaryTemplate(UUID.randomUUID(),company,"Bypass",rule("9000"),true,1)))
   .hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void repositoryCannotCreateUnprovedPersonalEconomicOverride(){
  UUID employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store),company,null,null));
  var adopted=enable(release(draft(false)),store,0);
  var p=new PayProfile(UUID.randomUUID(),company,employee,"Alice",LocalDate.of(2026,10,1),adopted.materializedTemplateId(),1,rule("9000"),new CpfProfile("foreign",null,null,"GG",""),1);
  assertThatThrownBy(()->configs.saveProfile(tenant,p)).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void repositoryCannotWriteUnapprovedMonthlyAmounts(){
  UUID employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store),company,null,null));
  var json=new com.rpb.reservation.workforce.application.WorkforceJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
  var pay=new PayrollRepository(jdbc,json);UUID batch=UUID.randomUUID();pay.create(tenant,company,batch,"Company",YearMonth.of(2026,10),adminId,clock.instant());
  var input=new LineInput(employee,1,"26","26",true,false,List.of(new Item("Bypass","ow_allowance","500")),"reviewed");
  assertThatThrownBy(()->pay.input(pay.find(tenant,batch).orElseThrow(),input)).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void publishedTemplateSwitchAndPositionMappingCannotBypassExistingEmployeeApproval(){
  UUID employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store),company,null,null));var cpf=new CpfProfile("foreign",null,null,"GG","");var a=enable(release(draft(false)),store,0);var first=configs.findTemplate(tenant,a.materializedTemplateId()).orElseThrow();var profile=payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),first.id(),first.rule(),cpf));
  var other=publications.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.DraftWrite(0L,"Different published",rule("4000"),List.of(company),List.of(store),false));var adopted=enable(release(other),store,0);var next=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();
  assertThatThrownBy(()->payroll.saveProfile(admin,store,profile.id(),new ProfileWrite(profile.version(),company,employee,profile.effectiveFrom(),next.id(),next.rule(),cpf))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
  var defaults=proxy(new PositionSalaryDefaultsService(access,new PayrollAccess(access,directory,jdbc),configs,repository,new com.rpb.reservation.workforce.positions.PositionRepository(jdbc),audit));var mapping=defaults.save(admin,store,UUID.randomUUID(),new DefaultWrite(0L,company,repository.find(tenant,employee).orElseThrow().positionId(),LocalDate.of(2026,10,1),next.id(),true));
  assertThatThrownBy(()->payroll.saveProfile(admin,store,profile.id(),new ProfileWrite(profile.version(),company,employee,profile.effectiveFrom(),next.id(),next.rule(),cpf,null,null,next.version(),mapping.id(),mapping.version()))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");assertThat(configs.findProfile(tenant,profile.id()).orElseThrow()).isEqualTo(profile);
 }
 @Test void firstExactAdoptionIgnoresItemIdentityButCannotAddUnapprovedEventSource(){
  UUID employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store),company,null,null));var item=new Item("Meal","ow_allowance","100",null,null,null);var rule=new Rule("monthly","3000","8","44",false,"1.5",List.of(item));var draft=publications.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.DraftWrite(0L,"With allowance",rule,List.of(company),List.of(store),false));var adopted=enable(release(draft),store,0);var template=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();var identity=new Item("Meal","ow_allowance","100",null,UUID.randomUUID(),null);var copied=new Rule("monthly","3000","8","44",false,"1.5",List.of(identity));var cpf=new CpfProfile("foreign",null,null,"GG","");var saved=payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),template.id(),copied,cpf));
  var event=new ItemSource("approved_work",UUID.randomUUID().toString(),1,null,null);var changed=new Rule("monthly","3000","8","44",false,"1.5",List.of(new Item("Meal","ow_allowance","100",null,identity.itemId(),event)));
  assertThatThrownBy(()->payroll.saveProfile(admin,store,saved.id(),new ProfileWrite(1L,company,employee,saved.effectiveFrom(),template.id(),changed,cpf))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void firstStandardCpfAndExplicitCdacApplicabilityDeclarationsNeedNoSecondApproval(){
  var adopted=enable(release(draft(false)),store,0);var t=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();
  for(var cpf:List.of(new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),new CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),"GG",""),new CpfProfile("foreign",null,null,"GG",""))){UUID employee=freshEmployee();var cdac=cpf.citizenship().equals("foreign")?null:new CdacProfile("not_applicable","Explicit standard eligibility declaration");assertThat(payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),cpf,null,cdac)).rule()).isEqualTo(t.rule());}
  UUID employee=freshEmployee();assertThat(payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),null,new CdacProfile("applicable","Explicit standard applicability")))).isNotNull();assertThat(jdbc.queryForObject("select count(*) from workforce_pay_change_requests where tenant_id=?",Integer.class,tenant)).isZero();
 }
 @Test void firstSpecialCpfElectionAndIndividualCdacExemptionOrManualNeedApproval(){
  var adopted=enable(release(draft(false)),store,0);var t=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();
  for(String mode:List.of("FG","FF")){UUID employee=freshEmployee();var cpf=new CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),mode,"Written election");assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),cpf,null,new CdacProfile("not_applicable","Declared")))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");}
  for(String mode:List.of("exempt","manual")){UUID employee=freshEmployee();assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),null,new CdacProfile(mode,"Written personal instruction")))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");}assertThat(configs.profiles(tenant,company)).isEmpty();
 }
 @Test void firstCitizenWithoutCdacDeclarationIsMissingInformationRatherThanAssumedZero(){
  var adopted=enable(release(draft(false)),store,0);var t=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();UUID employee=freshEmployee();assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","")))).hasMessage("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED");assertThat(configs.profiles(tenant,company)).isEmpty();
 }
 @Test void firstManualCdacAlreadyExplicitInPublishedRuleIsAnExactAdoption(){
  var rule=new Rule("monthly","3000","8","44",false,"1.5",List.of(new Item("Published CDAC","cdac","1.50")));var d=publications.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.DraftWrite(0L,"Explicit CDAC",rule,List.of(company),List.of(store),false));var adopted=enable(release(d),store,0);var t=configs.findTemplate(tenant,adopted.materializedTemplateId()).orElseThrow();UUID employee=freshEmployee();assertThat(payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),t.id(),t.rule(),new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),null,new CdacProfile("manual","Same published manual item"))).cdac().status()).isEqualTo("manual");
 }
}
