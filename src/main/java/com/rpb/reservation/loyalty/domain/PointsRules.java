package com.rpb.reservation.loyalty.domain;
public final class PointsRules {
 private PointsRules(){}
 public static int balance(int before,int quantity,boolean credit){
  if(quantity<=0)throw PointsException.invalid();
  long after=(long)before+(credit?(long)quantity:-(long)quantity);
  if(after<0)throw PointsException.conflict("POINTS_INSUFFICIENT","积分余额不足，请先核实原记录");
  if(after>Integer.MAX_VALUE)throw PointsException.conflict("POINTS_LIMIT_EXCEEDED","积分余额超过上限");
  return (int)after;
 }
 public static void version(int actual,Integer expected){if(expected==null||actual!=expected)throw PointsException.conflict("POINTS_VERSION_STALE","积分已变化，请刷新核对");}
 public static String required(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw PointsException.invalid();return value.trim();}
 public static void optional(String value,int max){if(value!=null&&value.length()>max)throw PointsException.invalid();}
}
