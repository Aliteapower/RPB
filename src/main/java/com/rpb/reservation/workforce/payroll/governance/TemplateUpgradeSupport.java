package com.rpb.reservation.workforce.payroll.governance;

import static com.rpb.reservation.workforce.payroll.governance.TemplateUpgradeDtos.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.Rule;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.LocalDate;
import java.util.*;

/** Shared draft/approval validation, called only after the owning service's permission checks. */
final class TemplateUpgradeSupport {
 private final TemplatePublicationRepository publications;
 private final PayChangeRepository changes;
 TemplateUpgradeSupport(TemplatePublicationRepository publications,PayChangeRepository changes){this.publications=publications;this.changes=changes;}
 Preview preview(UUID tenant,UUID store,UUID company,UUID master,UUID adoptionId,long version,LocalDate date){
  if(date==null||version<1)throw WorkforceException.invalid();
  var shop=publications.store(tenant,store);if(!company.equals(shop.companyId())||!shop.active()||!publications.companyActive(tenant,company))throw WorkforceException.forbidden();
  var base=publications.adoption(tenant,store,company,adoptionId);if(!base.masterId().equals(master))throw WorkforceException.forbidden();
  var current=publications.currentAt(tenant,store,master,date).orElseThrow(WorkforceException::missing);if(!current.id().equals(base.id()))throw WorkforceException.conflict();
  var old=publications.release(tenant,master,base.releaseVersion()).orElseThrow(WorkforceException::missing);
  var target=publications.release(tenant,master,version).orElseThrow(WorkforceException::missing);
  if(!TemplatePublicationScope.visible(target,shop)||!TemplatePublicationScope.visible(old,shop))throw WorkforceException.forbidden();
  if(version<=base.releaseVersion())throw WorkforceException.invalid();
  long latest=publications.latest(tenant,store,master).orElseThrow().storeVersion();
  var differences=TemplateUpgradeDiff.compare(old.rule(),base.rule(),target.rule());
  boolean local=!old.rule().equals(base.rule());
  // Human labels and translations are presentation, never a source-concurrency baseline.
  var hash=changes.hash(List.of(base,old,target,latest,date));
  return new Preview(base,old,target,latest,date,differences,local,hash);
 }
 Context context(UUID tenant,UUID store,UUID company,TemplatePublicationDtos.Adoption base,LocalDate date,Selection selection,Rule result){
  if(selection.targetReleaseVersion()==null||selection.expectedLatestStoreVersion()==null||selection.previewHash()==null||selection.resolutions()==null)throw WorkforceException.invalid();
  var preview=preview(tenant,store,company,base.masterId(),base.id(),selection.targetReleaseVersion(),date);
  if(preview.expectedLatestStoreVersion()!=selection.expectedLatestStoreVersion()||!preview.previewHash().equals(selection.previewHash()))throw WorkforceException.conflict();
  var resolutions=new TreeMap<String,Resolution>();for(var choice:selection.resolutions())if(choice==null||choice.path()==null||!Set.of("local","target","custom").contains(choice.choice()==null?"":choice.choice())||resolutions.putIfAbsent(choice.path(),choice)!=null)throw WorkforceException.invalid();
  for(var difference:preview.differences())if(difference.conflict()){
   var choice=resolutions.remove(difference.path());if(choice==null)throw new WorkforceException("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED",409);
   String actual=TemplateUpgradeDiff.value(result,difference.path());if(choice.choice().equals("local")&&!Objects.equals(actual,difference.local())||choice.choice().equals("target")&&!Objects.equals(actual,difference.target()))throw new WorkforceException("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED",409);
  }
  if(!resolutions.isEmpty())throw WorkforceException.invalid();
  return new Context(preview.oldRelease(),preview.targetRelease(),preview.expectedLatestStoreVersion(),date,preview.previewHash(),selection.resolutions().stream().sorted(Comparator.comparing(Resolution::path)).toList());
 }
 String recheck(UUID tenant,TemplatePublicationDtos.Adoption base,Context context){
  var preview=preview(tenant,base.storeId(),base.companyId(),base.masterId(),base.id(),context.targetRelease().version(),context.effectiveDate());
  if(!preview.oldRelease().equals(context.oldRelease())||!preview.targetRelease().equals(context.targetRelease()))throw WorkforceException.conflict();
  return preview.previewHash();
 }
}
