package com.rpb.reservation.workforce.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CalendarDtos {
    private CalendarDtos() {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record CalendarEvent(UUID id,LocalDate date,String kind,String name,boolean enabled,long version,String scope,LocalDate originDate,Boolean observed,Integer year,String englishName,Boolean tentative) {
        public CalendarEvent(UUID id,LocalDate date,String kind,String name,boolean enabled,long version){this(id,date,kind,name,enabled,version,null,null,null,null,null,null);}
    }
    public record CalendarWrite(Long expectedVersion,LocalDate date,String kind,String name,Boolean enabled) {}
    public record CalendarDay(LocalDate date,int schedules,int attended) {}
    public record CalendarPage(List<CalendarEvent> items,List<CalendarDay> days,String timezone,List<com.rpb.reservation.workforce.scheduling.restswap.RestSwapDtos.Link> restSwaps) {
        public CalendarPage(List<CalendarEvent> items,List<CalendarDay> days,String timezone){this(items,days,timezone,List.of());}
    }
}
