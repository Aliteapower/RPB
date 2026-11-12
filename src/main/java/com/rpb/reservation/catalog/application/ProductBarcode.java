package com.rpb.reservation.catalog.application;
import com.rpb.reservation.catalog.domain.CatalogException;
/** GS1 GTIN normalization. Never parse identifiers as numbers. */
public final class ProductBarcode {
 private ProductBarcode(){}
 public static String normalize(String value){
  String v=value==null?"":value.trim();if(!v.matches("[0-9]{8}|[0-9]{12,14}"))throw CatalogException.invalid();
  int sum=0,weight=3;for(int i=v.length()-2;i>=0;i--){sum+=(v.charAt(i)-'0')*weight;weight=4-weight;}
  if((10-sum%10)%10!=v.charAt(v.length()-1)-'0')throw CatalogException.invalid();
  return "0".repeat(14-v.length())+v;
 }
}
