package com.rpb.reservation.workforce.calendar.annual;
import java.time.*;import java.util.*;
public final class HolidayDtos {
 private HolidayDtos(){}
 public record Holiday(UUID id,LocalDate date,String name,String englishName,LocalDate observedOn,String observedKind,Boolean enabled,Boolean tentative){}
 public record YearPage(int year,long version,List<Holiday> items){}
 public record Write(Long expectedVersion,List<Holiday> items){}
 public record Template(int year,String source,List<Holiday> items){}
 public record MonthPage(String month,List<LocalDate> dates,String fingerprint){}
 public record MonthWrite(String fingerprint,List<LocalDate> dates){}
 public record Stored(Holiday holiday,long version,boolean deleted){}
}
