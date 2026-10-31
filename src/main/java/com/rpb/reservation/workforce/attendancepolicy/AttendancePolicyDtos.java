package com.rpb.reservation.workforce.attendancepolicy;
import java.time.*;import java.util.*;
public final class AttendancePolicyDtos {
 private AttendancePolicyDtos(){}
 public record Arrangement(List<Integer> weekdays,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalTime startTime,int standardWorkMinutes,int unpaidBreakMinutes,Integer unpaidBreakStartOffsetMinutes){public Arrangement{if(weekdays!=null)weekdays=List.copyOf(weekdays);}}
 public record DefaultWrite(Long expectedVersion,LocalDate effectiveFrom,String mode,Arrangement arrangement){}
 public record AssignmentWrite(Long expectedVersion,LocalDate effectiveFrom,Boolean adoptDefault,UUID defaultId,String mode,Arrangement arrangement){}
 public record PolicyVersion(UUID id,UUID employeeId,UUID companyId,UUID sourceStoreId,LocalDate effectiveFrom,String mode,String origin,UUID sourceDefaultId,long sourceDefaultVersion,Arrangement arrangement,String timezone,long version){}
 public record EffectiveAttendancePolicy(String mode,UUID policyId,long version,UUID sourceStoreId,String origin,Arrangement arrangement,String timezone,String sourceReason){}
 public record PolicyPage(UUID employeeId,boolean canManage,LocalDate today,List<PolicyVersion> assignments,EffectiveAttendancePolicy effective){}
 public record ExpectedInterval(LocalDate workDate,Instant start,Instant end,long standardWorkSeconds,long unpaidBreakSeconds,Instant breakStart,Instant breakEnd,UUID policyId,long policyVersion){}
}
