package com.rpb.reservation.workforce.calendar.annual;
import static com.rpb.reservation.workforce.calendar.annual.HolidayDtos.*;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Versioned, reviewable official dates. Not a lunar-date prediction engine. */
public final class SingaporeHolidayTemplates {
 private SingaporeHolidayTemplates(){}
 public static Template forYear(int year){String[][] rows=switch(year){
 case 2026->new String[][]{{"01-01","元旦","New Year's Day",""},{"02-17","农历新年初一","Chinese New Year (day 1)",""},{"02-18","农历新年初二","Chinese New Year (day 2)",""},{"03-21","开斋节","Hari Raya Puasa",""},{"04-03","耶稣受难日","Good Friday",""},{"05-01","劳动节","Labour Day",""},{"05-27","哈芝节","Hari Raya Haji",""},{"05-31","卫塞节","Vesak Day","06-01"},{"08-09","国庆日","National Day","08-10"},{"11-08","屠妖节","Deepavali","11-09"},{"12-25","圣诞节","Christmas Day",""}};
 case 2027->new String[][]{{"01-01","元旦","New Year's Day",""},{"02-06","农历新年初一","Chinese New Year (day 1)",""},{"02-07","农历新年初二","Chinese New Year (day 2)","02-08"},{"03-10","开斋节","Hari Raya Puasa",""},{"03-26","耶稣受难日","Good Friday",""},{"05-01","劳动节","Labour Day",""},{"05-17","哈芝节","Hari Raya Haji",""},{"05-20","卫塞节","Vesak Day",""},{"08-09","国庆日","National Day",""},{"10-28","屠妖节","Deepavali",""},{"12-25","圣诞节","Christmas Day",""}};
 default->throw com.rpb.reservation.workforce.domain.WorkforceException.missing();};var items=new ArrayList<Holiday>();for(var r:rows){var date=LocalDate.parse(year+"-"+r[0]);var observed=r[3].isEmpty()?null:LocalDate.parse(year+"-"+r[3]);items.add(new Holiday(UUID.nameUUIDFromBytes(("SG:"+year+":"+r[0]+":"+r[2]).getBytes(StandardCharsets.UTF_8)),date,r[1],r[2],observed,observed==null?"none":"official",true,year==2026&&(r[0].equals("03-21")||r[0].equals("05-27"))));}return new Template(year,year==2026?"https://www.mom.gov.sg/newsroom/press-releases/2025/0616-public-holidays-for-2026":"https://www.mom.gov.sg/newsroom/press-releases/2026/0618-public-holidays-for-2027",List.copyOf(items));}
}
