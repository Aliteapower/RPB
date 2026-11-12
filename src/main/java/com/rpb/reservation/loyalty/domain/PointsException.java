package com.rpb.reservation.loyalty.domain;
public class PointsException extends RuntimeException {
 private final String code; private final int status;
 public PointsException(String code,int status,String message){super(message);this.code=code;this.status=status;}
 public String code(){return code;} public int status(){return status;}
 public static PointsException invalid(){return new PointsException("POINTS_REQUEST_INVALID",400,"积分请求资料不正确");}
 public static PointsException missing(){return new PointsException("POINTS_NOT_FOUND",404,"积分记录不存在或无权访问");}
 public static PointsException conflict(String code,String message){return new PointsException(code,409,message);}
}
