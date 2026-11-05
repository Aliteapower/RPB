package com.rpb.reservation.workforce;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
class WorkforcePayEffectiveSourcesPostgresTest extends WorkforcePayChangePostgresTest {
 WorkforceJson json(){return new WorkforceJson(new ObjectMapper().findAndRegisterModules());}
 PayrollSources source(){var json=json();return new PayrollSources(new PayrollRepository(jdbc,json),configs,repository,new com.rpb.reservation.workforce.scheduling.SchedulingRepository(jdbc,json),new com.rpb.reservation.workforce.approval.ApprovalRepository(jdbc,json),new com.rpb.reservation.workforce.approval.ApprovalSources(new com.rpb.reservation.workforce.attendance.AttendanceRepository(jdbc,json),new com.rpb.reservation.workforce.leave.LeaveRepository(jdbc),new com.rpb.reservation.workforce.calendar.CalendarRepository(jdbc),json,clock),json,new com.rpb.reservation.workforce.workmode.WorkModeResolver(new com.rpb.reservation.workforce.workmode.WorkModeRepository(jdbc,json)));}
 @Test void approvedStableMonthWinsOverObsoleteRawInputs(){
  approve(submit(changes.save(admin,store,UUID.randomUUID(),personal(0,"3000"))));
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,null,null,null,List.of(new MonthWrite("2026-11",input(0,"90"))),null,"monthly"))));
  var raw=new LineInput(employee,8,"26","26",true,false,List.of(new Item("old","ow_allowance","999.00")),"old");
  assertThat(source().capture(tenant,company,YearMonth.of(2026,11),List.of(raw)).lines().getFirst().input().items().getFirst().amount()).isEqualTo("90.00");
  assertThat(source().capture(tenant,company,YearMonth.of(2026,11),List.of()).lines().getFirst().input().items().getFirst().amount()).isEqualTo("90.00");
 }
 @Test void unprovedProfileInsertedAfterInstallationCannotBecomeLegacy(){
  var command=personal(0,"3000");var p=command.profile();var id=UUID.randomUUID();var profile=new PayProfile(id,company,employee,"Alice",p.effectiveFrom(),p.templateId(),1,p.rule(),p.cpf(),1);
  jdbc.update("insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,version,snapshot) values(?,?,?,?,?,?,1,1,?::jsonb)",id,tenant,company,employee,p.effectiveFrom(),p.templateId(),json().write(profile));
  assertThat(source().capture(tenant,company,YearMonth.of(2026,11),List.of()).lines().getFirst().issues()).contains("WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED");
 }
 @Test void monthlyImpactAssumptionOverridesAlreadyApprovedMonthWithoutPersistence(){
  var initial=personal(0,"3000");var p=initial.profile();var on=LocalDate.of(2026,10,1);
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,on,null,new ProfileWrite(0L,company,employee,on,p.templateId(),p.rule(),p.cpf()),List.of(),null,"initial contract"))));
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,null,null,null,List.of(new MonthWrite("2026-10",input(0,"90"))),null,"approved month"))));
  var proposal=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,null,null,null,List.of(new MonthWrite("2026-10",input(1,"100"))),null,"proposed month"));
  var policy=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYear(2026);new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json()).save(tenant,new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(UUID.randomUUID(),policy.name(),2026,"SGD",policy.effectiveFrom(),policy.effectiveTo(),true,1,policy.config()));
  var pubs=new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository(jdbc,json(),clock);var impact=new com.rpb.reservation.workforce.payroll.governance.PayChangeImpactService(changes,requestRepository,new com.rpb.reservation.workforce.payroll.governance.PayChangeDrafts(requestRepository,configs,pubs,repository,clock),source(),new PayrollCalculationService(clock),new PayrollAccess(access,directory,jdbc),new PayrollRepository(jdbc,json()),clock);
  var result=impact.preview(admin,store,proposal.id(),"2026-10");assertThat(result.beforeNet()).isEqualTo("3090.00");assertThat(result.afterNet()).isEqualTo("3100.00");assertThat(result.netDifference()).isEqualTo("10.00");assertThat(requestRepository.approvedMonthly(tenant,company,employee,YearMonth.of(2026,10)).orElseThrow().input().items().getFirst().amount()).isEqualTo("90.00");
  assertThat(jdbc.queryForObject("select count(*) from workforce_pay_batches where tenant_id=?",Integer.class,tenant)).isZero();
 }
 @Test void oldApprovedProofCannotRollBackCurrentRepositoryProfile(){
  approve(submit(changes.save(admin,store,UUID.randomUUID(),personal(0,"3000"))));var old=configs.profiles(tenant,company).getFirst();
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,old.effectiveFrom(),null,new ProfileWrite(old.version(),company,employee,old.effectiveFrom(),old.templateId(),rule("4000"),old.cpf()),List.of(),null,"raise"))));
  assertThatThrownBy(()->configs.saveProfile(tenant,old)).hasMessage("WORKFORCE_CONFLICT");assertThat(configs.findProfile(tenant,old.id()).orElseThrow().rule().rate()).isEqualTo("4000.00");
 }
 @Test void repositoryCannotWriteReviewNotesToFrozenBatch(){
  UUID batch=UUID.randomUUID();var pay=new PayrollRepository(jdbc,json());pay.create(tenant,company,batch,"Company",YearMonth.of(2026,10),adminId,clock.instant());var state=pay.find(tenant,batch).orElseThrow();
  jdbc.update("update workforce_pay_batches set status='confirmed',calculation='{}'::jsonb,confirmed_by=?,confirmed_at=now() where id=?",adminId,batch);
  assertThatThrownBy(()->pay.input(state,new LineInput(employee,1,null,null,false,false,List.of(),"late note"))).hasMessage("WORKFORCE_CONFLICT");
 }
 @Test void aProofMustMatchImmutableApprovedResultRatherThanOnlyItsRequestHash(){
  var request=approve(submit(changes.save(admin,store,UUID.randomUUID(),personal(0,"3000"))));var original=request.content().profile();
  var fake=new PayProfile(UUID.randomUUID(),company,employee,original.employeeName(),LocalDate.of(2026,12,1),original.templateId(),original.templateVersion(),rule("9000"),original.cpf(),1);
  requestRepository.proof(tenant,company,new Proof(request.id(),request.revision(),request.hash(),"profile",fake.id(),null,1,adminId,clock.instant()),fake);
  assertThatThrownBy(()->configs.saveProfile(tenant,fake)).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void exactLegacyEvidenceAcceptsMissingNullableOldJsonWithoutRewritingIt(){
  var command=personal(0,"3000");var p=command.profile();var id=UUID.randomUUID();var profile=new PayProfile(id,company,employee,"Alice",p.effectiveFrom(),p.templateId(),1,p.rule(),p.cpf(),1);
  String old=json().write(profile);var node=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().findAndRegisterModules().valueToTree(profile);((com.fasterxml.jackson.databind.node.ObjectNode)node.get("cpf")).remove(List.of("birthDate","prDate","prDocumentId"));node.remove(List.of("origin","payment","cdac"));old=node.toString();
  jdbc.update("insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,version,snapshot) values(?,?,?,?,?,?,1,1,?::jsonb)",id,tenant,company,employee,p.effectiveFrom(),p.templateId(),old);
  jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) values(?,?,?,'legacy-valid','profile',?,1,?::jsonb)",UUID.randomUUID(),tenant,company,id,old);
  var before=jdbc.queryForObject("select snapshot::text from workforce_pay_profiles where id=?",String.class,id);var captured=source().capture(tenant,company,YearMonth.of(2026,11),List.of());assertThat(captured.lines().getFirst().profile()).isNotNull();assertThat(captured.lines().getFirst().issues()).doesNotContain("WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED");assertThat(jdbc.queryForObject("select snapshot::text from workforce_pay_profiles where id=?",String.class,id)).isEqualTo(before);
 }
 @Test void economicUnchangedAdoptionFixesSourceVersionEvenAfterSourceChanges(){
  var command=personal(0,"3000");var p=command.profile();var first=payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),p.templateId(),p.rule(),p.cpf()));
  var copy=payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,11,1),p.templateId(),p.rule(),p.cpf()));
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,first.effectiveFrom(),null,new ProfileWrite(first.version(),company,employee,first.effectiveFrom(),first.templateId(),rule("4000"),first.cpf()),List.of(),null,"raise original"))));
  var trace=configs.effective().evidence(tenant,company,"profile",copy.id(),null,copy.version(),copy);assertThat(trace.kind()).isEqualTo("economic-unchanged");assertThat(trace.sourceId()).isEqualTo(first.id());assertThat(trace.sourceVersion()).isEqualTo(1);assertThat(trace.requestId()).isNull();assertThat(jdbc.queryForObject("select source_snapshot->'rule'->>'rate' from workforce_pay_effective_evidence where id=?",String.class,trace.evidenceId())).isEqualTo("3000.00");
 }
 @Test void explicitFutureDerivedRuleCannotApplyEarlyButFutureDatedProfileCanUseIt(){
  var adopted=enable(release(draft(false)),store,0);var derived=approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,LocalDate.of(2026,12,1),adopted.id(),null,List.of(),rule("4000"),"future contract"))));var proof=requestRepository.proofs(tenant,derived.id()).getFirst();var t=configs.findTemplate(tenant,proof.subjectId()).orElseThrow();var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","");
  assertThatThrownBy(()->payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,11,1),t.id(),t.rule(),cpf))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
  assertThat(payroll.saveProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,12,1),t.id(),t.rule(),cpf)).rule().rate()).isEqualTo("4000.00");
 }
 @Test void realApprovalAllowsSpecialCpfElectionAndCdacExemptionExactResults(){
  var initial=personal(0,"3000");var p=initial.profile();long version=0;for(var mode:List.of("FG","FF")){var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),mode,"Explicit written election");var approved=approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,initial.effectiveDate(),null,new ProfileWrite(version,company,employee,initial.effectiveDate(),p.templateId(),p.rule(),cpf,null,new CdacProfile("exempt","Written opt-out")),List.of(),null,"Special choices explicitly reviewed"))));assertThat(approved.status()).isEqualTo("approved");var saved=configs.profiles(tenant,company).getFirst();version=saved.version();assertThat(saved.cpf().prMode()).isEqualTo(mode);assertThat(saved.cdac().status()).isEqualTo("exempt");assertThat(configs.effective().evidence(tenant,company,"profile",saved.id(),null,saved.version(),saved).kind()).isEqualTo("approved");}
 }

 PayProfile installedIdentity(String citizenship){return installedIdentity(citizenship,null);}
 PayProfile installedIdentity(String citizenship,Rule personal){var initial=personal(0,"3000");var t=configs.findTemplate(tenant,initial.profile().templateId()).orElseThrow();var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile(citizenship,citizenship.equals("foreign")?null:LocalDate.of(1990,1,1),null,"GG","");var p=new PayProfile(UUID.randomUUID(),company,employee,"Alice",LocalDate.of(2026,10,1),t.id(),t.version(),personal==null?t.rule():PayrollRules.normalize(personal),cpf,1);jdbc.update("insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,version,snapshot) values(?,?,?,?,?,?,?,1,?::jsonb)",p.id(),tenant,company,employee,p.effectiveFrom(),t.id(),t.version(),json().write(p));jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) values(?,?,?,'legacy-valid','profile',?,1,?::jsonb)",UUID.randomUUID(),tenant,company,p.id(),json().write(p));return p;}
 @Test void foreignLegacyNullCannotGrandfatherNewResidentUnknownCdacEvenWhenTenantApproves(){
  var before=installedIdentity("foreign");var date=LocalDate.of(2026,11,1);var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2026,10,1),"GG","");var r=submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,date,null,new ProfileWrite(0L,company,employee,date,before.templateId(),before.rule(),cpf),List.of(),null,"New resident identity needs applicability facts")));
  assertThatThrownBy(()->approve(r)).hasMessage("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED");assertThat(requestRepository.find(tenant,r.id()).orElseThrow().status()).isEqualTo("submitted");assertThat(configs.profiles(tenant,company)).containsExactly(before);assertThat(requestRepository.proofs(tenant,r.id())).isEmpty();
 }
 @Test void installedResidentNullKeepsItsExistingMeaningForNonEconomicMaintenance(){
  var before=installedIdentity("citizen");var after=payroll.saveProfile(admin,store,before.id(),new ProfileWrite(1L,company,employee,before.effectiveFrom(),before.templateId(),before.rule(),before.cpf(),new PaymentPreference("cash","")));assertThat(after.cdac()).isNull();assertThat(after.payment().method()).isEqualTo("cash");assertThat(configs.effective().evidence(tenant,company,"profile",after.id(),null,after.version(),after).kind()).isEqualTo("economic-unchanged");assertThat(jdbc.queryForObject("select count(*) from workforce_pay_change_requests where tenant_id=?",Integer.class,tenant)).isZero();
 }
 @Test void undeclaredNewResidentImpactCannotEstimateCdacAsKnownZero(){
  var initial=personal(0,"3000");var p=initial.profile();var on=LocalDate.of(2026,10,1);var fixed=new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of()));var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");var r=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,on,null,new ProfileWrite(0L,company,employee,on,p.templateId(),fixed,cpf),List.of(),null,"Missing applicability is not known zero"));
  var policy=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYear(2026);new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json()).save(tenant,new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(UUID.randomUUID(),policy.name(),2026,"SGD",policy.effectiveFrom(),policy.effectiveTo(),true,1,policy.config()));var pubs=new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository(jdbc,json(),clock);var impact=new com.rpb.reservation.workforce.payroll.governance.PayChangeImpactService(changes,requestRepository,new com.rpb.reservation.workforce.payroll.governance.PayChangeDrafts(requestRepository,configs,pubs,repository,clock),source(),new PayrollCalculationService(clock),new PayrollAccess(access,directory,jdbc),new PayrollRepository(jdbc,json()),clock).preview(admin,store,r.id(),"2026-10");
  assertThat(impact.afterNet()).isNull();assertThat(impact.issues()).contains("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED");assertThat(configs.profiles(tenant,company)).isEmpty();
 }
 Impact fixedImpact(Request r){var policy=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYear(2026);new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json()).save(tenant,new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(UUID.randomUUID(),policy.name(),2026,"SGD",policy.effectiveFrom(),policy.effectiveTo(),true,1,policy.config()));var pubs=new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository(jdbc,json(),clock);return new com.rpb.reservation.workforce.payroll.governance.PayChangeImpactService(changes,requestRepository,new com.rpb.reservation.workforce.payroll.governance.PayChangeDrafts(requestRepository,configs,pubs,repository,clock),source(),new PayrollCalculationService(clock),new PayrollAccess(access,directory,jdbc),new PayrollRepository(jdbc,json()),clock).preview(admin,store,r.id(),"2026-10");}
 @Test void missingNewResidentDeclarationKeepsTrustedBeforeNetAndMakesOnlyAfterUnknown(){
  var fixed=new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of()));var before=installedIdentity("foreign",fixed);var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");var r=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,before.effectiveFrom(),null,new ProfileWrite(1L,company,employee,before.effectiveFrom(),before.templateId(),before.rule(),cpf),List.of(),null,"Resident applicability is missing"));var result=fixedImpact(r);
  assertThat(result.beforeNet()).isEqualTo("3000.00");assertThat(result.afterNet()).isNull();assertThat(result.netDifference()).isNull();assertThat(result.issues()).contains("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED");assertThat(configs.findProfile(tenant,before.id())).contains(before);
 }
 @Test void fullySpecifiedPendingSpecialCpfAndExemptionRemainComputableAssumptions(){
  var fixed=new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of()));var before=installedIdentity("foreign",fixed);var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2025,1,1),"FF","Written special election");var r=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,before.effectiveFrom(),null,new ProfileWrite(1L,company,employee,before.effectiveFrom(),before.templateId(),before.rule(),cpf,null,new CdacProfile("exempt","Written opt-out proposal")),List.of(),null,"Complete special choices awaiting approval"));var result=fixedImpact(r);
  assertThat(r.status()).isEqualTo("draft");assertThat(result.beforeNet()).isEqualTo("3000.00");assertThat(result.afterNet()).isEqualTo("2400.00");assertThat(result.netDifference()).isEqualTo("-600.00");assertThat(result.issues()).doesNotContain("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED");assertThat(configs.findProfile(tenant,before.id())).contains(before);
 }
}
