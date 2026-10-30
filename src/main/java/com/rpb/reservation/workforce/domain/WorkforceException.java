package com.rpb.reservation.workforce.domain;

public class WorkforceException extends RuntimeException {
    private final String code;
    private final int status;
    public WorkforceException(String code,int status) { super(code); this.code=code; this.status=status; }
    public String code(){return code;}
    public int status(){return status;}
    public static WorkforceException forbidden(){return new WorkforceException("WORKFORCE_FORBIDDEN",403);}
    public static WorkforceException invalid(){return new WorkforceException("WORKFORCE_INVALID",400);}
    public static WorkforceException missing(){return new WorkforceException("WORKFORCE_NOT_FOUND",404);}
    public static WorkforceException conflict(){return new WorkforceException("WORKFORCE_CONFLICT",409);}
}
