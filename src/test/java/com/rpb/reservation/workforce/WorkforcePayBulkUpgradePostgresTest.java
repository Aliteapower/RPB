package com.rpb.reservation.workforce;

import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.*;
import com.rpb.reservation.workforce.payroll.governance.*;
import java.util.*;
import java.time.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;

class WorkforcePayBulkUpgradePostgresTest extends WorkforcePayChangePostgresTest {
 TemplateUpgradeService upgrades(){return proxy(new TemplateUpgradeService(access,publicationRepository(),requestRepository,changes));}
 PayChangeImpactService templateImpact(){return proxy(new PayChangeImpactService(changes,requestRepository,new PayChangeDrafts(requestRepository,configs,publicationRepository(),repository,clock),mock(PayrollSources.class),mock(PayrollCalculationService.class),new PayrollAccess(access,directory,jdbc),mock(PayrollRepository.class),clock));}
 PayChangeBulkService bulk(){return new PayChangeBulkService(access,changes,tx);}
 TemplatePublicationDtos.Release nextRelease(UUID master,String rate){var d=publications.drafts(admin,store).stream().filter(x->x.id().equals(master)).findFirst().orElseThrow();return release(publications.save(admin,store,d.id(),new TemplatePublicationDtos.DraftWrite(d.version(),d.name(),rule(rate),d.companyIds(),d.storeIds(),false)));}
 Request candidate(){var base=enable(release(draft(false)),store,0);return submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,LocalDate.of(2026,10,28),base.id(),null,List.of(),rule("3500"),"candidate")));}
 PayChangeBulkDtos.Item item(Request r){var c=action(r,"approve");return new PayChangeBulkDtos.Item(r.id(),c.commandId(),c.expectedRevision(),c.expectedHash(),c.action(),c.note());}
 TemplateUpgradeDtos.UpgradeWrite upgradeWrite(TemplateUpgradeDtos.Preview p,long revision,String rate,List<TemplateUpgradeDtos.Resolution> resolutions){return new TemplateUpgradeDtos.UpgradeWrite(revision,company,p.baseAdoption().id(),p.targetRelease().version(),p.effectiveDate(),p.expectedLatestStoreVersion(),p.previewHash(),rule(rate),resolutions,"explicit upgrade");}
 @Test void localDerivedCandidateCannotBeDiscardedByOldEnable(){
  var base=enable(release(draft(false)),store,0);derive(base,"2026-10-28","3500");
  var current=publicationRepository().current(tenant,store,base.masterId()).orElseThrow();
  var d=publications.drafts(admin,store).getFirst();
  var target=release(publications.save(admin,store,d.id(),new TemplatePublicationDtos.DraftWrite(d.version(),d.name(),rule("4000"),d.companyIds(),d.storeIds(),false)));
  assertThatThrownBy(()->enable(target,store,current.storeVersion())).hasMessage("WORKFORCE_PAYROLL_TEMPLATE_UPGRADE_REQUIRED");
  assertThat(publicationRepository().current(tenant,store,base.masterId()).orElseThrow()).isEqualTo(current);
  assertThat(configs.profiles(tenant,company)).isEmpty();
 }
 @Test void upgradeRequiresExplicitConflictThenFixedTargetProofAndNoEmployeeMutation(){
  var base=enable(release(draft(false)),store,0);derive(base,"2026-10-28","3500");var current=publicationRepository().current(tenant,store,base.masterId()).orElseThrow();var target=nextRelease(base.masterId(),"4000");
  var p=upgrades().preview(admin,store,base.masterId(),current.id(),target.version(),LocalDate.of(2026,11,1));
  assertThat(p.hasLocalChanges()).isTrue();assertThat(p.differences()).anyMatch(d->d.path().equals("rate")&&d.conflict());
  assertThatThrownBy(()->upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"4000",List.of()))).hasMessage("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED");
  var choices=List.of(new TemplateUpgradeDtos.Resolution("rate","target"));
  assertThatThrownBy(()->upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"5000",choices))).hasMessage("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED");
  var r=upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"4000",choices));
  assertThat(upgrades().save(admin,store,r.id(),upgradeWrite(p,0,"4000",choices))).isEqualTo(r);
  assertThatThrownBy(()->changes.save(admin,store,r.id(),new Write(r.revision(),company,null,r.effectiveDate(),r.adoptionId(),null,List.of(),rule("4000"),"drop upgrade"))).hasMessage("WORKFORCE_CONFLICT");
  var submitted=submit(r);nextRelease(base.masterId(),"5000");var approved=approve(submitted);assertThat(approved.status()).isEqualTo("approved");assertThat(approved.effectivity()).isEqualTo("future");
  var future=publicationRepository().currentAt(tenant,store,base.masterId(),LocalDate.of(2026,11,1)).orElseThrow();assertThat(future.releaseVersion()).isEqualTo(target.version());assertThat(future.rule().rate()).isEqualTo("4000.00");
  var template=configs.findTemplate(tenant,future.materializedTemplateId()).orElseThrow();assertThat(configs.effective().evidence(tenant,company,"template",template.id(),null,1,template)).isNotNull();
  assertThat(publicationRepository().current(tenant,store,base.masterId()).orElseThrow()).isEqualTo(current);assertThat(configs.profiles(tenant,company)).isEmpty();
  assertThatThrownBy(()->enable(target,store,future.storeVersion())).hasMessage("WORKFORCE_CONFLICT");
 }
 @Test void upgradePreviewRechecksLatestAndScopeAndApprovalsPersistStale(){
  var base=enable(release(draft(false)),store,0);var target=nextRelease(base.masterId(),"4000");var p=upgrades().preview(admin,store,base.masterId(),base.id(),target.version(),LocalDate.of(2026,11,1));
  var r=submit(upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"4000",List.of())));
  enable(target,store,base.storeVersion());assertThat(approve(r).status()).isEqualTo("stale");
  assertThatThrownBy(()->upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"4000",List.of()))).hasMessage("WORKFORCE_CONFLICT");
  assertThatThrownBy(()->upgrades().preview(admin,sibling,base.masterId(),base.id(),target.version(),LocalDate.of(2026,11,1))).hasMessage("WORKFORCE_NOT_FOUND");
 }
 @Test void bulkTransactionsKeepSuccessCommitStaleAndRetryFailedOriginalCommand(){
  var first=candidate();var second=candidate();var stale=candidate();var target=nextRelease(stale.content().adoption().masterId(),"4000");enable(target,store,stale.content().adoption().storeVersion());
  var commands=List.of(item(first),item(second),item(stale));var count=new java.util.concurrent.atomic.AtomicInteger();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenAnswer(inv->{if(count.incrementAndGet()==2)throw new IllegalStateException("injected audit outage");return null;});
  var response=bulk().action(admin,store,new PayChangeBulkDtos.Command(company,"2026-11",commands));assertThat(response.results()).extracting(PayChangeBulkDtos.Result::outcome).containsExactly("success","failed","stale");
  assertThat(changes.detail(admin,store,first.id()).status()).isEqualTo("approved");assertThat(changes.detail(admin,store,second.id()).status()).isEqualTo("submitted");assertThat(requestRepository.proofs(tenant,second.id())).isEmpty();
  assertThat(jdbc.queryForObject("select count(*) from workforce_pay_change_commands where id=?",Integer.class,commands.get(1).commandId())).isZero();
  when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenAnswer(inv->null);
  var retry=bulk().action(admin,store,new PayChangeBulkDtos.Command(company,"2026-11",commands));assertThat(retry.results()).extracting(PayChangeBulkDtos.Result::outcome).containsExactly("success","success","stale");assertThat(requestRepository.proofs(tenant,first.id())).hasSize(1);assertThat(requestRepository.proofs(tenant,second.id())).hasSize(1);
 }
 @Test void bulkRejectsChangedHashesForeignStoreAndMissingRequestsPerItem(){
  var r=candidate();var original=item(r);var wrongHash=new PayChangeBulkDtos.Item(r.id(),UUID.randomUUID(),r.revision(),"changed","approve","reviewed");var missing=new PayChangeBulkDtos.Item(UUID.randomUUID(),UUID.randomUUID(),1L,"x","approve","reviewed");
  var results=bulk().action(admin,store,new PayChangeBulkDtos.Command(company,null,List.of(wrongHash,missing))).results();assertThat(results).extracting(PayChangeBulkDtos.Result::httpStatus).containsExactly(409,404);assertThat(changes.detail(admin,store,r.id()).status()).isEqualTo("submitted");
  assertThat(bulk().action(admin,sibling,new PayChangeBulkDtos.Command(company,null,List.of(original))).results().getFirst().httpStatus()).isEqualTo(403);
  assertThat(bulk().action(admin,store,new PayChangeBulkDtos.Command(otherCompany,null,List.of(original))).results().getFirst().httpStatus()).isEqualTo(403);
  assertThat(bulk().action(admin,store,new PayChangeBulkDtos.Command(company,"2026-09",List.of(original))).results().getFirst().httpStatus()).isEqualTo(409);
  UUID managerId=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,managerId,new WorkforceDtos.AdministratorWrite(0L,true,null));var manager=new com.rpb.reservation.walkin.api.CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  assertThat(bulk().action(manager,store,new PayChangeBulkDtos.Command(company,null,List.of(original))).results().getFirst().httpStatus()).isEqualTo(403);
  assertThat(upgrades().preview(manager,store,r.content().adoption().masterId(),r.adoptionId(),nextRelease(r.content().adoption().masterId(),"4500").version(),r.effectiveDate()).baseAdoption().id()).isEqualTo(r.adoptionId());
 }
 @Test void oldContentWithoutUpgradeKeepsHashExactReplayAndApproval(){
  var write=personal(0,"4000");var draft=changes.save(admin,store,UUID.randomUUID(),write);var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());String raw=json.write(draft.content());assertThat(raw).doesNotContain("\"upgrade\"");
  var decoded=json.read(raw,Content.class);assertThat(decoded.upgrade()).isNull();assertThat(requestRepository.hash(decoded)).isEqualTo(draft.hash());assertThat(changes.save(admin,store,draft.id(),write)).isEqualTo(draft);
  var submitted=submit(draft);String snapshot=jdbc.queryForObject("select content::text from workforce_pay_change_revisions where request_id=? and revision=?",String.class,draft.id(),draft.revision());assertThat(approve(submitted).status()).isEqualTo("approved");assertThat(jdbc.queryForObject("select content::text from workforce_pay_change_revisions where request_id=? and revision=?",String.class,draft.id(),draft.revision())).isEqualTo(snapshot);
 }
 @Test void upgradeRequiresExplicitDateRatherThanUndatedOverride(){
  var base=enable(release(draft(false)),store,0);var target=nextRelease(base.masterId(),"4000");var p=upgrades().preview(admin,store,base.masterId(),base.id(),target.version(),LocalDate.of(2026,10,28));var c=upgradeWrite(p,0,"4000",List.of());
  assertThatThrownBy(()->upgrades().save(admin,store,UUID.randomUUID(),new TemplateUpgradeDtos.UpgradeWrite(c.expectedRevision(),c.companyId(),c.adoptionId(),c.targetReleaseVersion(),null,c.expectedLatestStoreVersion(),c.previewHash(),c.derivedRule(),c.resolutions(),c.reason()))).hasMessage("WORKFORCE_INVALID");
  assertThatThrownBy(()->upgrades().save(admin,store,UUID.randomUUID(),new TemplateUpgradeDtos.UpgradeWrite(c.expectedRevision(),otherCompany,c.adoptionId(),c.targetReleaseVersion(),c.effectiveDate(),c.expectedLatestStoreVersion(),c.previewHash(),c.derivedRule(),c.resolutions(),c.reason()))).hasMessage("WORKFORCE_FORBIDDEN");
  assertThatThrownBy(()->changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,null,base.id(),null,List.of(),rule("4000"),"undated direct selection",new TemplateUpgradeDtos.Selection(c.targetReleaseVersion(),c.expectedLatestStoreVersion(),c.previewHash(),c.resolutions())))).hasMessage("WORKFORCE_INVALID");
 }
 @Test void monthFilterDistinguishesMonthlyChildrenFromLongTermRetrieval(){
  var p=approve(submit(changes.save(admin,store,UUID.randomUUID(),personal(0,"3000"))));
  var monthly=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,employee,null,null,null,List.of(new MonthWrite("2026-12",input(0,"50"))),null,"December only"));
  assertThat(PayChangeMonthFilter.matches(monthly,YearMonth.of(2026,11))).isFalse();assertThat(PayChangeMonthFilter.matches(monthly,YearMonth.of(2026,12))).isTrue();assertThat(PayChangeMonthFilter.matches(p,YearMonth.of(2026,10))).isFalse();assertThat(PayChangeMonthFilter.matches(p,YearMonth.of(2026,12))).isTrue();
 }
 @Test void concurrentBulkExactRetryCreatesOneProofAndOriginalFrozenJsonStaysIdentical()throws Exception{
  var r=candidate();var command=new PayChangeBulkDtos.Command(company,"2026-11",List.of(item(r)));
  UUID batch=UUID.randomUUID();jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,created_by,calculation,confirmed_by,confirmed_at) values(?,?,?,'Company','2026-09-01','confirmed',1,?,'{\"lines\":[],\"historical\":true}'::jsonb,?,now())",batch,tenant,company,adminId,adminId);String frozen=jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,batch);
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);try{var one=executor.submit(()->bulk().action(admin,store,command));var two=executor.submit(()->bulk().action(admin,store,command));assertThat(one.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(two.get(20,java.util.concurrent.TimeUnit.SECONDS));}finally{executor.shutdownNow();}
  assertThat(requestRepository.proofs(tenant,r.id())).hasSize(1);assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,batch)).isEqualTo(frozen);
 }
 @Test void approvedUpgradeImpactKeepsUnknownAmountsAndExplainsChangedBaseline(){
  var base=enable(release(draft(false)),store,0);var target=nextRelease(base.masterId(),"4000");var p=upgrades().preview(admin,store,base.masterId(),base.id(),target.version(),LocalDate.of(2026,10,28));var r=approve(submit(upgrades().save(admin,store,UUID.randomUUID(),upgradeWrite(p,0,"4000",List.of()))));
  var impact=templateImpact().preview(admin,store,r.id(),"2026-11");assertThat(impact.netDifference()).isNull();assertThat(impact.issues()).contains("SOURCE_BASELINE_CHANGED","TEMPLATE_CANDIDATE_ONLY");assertThat(impact.standardBefore()).isEqualTo("3000.00");assertThat(impact.standardAfter()).isEqualTo("4000.00");assertThat(impact.standardPercent()).isEqualTo("33.33");
 }
 @Test void zeroStandardBaseHasNoFabricatedPercentageOrEmployeeNet(){
  var d=publications.save(admin,store,UUID.randomUUID(),new TemplatePublicationDtos.DraftWrite(0L,"Zero",rule("0"),List.of(company),List.of(store),false));var base=enable(release(d),store,0);var r=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,LocalDate.of(2026,10,28),base.id(),null,List.of(),rule("100"),"candidate"));var impact=templateImpact().preview(admin,store,r.id(),"2026-11");assertThat(impact.standardDifference()).isEqualTo("100.00");assertThat(impact.standardPercent()).isNull();assertThat(impact.netDifference()).isNull();
 }
 @Test void newCdacDeductionIsExplicitRiskWithoutMadeUpThreshold(){
  var base=enable(release(draft(false)),store,0);var rule=new com.rpb.reservation.workforce.payroll.PayrollDtos.Rule("monthly","3000","8","44",false,"1.5",List.of(new com.rpb.reservation.workforce.payroll.PayrollDtos.Item("CDAC","cdac","2")));
  var r=changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,LocalDate.of(2026,10,28),base.id(),null,List.of(),rule,"candidate"));var impact=templateImpact().preview(admin,store,r.id(),"2026-11");assertThat(impact.warnings()).contains("NEW_DEDUCTION","PAY_REDUCTION");assertThat(impact.standardDifference()).isEqualTo("-2.00");assertThat(impact.netDifference()).isNull();
 }
 @Test void metadataOnlyLocalLabelsAreNotSilentlyDiscardedByEnable(){
  var initialRule=new com.rpb.reservation.workforce.payroll.PayrollDtos.Rule("monthly","3000","8","44",false,"1.5",List.of(new com.rpb.reservation.workforce.payroll.PayrollDtos.Item("Mother allowance","ow_allowance","20")));
  var d=publications.save(admin,store,UUID.randomUUID(),new TemplatePublicationDtos.DraftWrite(0L,"Local names",initialRule,List.of(company),List.of(store),false));var base=enable(release(d),store,0);
  var localRule=new com.rpb.reservation.workforce.payroll.PayrollDtos.Rule("monthly","3000","8","44",false,"1.5",List.of(new com.rpb.reservation.workforce.payroll.PayrollDtos.Item("Store allowance","ow_allowance","20")));
  approve(submit(changes.save(admin,store,UUID.randomUUID(),new Write(0L,company,null,LocalDate.of(2026,10,28),base.id(),null,List.of(),localRule,"local naming"))));var local=publicationRepository().current(tenant,store,base.masterId()).orElseThrow();var target=nextRelease(base.masterId(),"4000");
  assertThat(upgrades().preview(admin,store,base.masterId(),local.id(),target.version(),LocalDate.of(2026,10,28)).hasLocalChanges()).isTrue();assertThatThrownBy(()->enable(target,store,local.storeVersion())).hasMessage("WORKFORCE_PAYROLL_TEMPLATE_UPGRADE_REQUIRED");
 }
 @Test void previewHashBindsSourceSnapshotsAndDateNotPresentationLabels(){var base=enable(release(draft(false)),store,0);var target=nextRelease(base.masterId(),"4000");var p=upgrades().preview(admin,store,base.masterId(),base.id(),target.version(),LocalDate.of(2026,11,1));assertThat(p.previewHash()).isEqualTo(requestRepository.hash(List.of(p.baseAdoption(),p.oldRelease(),p.targetRelease(),p.expectedLatestStoreVersion(),p.effectiveDate())));}
}
