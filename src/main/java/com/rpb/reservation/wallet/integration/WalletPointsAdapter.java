package com.rpb.reservation.wallet.integration;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.wallet.marketing.WalletMarketingService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.loyalty.application.*;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** Wallet authorization and program binding are kept outside the independent points ledger. */
@Component
@Transactional(readOnly=true)
public class WalletPointsAdapter {
 private final WalletAccess access;private final WalletDirectory directory;private final PointsService points;private final JdbcTemplate jdbc;private final WalletMarketingService settings;
 public WalletPointsAdapter(WalletAccess access,WalletDirectory directory,PointsService points,JdbcTemplate jdbc,WalletMarketingService settings){this.access=access;this.directory=directory;this.points=points;this.jdbc=jdbc;this.settings=settings;}
 private Binding binding(StoreScope s,boolean active){
  var binding=jdbc.query("select p.*,s.enabled,b.loyalty_program_id from wallet_programs p join wallet_program_stores s on s.program_id=p.id and s.tenant_id=p.tenant_id left join wallet_loyalty_bindings b on b.wallet_program_id=p.id and b.tenant_id=p.tenant_id and b.operating_entity_id=p.operating_entity_id where p.tenant_id=? and s.store_id=?",(r,n)->{
   if(active&&(!r.getBoolean("enabled")||!"active".equals(r.getString("status"))))throw WalletException.conflict("门店钱包计划已停用");
   return new Binding(s.tenantId().value(),r.getObject("operating_entity_id",UUID.class),r.getObject("id",UUID.class),s.storeId().value(),r.getObject("loyalty_program_id",UUID.class));
  },s.tenantId().value(),s.storeId().value()).stream().findFirst().orElseThrow(WalletException::missing);
  if(active&&binding.programId()==null){
   jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","wallet-loyalty-binding:"+binding.tenantId()+":"+binding.walletProgramId());
   var existing=jdbc.query("select loyalty_program_id from wallet_loyalty_bindings where tenant_id=? and wallet_program_id=? and operating_entity_id=?",(rs,n)->rs.getObject(1,UUID.class),binding.tenantId(),binding.walletProgramId(),binding.issuerId());
   UUID program;
   if(existing.isEmpty()){
    program=points.createProgram(binding.tenantId(),binding.issuerId());
    jdbc.update("insert into wallet_loyalty_bindings(tenant_id,wallet_program_id,loyalty_program_id,operating_entity_id) values(?,?,?,?)",binding.tenantId(),binding.walletProgramId(),program,binding.issuerId());
   }else program=existing.getFirst();
   binding=new Binding(binding.tenantId(),binding.issuerId(),binding.walletProgramId(),binding.storeId(),program);
  }
  return binding;
 }
 public WalletViews.Context context(StoreScope s,CurrentActor a){
  access.staff(s,a,"wallet.points.view",false);var commercial=access.commercial(s);
  boolean enabled=commercial.allowed()&&settings.pointsEnabled(s);
  String denied=commercial.allowed()?(enabled?null:"WALLET_POINTS_DISABLED"):commercial.denyReason().name();
  return new WalletViews.Context(directory.store(s),null,enabled,denied,false);
 }
 public Account get(StoreScope s,CurrentActor a,UUID customer){access.staff(s,a,"wallet.points.view",false);directory.customer(s.tenantScope(),customer);return points.get(binding(s,false),customer);}
 public List<Entry> entries(StoreScope s,CurrentActor a,UUID customer,int offset){access.staff(s,a,"wallet.points.view",false);directory.customer(s.tenantScope(),customer);return points.entries(binding(s,false),customer,offset);}
 public Account own(StoreScope s,CustomerAuthPrincipal p){access.customer(s,p,false);return points.get(binding(s,false),p.customerId());}
 public List<Entry> ownEntries(StoreScope s,CustomerAuthPrincipal p,int offset){access.customer(s,p,false);return points.entries(binding(s,false),p.customerId(),offset);}
 @Transactional public Result mutate(StoreScope s,CurrentActor a,UUID customer,String kind,Mutation m){
  String permission=switch(kind){case "manual_grant"->"wallet.points.grant";case "manual_deduct"->"wallet.points.adjust";case "redemption"->"wallet.points.redeem";default->throw WalletException.invalid();};
  access.staff(s,a,permission,true);var profile=directory.customer(s.tenantScope(),customer);var b=binding(s,true);
  if(!settings.pointsEnabled(s))throw new WalletException("WALLET_POINTS_DISABLED",409,"手动积分尚未启用");
  if(points.get(b,customer).id()==null&&(profile.phone()==null||profile.phone().isBlank()))throw new WalletException("CUSTOMER_PHONE_REQUIRED",409,"请先在统一顾客管理补全手机号");
  return points.mutate(b,a.actorId(),customer,kind,m);
 }
 @Transactional public Result reverse(StoreScope s,CurrentActor a,UUID entryId,Reverse r){
  access.staff(s,a,"wallet.points.reverse",false);
  if(!a.roles().contains("tenant_admin")&&!a.roles().contains("store_manager"))throw WalletException.forbidden();
  var b=binding(s,false);UUID customer=points.customerForEntry(b,entryId);directory.customer(s.tenantScope(),customer);
  // Original correction may proceed after entitlement expiry and feature disablement.
  UUID original=points.originalEntryStore(b,entryId);
  if(!a.canAccessStore(original))throw WalletException.forbidden();
  return points.reverse(b,a.actorId(),entryId,r);
 }
 /** Called only after a persisted merchant intent is locked and its bound customer explicitly confirms. */
 @Transactional public Result redeemConfirmed(StoreScope s,CustomerAuthPrincipal p,UUID issuerActor,UUID requestId,int quantity,String description,Integer version){
  access.customer(s,p,true);var b=binding(s,true);
  if(!settings.pointsEnabled(s))throw new WalletException("WALLET_POINTS_DISABLED",409,"积分功能尚未启用");
  return points.mutate(b,issuerActor,p.customerId(),"redemption",new Mutation(quantity,"顾客扫码确认积分兑换",null,"qr:"+requestId,description,version,"qr:"+requestId));
 }
}
