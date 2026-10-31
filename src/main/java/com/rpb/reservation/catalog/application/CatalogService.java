package com.rpb.reservation.catalog.application;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.domain.CatalogException;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.catalog.pricing.TaxCalculator;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CatalogService {
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public ProductPage storageProducts(StoreScope s,CurrentActor a,String query,int page,int size){access.require(s,a,"catalog.view");if(query==null||query.length()>120||page<0||page>100000||size<1||size>100)throw CatalogException.invalid();return r.storageProducts(s,query.trim(),page,size);}
 private final CatalogAccess access;private final CatalogRepository r;private final CatalogCommands commands;private final ProductCatalogQuery query;
 public CatalogService(CatalogAccess access,CatalogRepository r,CatalogCommands commands,ProductCatalogQuery query){this.access=access;this.r=r;this.commands=commands;this.query=query;}
 public List<Category> categories(StoreScope s,CurrentActor a){access.require(s,a,"catalog.view");return r.categories(s.tenantId().value());}
 public List<Product> products(StoreScope s,CurrentActor a){access.require(s,a,"catalog.view");return r.list(s);}
 @Transactional public Category category(StoreScope s,CurrentActor a,UUID id,CategoryWrite c){access.requireGlobal(s,a,"catalog.manage");if(c==null||blank(c.code())||blank(c.nameZh())&&blank(c.nameEn()))throw CatalogException.invalid();return commands.run(s,a.actorId(),"category.save",c.idempotencyKey(),Map.of("id",id==null?"new":id,"body",c),Category.class,()->{UUID saved=id;if(id==null)saved=r.category(s,c);else r.categoryUpdate(s,id,c);UUID result=saved;return r.categories(s.tenantId().value()).stream().filter(x->x.id().equals(result)).findFirst().orElseThrow(CatalogException::missing);});}
 @Transactional public Product save(StoreScope s,CurrentActor a,UUID id,ProductWrite c){access.requireGlobal(s,a,"catalog.manage");validate(c,id==null);return commands.run(s,a.actorId(),"product.save",c.idempotencyKey(),Map.of("id",id==null?"new":id,"body",c),Product.class,()->{if(r.categories(s.tenantId().value()).stream().noneMatch(x->x.id().equals(c.categoryId())))throw CatalogException.missing();UUID saved=id;if(id==null)saved=r.create(s,c);else r.update(s,id,c);return r.get(s,saved);});}
 @Transactional public Product status(StoreScope s,CurrentActor a,UUID id,Status c){access.requireGlobal(s,a,"catalog.publish");if(c==null||!Set.of("active","inactive","archived").contains(c.status()==null?"":c.status()))throw CatalogException.invalid();return commands.run(s,a.actorId(),"product.status",c.idempotencyKey(),Map.of("id",id,"body",c),Product.class,()->{r.lock(s,id);r.status(s,id,c);return r.get(s,id);});}
 @Transactional public Product listing(StoreScope s,CurrentActor a,UUID id,Listing c){access.require(s,a,"catalog.manage");if(c==null||c.priceCents()!=null&&(c.priceCents()<0||c.priceCents()>9999999999L)||c.priceCents()==null&&c.channels()!=null&&!Set.of("storage").containsAll(c.channels())||c.expectedVersion()<0||c.channels()==null||!Set.of("cards","rewards","shopping","storage").containsAll(c.channels()))throw CatalogException.invalid();return commands.run(s,a.actorId(),"product.listing",c.idempotencyKey(),Map.of("id",id,"body",c),Product.class,()->{var current=r.lock(s,id);if(current.listingVersion()!=c.expectedVersion())throw CatalogException.stale();r.listingUpdate(s,id,c);return r.get(s,id);});}
 public Quote quote(StoreScope s,CurrentActor a,UUID id,QuoteRequest c){access.require(s,a,"catalog.view");if(c==null)throw CatalogException.invalid();var p=query.requireUsable(s,id,c.channel());if(c.expectedVersion()!=p.version())throw CatalogException.stale();if(p.priceCents()==null||p.taxMode()==null||p.taxRate()==null)throw new CatalogException("CATALOG_UNPRICED",409,"产品尚未定价");var amount=TaxCalculator.calculate(p.priceCents(),c.quantity(),p.taxMode(),p.taxRate());return new Quote(p,c.quantity(),amount.netCents(),amount.taxCents(),amount.grossCents(),p.currency(),1);}
 private void validate(ProductWrite c,boolean creating){
  if(c==null||(blank(c.code())&&!(creating&&c.channels()!=null&&c.channels().contains("storage")))||(c.code()!=null&&c.code().length()>80)||c.categoryId()==null||blank(c.nameZh())&&blank(c.nameEn())||blank(c.unit())||!Set.of("physical","service").contains(c.kind()==null?"":c.kind())||c.channels()==null||!Set.of("cards","rewards","shopping","storage").containsAll(c.channels())||c.imageUrl()!=null&&!c.imageUrl().isBlank()&&!c.imageUrl().matches("https?://[^\\s]+|/[^\\s]*"))throw CatalogException.invalid();
  if(c.priceCents()==null){if(!Set.of("storage").containsAll(c.channels())||c.taxMode()!=null||c.taxCategory()!=null||c.taxRate()!=null)throw CatalogException.invalid();return;}
  if(!Set.of("taxable","zero","exempt","not_applicable").contains(c.taxCategory()==null?"":c.taxCategory())||c.taxRate()==null||!c.taxCategory().equals("taxable")&&c.taxRate().signum()!=0)throw CatalogException.invalid();
  try{TaxCalculator.calculate(c.priceCents(),1,c.taxMode(),c.taxRate());}catch(IllegalArgumentException e){throw CatalogException.invalid();}
 }
 private static boolean blank(String s){return s==null||s.isBlank();}
}
