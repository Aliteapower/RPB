package com.rpb.reservation.workforce.leave;

import java.time.Instant;
import java.util.UUID;

public final class LeaveDtos {
    private LeaveDtos() {}
    public record LeaveType(UUID id,String name,boolean paid,boolean enabled,long version) {}
    public record LeaveTypeWrite(Long expectedVersion,String name,Boolean paid,Boolean enabled) {}
    public record Leave(UUID id,UUID scheduleId,UUID employeeId,String employeeName,UUID typeId,String typeName,boolean paid,Instant startAt,Instant endAt,String reason,String status,UUID applicantId,UUID reviewerId,String reviewNote,long version,Instant createdAt,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String medicalReference) { public Leave(UUID id,UUID scheduleId,UUID employeeId,String employeeName,UUID typeId,String typeName,boolean paid,Instant startAt,Instant endAt,String reason,String status,UUID applicantId,UUID reviewerId,String reviewNote,long version,Instant createdAt){this(id,scheduleId,employeeId,employeeName,typeId,typeName,paid,startAt,endAt,reason,status,applicantId,reviewerId,reviewNote,version,createdAt,null);} }
    public record LeaveWrite(UUID scheduleId,UUID typeId,Instant startAt,Instant endAt,String reason,String medicalReference) {public LeaveWrite(UUID scheduleId,UUID typeId,Instant startAt,Instant endAt,String reason){this(scheduleId,typeId,startAt,endAt,reason,null);}}
}
