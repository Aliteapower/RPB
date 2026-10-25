package com.rpb.reservation.cards.application;
public class CardException extends RuntimeException {
 private final String code;private final int status;
 public CardException(String code,int status,String message){super(message);this.code=code;this.status=status;}
 public String code(){return code;}public int status(){return status;}
 public static CardException invalid(){return new CardException("CARD_INVALID",400,"卡业务请求不完整或格式错误");}
 public static CardException missing(){return new CardException("CARD_NOT_FOUND",404,"卡或业务单不存在");}
 public static CardException conflict(String message){return new CardException("CARD_CONFLICT",409,message);}
 public static CardException forbidden(){return new CardException("CARD_FORBIDDEN",403,"无卡业务操作权限");}
}
