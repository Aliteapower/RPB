package com.rpb.reservation.workforce.scheduling;
import java.time.*;
import java.util.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
public final class SchedulingDtos {
    private SchedulingDtos(){}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Shift(UUID id,String name,String startTime,String endTime,boolean nextDay,String breakMode,int breakMinutes,int breakAfterMinutes,int breakStartOffset,int breakEndOffset,boolean breakPaid,boolean enabled,long version,com.rpb.reservation.workforce.scheduling.rules.AttendanceRules rules,Boolean startNextDay){
        public Shift(UUID id,String name,String startTime,String endTime,boolean nextDay,String breakMode,int breakMinutes,int breakAfterMinutes,int breakStartOffset,int breakEndOffset,boolean breakPaid,boolean enabled,long version,com.rpb.reservation.workforce.scheduling.rules.AttendanceRules rules){this(id,name,startTime,endTime,nextDay,breakMode,breakMinutes,breakAfterMinutes,breakStartOffset,breakEndOffset,breakPaid,enabled,version,rules,null);}
        public Shift(UUID id,String name,String startTime,String endTime,boolean nextDay,String breakMode,int breakMinutes,int breakAfterMinutes,int breakStartOffset,int breakEndOffset,boolean breakPaid,boolean enabled,long version){this(id,name,startTime,endTime,nextDay,breakMode,breakMinutes,breakAfterMinutes,breakStartOffset,breakEndOffset,breakPaid,enabled,version,null);}
    }
    public record ShiftWrite(Long expectedVersion,String name,String startTime,String endTime,Boolean nextDay,String breakMode,Integer breakMinutes,Integer breakAfterMinutes,Integer breakStartOffset,Integer breakEndOffset,Boolean breakPaid,Boolean enabled,com.rpb.reservation.workforce.scheduling.rules.AttendanceRules rules,Boolean startNextDay){
        public ShiftWrite(Long expectedVersion,String name,String startTime,String endTime,Boolean nextDay,String breakMode,Integer breakMinutes,Integer breakAfterMinutes,Integer breakStartOffset,Integer breakEndOffset,Boolean breakPaid,Boolean enabled,com.rpb.reservation.workforce.scheduling.rules.AttendanceRules rules){this(expectedVersion,name,startTime,endTime,nextDay,breakMode,breakMinutes,breakAfterMinutes,breakStartOffset,breakEndOffset,breakPaid,enabled,rules,null);}
        public ShiftWrite(Long expectedVersion,String name,String startTime,String endTime,Boolean nextDay,String breakMode,Integer breakMinutes,Integer breakAfterMinutes,Integer breakStartOffset,Integer breakEndOffset,Boolean breakPaid,Boolean enabled){this(expectedVersion,name,startTime,endTime,nextDay,breakMode,breakMinutes,breakAfterMinutes,breakStartOffset,breakEndOffset,breakPaid,enabled,null);}
    }
    public record Schedule(UUID id,UUID employeeId,String employeeName,String employeeNo,UUID shiftId,LocalDate workDate,Instant startAt,Instant endAt,String timezone,String status,Shift shift,UUID companyId,long version){}
    public record ScheduleWrite(Long expectedVersion,UUID employeeId,UUID shiftId,LocalDate workDate,String status){}
    public record SchedulePage(List<Schedule> items,List<Option> employees,String timezone){}
    public record CopyWrite(LocalDate sourceWeek,LocalDate targetWeek){}
}
