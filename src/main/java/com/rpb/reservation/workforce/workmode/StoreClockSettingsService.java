package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import static com.rpb.reservation.workforce.workmode.StoreClockSettings.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.Clock;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
/** Store master address and site configuration have one atomic boundary; calibration has no write path. */
@Service @Transactional(readOnly=true)
public class StoreClockSettingsService {
 private final WorkforceAccess access;private final StoreClockSettingsRepository stores;private final WorkModeRepository sites;private final WorkModeService modes;private final WorkforceAudit audit;private final Clock clock;
 public StoreClockSettingsService(WorkforceAccess access,StoreClockSettingsRepository stores,WorkModeRepository sites,WorkModeService modes,WorkforceAudit audit,Clock clock){this.access=access;this.stores=stores;this.sites=sites;this.modes=modes;this.audit=audit;this.clock=clock;}
 private void manage(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);}
 private boolean addressPermission(CurrentActor actor){return actor.hasPermission("tenant.admin.manage");}
 public Settings get(CurrentActor actor,UUID store){manage(actor,store);return view(actor,stores.identity(actor.tenantId(),store,false),sites.site(actor.tenantId(),store));}
 private Settings view(CurrentActor actor,StoreClockSettingsRepository.Identity identity,Site site){return new Settings(identity.storeId(),identity.storeCode(),identity.storeName(),identity.entityId(),identity.entityName(),identity.address(),identity.version(),site.point(),site.version(),addressPermission(actor));}
 @Transactional public Settings save(CurrentActor actor,UUID store,Write c){
  access.sourceWrite(actor);manage(actor,store);
  if(c==null||c.expectedStoreVersion()==null||c.expectedStoreVersion()<0||c.expectedVersion()==null||c.expectedVersion()<0)throw WorkforceException.invalid();
  var point=WorkModeRules.point(c.point());String address=c.address()==null?"":c.address().trim();
  sites.siteLock(actor.tenantId(),store);var before=stores.identity(actor.tenantId(),store,true);var old=sites.site(actor.tenantId(),store);
  boolean changed=!address.equals(before.address());if(changed&&!addressPermission(actor))throw WorkforceException.forbidden();if(changed&&address.length()>500)throw WorkforceException.invalid();
  boolean storeVersion=before.version()==c.expectedStoreVersion(),siteVersion=old.version()==c.expectedVersion();
  if(!changed&&point.equals(old.point())&&(storeVersion||before.version()==c.expectedStoreVersion()+1)&&(siteVersion||old.version()==c.expectedVersion()+1))return view(actor,before,old);
  if(!storeVersion||!siteVersion)throw WorkforceException.conflict();
  if(changed){stores.address(actor.tenantId(),before,address);audit.changed(actor,store,"clock-site.address",store,Map.of("address",before.address()),Map.of("address",address));}
  var site=modes.saveSite(actor,store,new SiteWrite(old.version(),point));return view(actor,stores.identity(actor.tenantId(),store,false),site);
 }
 public CheckResult check(CurrentActor actor,UUID store,CheckWrite c){
  manage(actor,store);stores.identity(actor.tenantId(),store,false);if(c==null||(c.expectedVersion()!=null&&c.expectedVersion()<0))throw WorkforceException.invalid();
  var site=sites.site(actor.tenantId(),store);if(c.expectedVersion()!=null&&site.version()!=c.expectedVersion())throw WorkforceException.conflict();
  var point=c.point()==null?site.point():c.point();if(point==null)throw new WorkforceException("WORKFORCE_CLOCK_SITE_REQUIRED",409);point=WorkModeRules.point(point);
  WorkModeRules.location(c.location());var now=clock.instant();Double distance=null,accuracy=c.location()==null?null:c.location().accuracyMeters();
  if(c.location()!=null&&c.location().latitude()!=null&&c.location().longitude()!=null)distance=WorkModeRules.distance(point.latitude(),point.longitude(),c.location().latitude(),c.location().longitude());
  try{WorkModeRules.locate(point,c.location(),now);return new CheckResult(distance,point.radiusMeters(),accuracy,true,null,site.version(),now);}
  catch(WorkforceException e){if(!Set.of("WORKFORCE_LOCATION_REQUIRED","WORKFORCE_LOCATION_STALE","WORKFORCE_LOCATION_ACCURACY","WORKFORCE_LOCATION_OUTSIDE").contains(e.code()))throw e;return new CheckResult(distance,point.radiusMeters(),accuracy,false,e.code(),site.version(),now);}
 }
}
