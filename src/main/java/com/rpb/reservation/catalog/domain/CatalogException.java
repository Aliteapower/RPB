package com.rpb.reservation.catalog.domain;
public class CatalogException extends RuntimeException {
 private final String code;private final int status;
 public CatalogException(String code,int status,String message){super(message);this.code=code;this.status=status;}
 public String code(){return code;}public int status(){return status;}
 public static CatalogException invalid(){return new CatalogException("CATALOG_INVALID",400,"产品资料不完整或格式错误");}
 public static CatalogException missing(){return new CatalogException("CATALOG_NOT_FOUND",404,"产品或分类不存在");}
 public static CatalogException stale(){return new CatalogException("CATALOG_STALE",409,"产品资料已变化，请刷新后重试");}
}
