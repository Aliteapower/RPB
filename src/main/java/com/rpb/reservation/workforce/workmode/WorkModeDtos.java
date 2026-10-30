package com.rpb.reservation.workforce.workmode;
import java.time.*;import java.util.*;
public final class WorkModeDtos {
 private WorkModeDtos(){}
 public record Point(String label,Double latitude,Double longitude,Double radiusMeters){}
 public record Location(Double latitude,Double longitude,Double accuracyMeters,Instant capturedAt,String description){}
 public record Assignment(UUID id,UUID employeeId,LocalDate effectiveFrom,String mode,String origin,UUID positionId,long positionVersion,long version){}
 public record AssignmentWrite(Long expectedVersion,LocalDate effectiveFrom,String mode,Boolean inheritPosition){}
 public record Site(Point point,long version){}
 public record SiteWrite(Long expectedVersion,Point point){}
 public record HomeAddress(UUID id,UUID employeeId,Point point,LocalDate effectiveFrom,String status,long version,UUID applicantId,UUID reviewerId,String reviewNote,Instant createdAt){}
 public record HomeWrite(Point point,LocalDate effectiveFrom){}
 public record HomeDecision(Long expectedVersion,String status,String note){}
 public record ClockPolicy(String mode,UUID assignmentId,long assignmentVersion,Point point,UUID addressId,long addressVersion,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Long siteVersion){public ClockPolicy(String mode,UUID assignmentId,long assignmentVersion,Point point,UUID addressId,long addressVersion){this(mode,assignmentId,assignmentVersion,point,addressId,addressVersion,null);}}
 public record WorkModePage(UUID employeeId,String employeeName,boolean canManage,boolean canReview,LocalDate serverDate,List<Assignment> assignments,List<HomeAddress> addresses,ClockPolicy policy){}
}
