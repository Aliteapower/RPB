package com.rpb.reservation.catalog.application;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.domain.CatalogException;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Atomic edit aggregate: existing product rules and barcode ownership remain in their modules. */
@Service @Transactional(readOnly=true)
public class CatalogProductEditService {
 public record Detail(Product product,List<String> barcodes){}
 public record Write(ProductWrite product,List<String> barcodes,List<String> expectedBarcodes,int expectedListingVersion,String idempotencyKey){}
 private final CatalogAccess access;private final CatalogRepository repository;private final CatalogCommands commands;private final CatalogService products;private final CatalogBarcodeService barcodes;
 public CatalogProductEditService(CatalogAccess a,CatalogRepository r,CatalogCommands c,CatalogService p,CatalogBarcodeService b){access=a;repository=r;commands=c;products=p;barcodes=b;}
 public Detail get(StoreScope s,CurrentActor a,UUID id){access.require(s,a,"catalog.view");return new Detail(repository.get(s,id),barcodes.values(s,id));}
 @Transactional public Detail save(StoreScope s,CurrentActor a,UUID id,Write c){
  access.requireGlobal(s,a,"catalog.manage");if(c==null||c.product()==null||c.idempotencyKey()==null||c.idempotencyKey().length()>80||c.barcodes()==null||c.expectedBarcodes()==null)throw CatalogException.invalid();
  return commands.run(s,a.actorId(),"product.edit",c.idempotencyKey(),Map.of("id",id==null?"new":id,"body",c),Detail.class,()->{
   if(id!=null&&repository.lock(s,id).listingVersion()!=c.expectedListingVersion())throw CatalogException.stale();var p=c.product();
   var command=new ProductWrite(p.code(),p.categoryId(),p.nameZh(),p.nameEn(),p.descriptionZh(),p.descriptionEn(),p.kind(),p.unit(),p.imageUrl(),p.priceCents(),p.taxMode(),p.taxCategory(),p.taxRate(),p.channels(),p.expectedVersion(),c.idempotencyKey()+":product");
   var saved=products.save(s,a,id,command);barcodes.replace(s,a,saved.id(),c.expectedBarcodes(),c.barcodes());return new Detail(repository.get(s,saved.id()),barcodes.values(s,saved.id()));
  });
 }
}
