package com.rpb.reservation.workforce.documents.expiry;
import java.time.LocalDate;import java.util.*;
public final class DocumentExpiryDtos {
 private DocumentExpiryDtos(){}
 public record Item(UUID documentId,UUID employeeId,String employeeNo,String employeeName,String typeName,LocalDate expiresOn,long daysRemaining,String status){}
 public record Page(UUID storeId,String timezone,LocalDate today,int days,String status,long due,long expired,long total,int limit,int offset,List<Item> items){}
 public record EmployeeCount(UUID employeeId,long due,long expired){}
 public record EmployeePage(UUID storeId,String timezone,LocalDate today,int days,List<EmployeeCount> items){}
}
