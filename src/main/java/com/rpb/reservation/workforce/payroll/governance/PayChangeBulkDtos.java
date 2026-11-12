package com.rpb.reservation.workforce.payroll.governance;

import java.util.*;
public final class PayChangeBulkDtos {
 private PayChangeBulkDtos(){}
 public record Item(UUID requestId,UUID commandId,Long expectedRevision,String expectedHash,String action,String note){}
 public record Command(UUID companyId,String month,List<Item> items){}
 public record Result(UUID requestId,UUID commandId,String outcome,PayChangeDtos.Request request,String errorCode,Integer httpStatus){}
 public record Response(List<Result> results){}
}
