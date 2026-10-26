package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.catalog.application.StorageProductCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.*;
import com.rpb.reservation.winestorage.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;
import static com.rpb.reservation.winestorage.persistence.StorageRepository.at;

@Service
@Transactional(readOnly=true)
public class StorageApplicationService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;
 private final StorageProductCatalog products;private final StoragePhotoService photos;private final Clock clock;private final StorageCustodianService custodians;private final StorageLocationService locations;
 public StorageApplicationService(StorageAccess access,StorageRepository r,StorageCommands commands,StorageProductCatalog products,StoragePhotoService photos,Clock clock,StorageCustodianService custodians,StorageLocationService locations){this.locations=locations;this.custodians=custodians;this.access=access;this.r=r;this.commands=commands;this.products=products;this.photos=photos;this.clock=clock;}

 @Transactional public Rule rule(StoreScope s,CurrentActor a,RuleWrite c){
  access.staff(s,a,"wine_storage.rules.manage",true);
  if(c==null||c.categoryId()==null||text(c.name()).isBlank()||c.name().length()>200||c.months()<1||c.months()>120)throw StorageException.invalid();
  return commands.run(s,a.actorId(),"staff","rule",c.idempotencyKey(),c,Rule.class,()->{
   if(products.categories(s).stream().noneMatch(x->x.id().equals(c.categoryId())&&x.enabled()))throw StorageException.missing();
   r.lock(s,"rule:"+c.categoryId());int version=r.rules(s).stream().filter(x->x.categoryId().equals(c.categoryId())).mapToInt(Rule::version).max().orElse(0)+1;
   Rule rule=new Rule(UUID.randomUUID(),c.categoryId(),c.name().trim(),c.months(),version);r.addRule(s,rule,a.actorId());return rule;
  });
 }

 @Transactional public Application apply(StoreScope s,CustomerAuthPrincipal p,Apply c){
  access.customer(s,p,true);if(c==null||!c.rulesAccepted()||c.lines()==null||c.lines().isEmpty()||c.lines().size()>50)throw StorageException.invalid();
  return commands.run(s,p.customerId(),"customer","apply",c.idempotencyKey(),c,Application.class,()->{
   r.requireCustomerMode(s);
   var lines=new ArrayList<Line>();Set<UUID> allPhotos=new HashSet<>();
   for(Line l:c.lines()){
    if(l==null)throw StorageException.invalid();validate(l.quantity(),l.opened(),l.source(),l.note());names(l.nameZh(),l.nameEn());
    photos.requireOptional(s,p.customerId(),l.photos(),"application");for(var photo:l.photos())if(!allPhotos.add(photo))throw StorageException.conflict("不同瓶请分别上传照片");
    String zh=text(l.nameZh()),en=text(l.nameEn());
    if(l.productId()!=null){var product=products.selectable(s,l.productId());zh=product.nameZh();en=product.nameEn();}
    else if(zh.isBlank()&&en.isBlank())throw StorageException.invalid();
    lines.add(new Line(UUID.randomUUID(),l.productId(),zh,en,l.quantity(),l.opened(),l.source(),List.copyOf(l.photos()),text(l.note())));
   }
   if((c.withdrawalId()==null)!=(c.withdrawalItemId()==null))throw StorageException.invalid();
   if(c.withdrawalId()!=null)validateRedeposit(s,p,c,lines);
   UUID id=UUID.randomUUID();var now=clock.instant();
   r.update(s,"insert into wine_storage_applications(tenant_id,store_id,id,customer_id,status,original,created_at,updated_at) values(?,?,?,?,'pending',?::jsonb,?,?)",id,p.customerId(),r.json(c),at(now),at(now));
   for(Line l:lines){r.update(s,"insert into wine_storage_application_lines(tenant_id,store_id,id,customer_id,application_id,original) values(?,?,?,?,?,?::jsonb)",l.id(),p.customerId(),id,r.json(l));
    photos.attach(s,p.customerId(),l.photos(),id,"application");
    if(l.productId()==null)r.update(s,"insert into wine_storage_suggestions(tenant_id,store_id,id,customer_id,line_id,name_zh,name_en,status) values(?,?,?,?,?,?,?,'pending')",UUID.randomUUID(),p.customerId(),l.id(),l.nameZh(),l.nameEn());
   }
   if(c.withdrawalId()!=null)r.update(s,"insert into wine_storage_redeposits(tenant_id,store_id,customer_id,intent_id,item_id,application_id,quantity) values(?,?,?,?,?,?,?)",p.customerId(),c.withdrawalId(),c.withdrawalItemId(),id,lines.stream().mapToInt(Line::quantity).sum());
   r.event(s,p.customerId(),id,"applied",p.customerId(),Map.of("lines",lines,"rulesAccepted",true),now);return r.application(s,id,false);
  });
 }
 private void validateRedeposit(StoreScope s,CustomerAuthPrincipal p,Apply c,List<Line> lines){
  var intent=r.intent(s,c.withdrawalId(),true);access.own(intent.customerId(),p.customerId());if(!intent.status().equals("completed"))throw StorageException.conflict("必须先完成实际领取");
  var selected=intent.selections().stream().filter(x->x.itemId().equals(c.withdrawalItemId())).findFirst().orElseThrow(StorageException::missing);
  var old=r.item(s,c.withdrawalItemId(),false,clock.instant());
  if(lines.stream().anyMatch(l->!Objects.equals(l.productId(),old.snapshot().productId())||!l.source().equals(old.source())))throw StorageException.conflict("再次寄存须关联原领取酒品及来源");
  int used=r.rows(s,"select coalesce(sum(d.quantity),0) used from wine_storage_redeposits d join wine_storage_applications a on a.id=d.application_id where d.tenant_id=? and d.store_id=? and d.intent_id=? and d.item_id=? and a.status in('pending','stored')",c.withdrawalId(),c.withdrawalItemId()).stream().mapToInt(row->((Number)row.get("used")).intValue()).sum();
  if(used+lines.stream().mapToInt(Line::quantity).sum()>selected.quantity())throw StorageException.conflict("再次寄存数量超过原实际领取数量");
  // Photos from the old record cannot be recycled as new evidence.
  for(Line line:lines)if(line.photos().stream().anyMatch(photo->old.snapshot().applicationPhotos().contains(photo)||old.snapshot().receiptPhotos().contains(photo)))throw StorageException.conflict("再次寄存如需上传照片，请使用本次实物照片");
 }
 @Transactional public Application cancel(StoreScope s,CustomerAuthPrincipal p,UUID id,Key c){
  access.customer(s,p,false);if(c==null)throw StorageException.invalid();return commands.run(s,p.customerId(),"customer","cancel.apply",c.idempotencyKey(),Map.of("id",id,"command",c),Application.class,()->{var app=r.application(s,id,true);access.own(app.customerId(),p.customerId());return close(s,app,"cancelled",text(c.reason()),p.customerId());});
 }
 @Transactional public Application reject(StoreScope s,CurrentActor a,UUID id,Key c){
  access.staff(s,a,"wine_storage.receive",false);if(c==null||text(c.reason()).isBlank()||c.reason().length()>2000)throw StorageException.invalid();return commands.run(s,a.actorId(),"staff","reject",c.idempotencyKey(),Map.of("id",id,"command",c),Application.class,()->close(s,r.application(s,id,true),"rejected",c.reason(),a.actorId()));
 }
 private Application close(StoreScope s,Application app,String status,String reason,UUID actor){
  if(!app.status().equals("pending"))throw StorageException.conflict("申请已处理");
  r.change(s,"applications",app.id(),"status=?,reason=?,updated_at=?",status,reason,at(clock.instant()));
  r.event(s,app.customerId(),app.id(),status,actor,Map.of("reason",reason),clock.instant());return r.application(s,app.id(),false);
 }

 @Transactional public Application receive(StoreScope s,CurrentActor a,UUID id,Receive c){
  access.staff(s,a,"wine_storage.receive",true);if(c==null||c.lines()==null||c.lines().isEmpty()||c.lines().stream().anyMatch(Objects::isNull)||text(c.reason()).isBlank()||c.reason().length()>2000)throw StorageException.invalid();
  return commands.run(s,a.actorId(),"staff","receive",c.idempotencyKey(),Map.of("id",id,"command",c),Application.class,()->{
   var app=r.application(s,id,true);if(!app.status().equals("pending"))throw StorageException.conflict("申请已处理");
   var original=new HashMap<UUID,Line>();app.lines().forEach(l->original.put(l.id(),l));
   if(c.lines().size()>100||!c.lines().stream().map(ReceiptLine::lineId).collect(java.util.stream.Collectors.toSet()).equals(original.keySet()))throw StorageException.invalid();
   if(app.original().withdrawalId()!=null){
    var previous=r.intent(s,app.original().withdrawalId(),true);int available=previous.selections().stream().filter(line->line.itemId().equals(app.original().withdrawalItemId())).findFirst().orElseThrow(StorageException::missing).quantity();
    long used=((Number)r.rows(s,"select coalesce(sum(d.quantity),0) used from wine_storage_redeposits d join wine_storage_applications a on a.id=d.application_id where d.tenant_id=? and d.store_id=? and d.intent_id=? and d.item_id=? and d.application_id<>? and a.status in('pending','stored')",previous.id(),app.original().withdrawalItemId(),id).getFirst().get("used")).longValue();
    int actual=c.lines().stream().mapToInt(ReceiptLine::quantity).sum();if(actual<1||used+actual>available)throw StorageException.conflict("再次收存数量不能超过原实际领取数量");
    r.change(s,"redeposits",id,"quantity=?",actual);
   }
   CustodianRef custodian=custodians.resolve(s,c.custodianId());String zone=access.timezone(s);var now=clock.instant();var confirmed=new ArrayList<ReceiptLine>();var associated=new HashMap<UUID,com.rpb.reservation.catalog.application.CatalogDtos.Product>();Set<UUID> receiptPhotos=new HashSet<>();
   for(ReceiptLine l:c.lines()){
    var before=original.get(l.lineId());if(before==null||text(l.location()).length()>200||text(l.label()).length()>200)throw StorageException.invalid();validate(l.quantity(),l.opened(),l.source(),l.note());names(l.nameZh(),l.nameEn());
    photos.requireOptional(s,app.customerId(),l.photos(),"receipt");
    for(UUID photo:l.photos())if(!receiptPhotos.add(photo))throw StorageException.conflict("每瓶收存照片须独立");
    var product=l.productId()==null&&associated.containsKey(l.lineId())?associated.get(l.lineId()):products.associate(s,l.productId(),l.categoryId(),l.nameZh(),l.nameEn(),a.actorId());associated.put(l.lineId(),product);
    if(app.original().withdrawalId()!=null&&(!product.id().equals(before.productId())||!l.source().equals(before.source())))throw StorageException.conflict("再次寄存不能更换原领取酒品及来源");
    var rule=r.rule(s,l.ruleId());if(!rule.categoryId().equals(product.categoryId()))throw StorageException.conflict("保存规则与酒品分类不符");
    if(r.rules(s).stream().anyMatch(latest->latest.categoryId().equals(rule.categoryId())&&latest.version()>rule.version()))throw StorageException.conflict("保存规则已有新版本，请刷新后重新确认");
    var location=locations.resolve(s,l.storageLocationId());
    var snapshot=new Snapshot(product.id(),product.code(),product.nameZh(),product.nameEn(),product.categoryId(),rule,zone,text(l.note()),before.photos(),List.copyOf(l.photos()),custodian,location);
    var item=new Item(UUID.randomUUID(),id,app.customerId(),snapshot,l.quantity(),l.quantity(),l.opened(),l.source(),text(l.location()),text(l.label()),now,StorageRules.expires(now,zone,rule.months()),"stored");
    r.addItem(s,id,l.lineId(),rule.id(),item);
    photos.attach(s,app.customerId(),l.photos(),item.id(),"item");
    confirmed.add(new ReceiptLine(l.lineId(),product.id(),product.categoryId(),product.nameZh(),product.nameEn(),l.quantity(),l.opened(),l.source(),rule.id(),l.photos(),text(l.note()),text(l.location()),text(l.label()),l.storageLocationId(),location));
    r.change(s,"suggestions",l.lineId(),"status=?,product_id=?,reviewed_by=?,reviewed_at=?",l.productId()==null?"created":"linked",product.id(),a.actorId(),at(now));
   }
   var confirmation=new Receive(confirmed,c.reason(),c.idempotencyKey(),c.custodianId());r.change(s,"applications",id,"status='stored',confirmation=?::jsonb,reason=?,updated_at=?",r.json(confirmation),c.reason(),at(now));
   r.event(s,app.customerId(),id,"received",a.actorId(),confirmation,now);return r.application(s,id,false);
  });
 }
 @Transactional public Event correct(StoreScope s,CurrentActor a,UUID id,Correction c){
  access.staff(s,a,"wine_storage.correct",false);if(c==null||text(c.reason()).isBlank()||text(c.note()).isBlank()||c.note().length()>2000)throw StorageException.invalid();
  return commands.run(s,a.actorId(),"staff","correct",c.idempotencyKey(),Map.of("id",id,"command",c),Event.class,()->{var item=r.item(s,id,true,clock.instant());return r.event(s,item.customerId(),id,"correction",a.actorId(),c,clock.instant());});
 }
 @Transactional public Event addPhotos(StoreScope s,CurrentActor a,UUID id,PhotoAdd c){
  access.staff(s,a,"wine_storage.receive",false);if(c==null||text(c.note()).length()>2000)throw StorageException.invalid();
  return commands.run(s,a.actorId(),"staff","photos",c.idempotencyKey(),Map.of("id",id,"command",c),Event.class,()->{var item=r.item(s,id,true,clock.instant());photos.require(s,item.customerId(),c.photos(),"followup");photos.attach(s,item.customerId(),c.photos(),id,"item");return r.event(s,item.customerId(),id,"photos.added",a.actorId(),c,clock.instant());});
 }
 public static String text(String value){return value==null?"":value.trim();}
 private void names(String zh,String en){if(text(zh).length()>200||text(en).length()>200)throw StorageException.invalid();}
 private void validate(int quantity,boolean opened,String source,String note){if(quantity<1||quantity>10000||opened&&quantity!=1||!Set.of("store","customer").contains(text(source))||text(note).length()>2000)throw StorageException.invalid();}
}
