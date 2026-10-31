package com.rpb.reservation.winestorage.domain;

import java.util.List;

public class StorageException extends RuntimeException {
 private final String code; private final int status; private final List<?> problems;
 public StorageException(String code,int status,String message){this(code,status,message,List.of());}
 public StorageException(String code,int status,String message,List<?> problems){super(message);this.code=code;this.status=status;this.problems=List.copyOf(problems);}
 public String code(){return code;} public int status(){return status;} public List<?> problems(){return problems;}
 public static StorageException invalid(){return new StorageException("STORAGE_INVALID",400,"请检查寄存资料、数量及照片");}
 public static StorageException missing(){return new StorageException("STORAGE_NOT_FOUND",404,"寄存记录不存在");}
 public static StorageException forbidden(){return new StorageException("STORAGE_FORBIDDEN",403,"无此门店或业务操作权限");}
 public static StorageException conflict(String message){return new StorageException("STORAGE_CONFLICT",409,message);}
}
