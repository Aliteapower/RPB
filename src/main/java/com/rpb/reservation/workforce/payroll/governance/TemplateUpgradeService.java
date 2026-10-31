package com.rpb.reservation.workforce.payroll.governance;

import static com.rpb.reservation.workforce.payroll.governance.TemplateUpgradeDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class TemplateUpgradeService {
 private final TemplatePublicationScope scope;
 private final TemplateUpgradeSupport support;
 private final PayChangeService changes;
 public TemplateUpgradeService(WorkforceAccess access,TemplatePublicationRepository publications,PayChangeRepository repository,PayChangeService changes){scope=new TemplatePublicationScope(access,publications);support=new TemplateUpgradeSupport(publications,repository);this.changes=changes;}
 public Preview preview(CurrentActor actor,UUID store,UUID master,UUID adoption,long releaseVersion,LocalDate date){var shop=scope.managed(actor,store);return support.preview(actor.tenantId(),store,shop.companyId(),master,adoption,releaseVersion,date);}
 @Transactional public PayChangeDtos.Request save(CurrentActor actor,UUID store,UUID id,UpgradeWrite c){
  var shop=scope.managed(actor,store);if(c==null||c.companyId()==null||c.effectiveDate()==null)throw WorkforceException.invalid();if(!c.companyId().equals(shop.companyId()))throw WorkforceException.forbidden();
  return changes.save(actor,store,id,new PayChangeDtos.Write(c.expectedRevision(),c.companyId(),null,c.effectiveDate(),c.adoptionId(),null,List.of(),c.derivedRule(),c.reason(),new Selection(c.targetReleaseVersion(),c.expectedLatestStoreVersion(),c.previewHash(),c.resolutions())));
 }
}
