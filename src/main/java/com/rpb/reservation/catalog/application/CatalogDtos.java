package com.rpb.reservation.catalog.application;
import java.math.BigDecimal;
import java.util.*;
public final class CatalogDtos {
 private CatalogDtos(){}
 public record Category(UUID id,String code,String nameZh,String nameEn,boolean enabled,int sortOrder,int version){}
 public record CategoryWrite(String code,String nameZh,String nameEn,boolean enabled,int sortOrder,Integer expectedVersion,String idempotencyKey){}
 public record ProductWrite(String code,UUID categoryId,String nameZh,String nameEn,String descriptionZh,String descriptionEn,String kind,String unit,String imageUrl,Long priceCents,String taxMode,String taxCategory,BigDecimal taxRate,List<String> channels,Integer expectedVersion,String idempotencyKey){
  public ProductWrite(String code,UUID categoryId,String nameZh,String nameEn,String descriptionZh,String descriptionEn,String kind,String unit,String imageUrl,int priceCents,String taxMode,String taxCategory,BigDecimal taxRate,List<String> channels,Integer expectedVersion,String idempotencyKey){this(code,categoryId,nameZh,nameEn,descriptionZh,descriptionEn,kind,unit,imageUrl,Long.valueOf(priceCents),taxMode,taxCategory,taxRate,channels,expectedVersion,idempotencyKey);}
 }
 public record Product(UUID id,UUID categoryId,String code,String nameZh,String nameEn,String descriptionZh,String descriptionEn,String kind,String unit,String imageUrl,String status,int version,Long priceCents,String currency,String taxMode,String taxCategory,BigDecimal taxRate,List<String> channels,boolean listingEnabled,int listingVersion) {
  public String displayName(String locale){return locale!=null&&locale.startsWith("en")?(nameEn==null||nameEn.isBlank()?nameZh:nameEn):(nameZh==null||nameZh.isBlank()?nameEn:nameZh);}
 }
 public record Status(String status,int expectedVersion,String idempotencyKey){}
 public record Listing(Long priceCents,List<String> channels,boolean enabled,int expectedVersion,String idempotencyKey){
  public Listing(long priceCents,List<String> channels,boolean enabled,int expectedVersion,String idempotencyKey){this(Long.valueOf(priceCents),channels,enabled,expectedVersion,idempotencyKey);}
 }
 public record QuoteRequest(int quantity,String channel,int expectedVersion){}
 public record Quote(Product product,int quantity,long netCents,long taxCents,long grossCents,String currency,int ruleVersion){}
 public record ProductPage(List<Product> items,long total,int page,int size,boolean hasMore){}
}
