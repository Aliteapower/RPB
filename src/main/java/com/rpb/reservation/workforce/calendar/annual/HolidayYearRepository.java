package com.rpb.reservation.workforce.calendar.annual;
import static com.rpb.reservation.workforce.calendar.annual.HolidayDtos.*;
import com.rpb.reservation.workforce.calendar.CalendarDtos.CalendarEvent;import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class HolidayYearRepository {
 private final JdbcTemplate jdbc;public HolidayYearRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public long version(UUID tenant,int year){return jdbc.queryForList("select version from workforce_holiday_years where tenant_id=? and year=?",Long.class,tenant,year).stream().findFirst().orElse(0L);}
 public List<Stored> rows(UUID tenant,int year,boolean deleted){return jdbc.query("select * from workforce_public_holidays where tenant_id=? and year=? and (? or deleted_at is null) order by holiday_date,name,id",(r,n)->new Stored(new Holiday(r.getObject("id",UUID.class),r.getObject("holiday_date",LocalDate.class),r.getString("name"),r.getString("english_name"),r.getObject("observed_on",LocalDate.class),r.getString("observed_kind"),r.getBoolean("enabled"),r.getBoolean("tentative")),r.getLong("version"),r.getTimestamp("deleted_at")!=null),tenant,year,deleted);}
 public void lock(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.holiday-year:"+tenant);}
 public void lockCalendars(UUID tenant){for(var id:jdbc.queryForList("select id from stores where tenant_id=? order by id",UUID.class,tenant))jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.calendar:"+tenant+":"+id);}
 public void save(UUID tenant,int year,long version,List<Holiday> items){
  var old=rows(tenant,year,true);jdbc.update("insert into workforce_holiday_years(tenant_id,year,version) values(?,?,?) on conflict(tenant_id,year) do update set version=excluded.version,updated_at=now()",tenant,year,version);
  for(var previous:old)if(items.stream().noneMatch(h->h.id().equals(previous.holiday().id()))&&!previous.deleted())jdbc.update("update workforce_public_holidays set deleted_at=now(),version=version+1,updated_at=now() where tenant_id=? and year=? and id=?",tenant,year,previous.holiday().id());
  for(var h:items){var previous=old.stream().filter(p->p.holiday().id().equals(h.id())).findFirst().orElse(null);if(previous!=null&&!previous.deleted()&&previous.holiday().equals(h))continue;long next=previous==null?1:previous.version()+1;int count=jdbc.update("insert into workforce_public_holidays(id,tenant_id,year,holiday_date,name,english_name,observed_on,observed_kind,enabled,tentative,version) values(?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do update set holiday_date=excluded.holiday_date,name=excluded.name,english_name=excluded.english_name,observed_on=excluded.observed_on,observed_kind=excluded.observed_kind,enabled=excluded.enabled,tentative=excluded.tentative,version=excluded.version,deleted_at=null,updated_at=now() where workforce_public_holidays.tenant_id=excluded.tenant_id and workforce_public_holidays.year=excluded.year",h.id(),tenant,year,h.date(),h.name(),h.englishName(),h.observedOn(),h.observedKind(),h.enabled(),h.tentative(),next);if(count!=1)throw WorkforceException.conflict();}
 }
 public List<CalendarEvent> events(UUID tenant,LocalDate from,LocalDate to){
  var result=new ArrayList<CalendarEvent>();for(int year=from.getYear()-1;year<=to.getYear();year++)for(var row:rows(tenant,year,false)){var h=row.holiday();if(!h.date().isBefore(from)&&!h.date().isAfter(to))result.add(event(h.id(),h.date(),h,false,row.version(),year));if(h.observedOn()!=null&&!h.observedOn().isBefore(from)&&!h.observedOn().isAfter(to))result.add(event(UUID.nameUUIDFromBytes(("observed:"+h.id()).getBytes(StandardCharsets.UTF_8)),h.observedOn(),h,true,row.version(),year));}return result;
 }
 private CalendarEvent event(UUID id,LocalDate date,Holiday h,boolean observed,long version,int year){String suffix=observed?(h.observedKind().equals("official")?"（补假）":"（约定补休）"):"";return new CalendarEvent(id,date,"public_holiday",h.name()+suffix,h.enabled(),version,"tenant",h.date(),observed,year,h.englishName()==null||h.englishName().isBlank()?"":h.englishName()+(observed?" (in lieu)":""),h.tentative());}
}

