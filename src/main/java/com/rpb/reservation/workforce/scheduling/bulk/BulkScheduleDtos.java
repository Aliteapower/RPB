package com.rpb.reservation.workforce.scheduling.bulk;

import java.time.*;
import java.util.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;

public final class BulkScheduleDtos {
    private BulkScheduleDtos() {}
    public record Request(List<UUID> employeeIds, UUID shiftId, LocalDate from, LocalDate to, List<Integer> weekdays) {}
    public record Commit(Request request, String fingerprint) {}
    public record Employee(UUID id,String employeeNo,String name,UUID positionId) {}
    public record Position(UUID id,String name,boolean enabled) {}
    public record Options(List<Employee> items,long total,int limit,int offset,List<Position> positions,List<Shift> shifts,String timezone) {}
    public record Item(UUID employeeId,String employeeNo,String employeeName,LocalDate date,String status,String reason) {}
    public record Preview(String fingerprint,int employeeCount,int total,int ready,int existing,int conflicts,List<Item> items,String timezone) {}
    public record Result(UUID commandId,int created,int existing,int conflicts,List<UUID> scheduleIds) {}
    public record ShiftCount(UUID shiftId,String name,long people,long personDays) {}
    public record ShiftDayCount(UUID shiftId,LocalDate date,long people) {}
    public record Staffing(List<ShiftCount> shifts,List<ShiftDayCount> days) {}
    public record StaffingSchedule(UUID id,Instant startAt,Instant endAt,String timezone,long version) {}
    public record StaffingEmployee(UUID employeeId,String employeeNo,String name,LocalDate date,String shiftName,List<StaffingSchedule> schedules) {}
    public record StaffingEmployees(List<StaffingEmployee> items,long total,long people,int limit,int offset) {}
    public record Board(List<Employee> employees,long total,int limit,int offset,List<Schedule> items,String timezone,long weekScheduleCount,List<Position> positions,long periodScheduleCount,Staffing staffing,List<com.rpb.reservation.workforce.calendar.CalendarDtos.CalendarEvent> calendar,List<com.rpb.reservation.workforce.scheduling.restswap.RestSwapDtos.Link> restSwaps) {
        public Board(List<Employee> employees,long total,int limit,int offset,List<Schedule> items,String timezone,long weekScheduleCount,List<Position> positions,long periodScheduleCount,Staffing staffing,List<com.rpb.reservation.workforce.calendar.CalendarDtos.CalendarEvent> calendar){this(employees,total,limit,offset,items,timezone,weekScheduleCount,positions,periodScheduleCount,staffing,calendar,List.of());}
        public Board(List<Employee> employees,long total,int limit,int offset,List<Schedule> items,String timezone,long weekScheduleCount,List<Position> positions,long periodScheduleCount,Staffing staffing){this(employees,total,limit,offset,items,timezone,weekScheduleCount,positions,periodScheduleCount,staffing,List.of());}
    }
}

