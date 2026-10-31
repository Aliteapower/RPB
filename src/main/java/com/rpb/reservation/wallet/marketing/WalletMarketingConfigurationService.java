package com.rpb.reservation.wallet.marketing;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;
import static com.rpb.reservation.wallet.marketing.MarketingRepository.*;

@Service
@Transactional
public class WalletMarketingConfigurationService {
 private final MarketingRepository r;private final WalletAccountRepository accounts;private final WalletAccess access;private final WalletCommandRepository commands;
 public WalletMarketingConfigurationService(MarketingRepository r,WalletAccountRepository accounts,WalletAccess access,WalletCommandRepository commands){this.r=r;this.accounts=accounts;this.access=access;this.commands=commands;}
 private WalletViews.Program context(StoreScope s,CurrentActor a,boolean write){
  String permission=write?"wallet.marketing.manage":a!=null&&a.hasPermission("wallet.marketing.view")?"wallet.marketing.view":"wallet.topup.create";
  access.staff(s,a,permission,true);var p=accounts.requireProgram(s,true);
  if(write)for(UUID store:accounts.programStores(s.tenantId().value(),p.id()))access.staff(new StoreScope(s.tenantId(),store),a,permission,true);
  r.lock(s.tenantId().value(),p.id());r.initialize(s.tenantId().value(),p.id(),a.actorId());return p;
 }
 public Settings settings(StoreScope s,CurrentActor a){var p=context(s,a,false);return view(s,p.id());}
 private Settings view(StoreScope s,UUID p){UUID t=s.tenantId().value();var m=r.settings(t,p);return new Settings(integer(m,"config_version"),(String)m.get("default_deduction_policy"),integer(m,"policy_version"),(Boolean)m.get("self_topup_enabled"),(Boolean)m.get("campaigns_enabled"),(Boolean)m.get("points_enabled"),accounts.programStores(t,p),r.offers(t,p,null,r.baseRevision(t,p)));}
 public Settings save(StoreScope s,CurrentActor a,SettingsUpdate c){
  var p=context(s,a,true);if(c==null||!Set.of("proportional","principal_first").contains(Objects.toString(c.deductionPolicy(),"")))throw WalletException.invalid();MarketingRepository.validateOffers(c.offers(),true);
  return commands.run(s,a.actorId(),"marketing.settings",c.idempotencyKey(),c,Settings.class,()->{UUID t=s.tenantId().value();var old=r.settings(t,p.id());if(integer(old,"config_version")!=c.expectedVersion())throw stale();
   r.createSet(t,p.id(),null,r.baseRevision(t,p.id())+1,c.offers(),a.actorId());
   r.jdbc().update("update wallet_marketing_settings set default_deduction_policy=?,policy_version=policy_version+case when default_deduction_policy<>? then 1 else 0 end,config_version=config_version+1,self_topup_enabled=?,campaigns_enabled=?,points_enabled=?,updated_at=now() where tenant_id=? and program_id=?",c.deductionPolicy(),c.deductionPolicy(),c.selfTopupEnabled(),c.campaignsEnabled(),c.pointsEnabled(),t,p.id());
   Settings result=view(s,p.id());commands.audit(s,a.actorId(),"marketing.settings",p.id(),result);return result;});
 }
 public List<Campaign> campaigns(StoreScope s,CurrentActor a,String status,int limit,String cursor){
  var p=context(s,a,false);access.staff(s,a,"wallet.marketing.view",true);if(limit<1||limit>100||!Set.of("all","draft","published","disabled","archived").contains(status))throw WalletException.invalid();
  Instant at=Instant.MAX;UUID id=new UUID(-1L,-1L);if(cursor!=null&&!cursor.isBlank()){try{String[] bits=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",2);at=Instant.parse(bits[0]);id=UUID.fromString(bits[1]);}catch(Exception e){throw WalletException.invalid();}}
  // Timestamp.MAX is deliberately avoided: PostgreSQL accepts a stable upper application bound.
  if(at==Instant.MAX)at=Instant.parse("9999-12-31T23:59:59Z");
  var ids=r.jdbc().query("select id from wallet_campaigns where tenant_id=? and program_id=? and (?='all' or status=?) and (created_at,id)<(?,?) order by created_at desc,id desc limit ?",(rs,n)->rs.getObject(1,UUID.class),s.tenantId().value(),p.id(),status,status,Timestamp.from(at),id,limit);
  return ids.stream().map(x->r.campaign(s.tenantId().value(),p.id(),x)).toList();
 }
 public Campaign campaign(StoreScope s,CurrentActor a,UUID id){var p=context(s,a,false);access.staff(s,a,"wallet.marketing.view",true);return r.campaign(s.tenantId().value(),p.id(),id);}
 public Campaign create(StoreScope s,CurrentActor a,CampaignDraft c){var p=context(s,a,true);validate(c);return commands.run(s,a.actorId(),"marketing.create",c.idempotencyKey(),c,Campaign.class,()->{UUID id=UUID.randomUUID();r.jdbc().update("insert into wallet_campaigns(id,tenant_id,program_id,name) values(?,?,?,?)",id,s.tenantId().value(),p.id(),c.name().trim());revision(s,p.id(),id,1,a,c);commands.audit(s,a.actorId(),"marketing.create",id,c);return r.campaign(s.tenantId().value(),p.id(),id);});}
 public Campaign draft(StoreScope s,CurrentActor a,UUID id,CampaignDraft c){var p=context(s,a,true);validate(c);return commands.run(s,a.actorId(),"marketing.draft",c.idempotencyKey(),Map.of("id",id,"draft",c),Campaign.class,()->{var old=r.campaign(s.tenantId().value(),p.id(),id);if(old.version()!=c.expectedVersion()||"archived".equals(old.status()))throw stale();int rev=old.revisions().getFirst().revision()+1;revision(s,p.id(),id,rev,a,c);r.jdbc().update("update wallet_campaigns set name=?,version=version+1,updated_at=now() where tenant_id=? and program_id=? and id=?",c.name().trim(),s.tenantId().value(),p.id(),id);commands.audit(s,a.actorId(),"marketing.draft",id,c);return r.campaign(s.tenantId().value(),p.id(),id);});}
 public Campaign publish(StoreScope s,CurrentActor a,UUID id,Publish c){var p=context(s,a,true);if(c==null)throw WalletException.invalid();return commands.run(s,a.actorId(),"marketing.publish",c.idempotencyKey(),Map.of("id",id,"publish",c),Campaign.class,()->{UUID t=s.tenantId().value();var old=r.campaign(t,p.id(),id);if(old.version()!=c.expectedVersion()||"archived".equals(old.status())||old.revisions().getFirst().revision()!=c.draftVersion()||Objects.equals(old.currentPublishedRevision(),c.draftVersion()))throw stale();var rev=old.revisions().getFirst();if(!rev.endAt().isAfter(Instant.now()))throw stale();
  int n=r.jdbc().queryForObject("select count(*) from wallet_campaigns c join wallet_campaign_revisions r on r.campaign_id=c.id and r.tenant_id=c.tenant_id and r.program_id=c.program_id and r.revision=c.current_published_revision where c.tenant_id=? and c.program_id=? and c.id<>? and c.status='published' and r.priority=? and r.start_at<? and r.end_at>?",Integer.class,t,p.id(),id,rev.priority(),Timestamp.from(rev.endAt()),Timestamp.from(rev.startAt()));if(n>0)throw stale();
  r.jdbc().update("update wallet_campaigns set status='published',current_published_revision=?,version=version+1,updated_at=now() where tenant_id=? and program_id=? and id=?",c.draftVersion(),t,p.id(),id);commands.audit(s,a.actorId(),"marketing.publish",id,c);return r.campaign(t,p.id(),id);});}
 public Campaign lifecycle(StoreScope s,CurrentActor a,UUID id,Lifecycle c,boolean archive){var p=context(s,a,true);if(c==null||c.reason()==null||c.reason().isBlank()||c.reason().length()>500)throw WalletException.invalid();String action=archive?"archive":"disable";return commands.run(s,a.actorId(),"marketing."+action,c.idempotencyKey(),Map.of("id",id,"command",c),Campaign.class,()->{UUID t=s.tenantId().value();var old=r.campaign(t,p.id(),id);if(old.version()!=c.expectedVersion()||"archived".equals(old.status()))throw stale();r.jdbc().update("update wallet_campaigns set status=?,version=version+1,updated_at=now() where tenant_id=? and program_id=? and id=?",archive?"archived":"disabled",t,p.id(),id);commands.audit(s,a.actorId(),"marketing."+action,id,c);return r.campaign(t,p.id(),id);});}
 private void revision(StoreScope s,UUID p,UUID id,int rev,CurrentActor a,CampaignDraft c){r.jdbc().update("insert into wallet_campaign_revisions(tenant_id,program_id,campaign_id,revision,start_at,end_at,priority,terms_snapshot,created_by) values(?,?,?,?,?,?,?,?,?)",s.tenantId().value(),p,id,rev,Timestamp.from(c.startAt()),Timestamp.from(c.endAt()),c.priority(),Objects.toString(c.terms(),""),a.actorId());r.createSet(s.tenantId().value(),p,id,rev,c.offers(),a.actorId());}
 private void validate(CampaignDraft c){if(c==null||c.name()==null||c.name().isBlank()||c.name().length()>120||c.startAt()==null||c.endAt()==null||!c.startAt().isBefore(c.endAt())||Objects.toString(c.terms(),"").length()>5000)throw WalletException.invalid();MarketingRepository.validateOffers(c.offers(),false);}
}
