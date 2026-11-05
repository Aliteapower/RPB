package com.rpb.reservation.workforce.calendar;

import static com.rpb.reservation.workforce.calendar.CalendarDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarRepository {
    private final JdbcTemplate jdbc;
    public CalendarRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<com.rpb.reservation.workforce.scheduling.restswap.RestSwapDtos.Link> restSwaps(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID employee){return new com.rpb.reservation.workforce.scheduling.restswap.RestSwapRepository(jdbc).list(tenant,store,from,to,employee);}
    public List<CalendarEvent> list(UUID tenant,UUID store,LocalDate from,LocalDate to){var events=new ArrayList<>(jdbc.query("select * from workforce_calendar_events where tenant_id=? and store_id=? and event_date between ? and ? order by event_date,kind,id",this::map,tenant,store,from,to));events.addAll(new com.rpb.reservation.workforce.calendar.annual.HolidayYearRepository(jdbc).events(tenant,from,to));events.sort(Comparator.comparing(CalendarEvent::date).thenComparing(CalendarEvent::kind).thenComparing(CalendarEvent::id));return events;}
    public Optional<CalendarEvent> find(UUID tenant,UUID store,UUID id){return jdbc.query("select * from workforce_calendar_events where tenant_id=? and store_id=? and id=?",this::map,tenant,store,id).stream().findFirst();}
    public List<CalendarDay> days(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID employee){return jdbc.query("""
        select d.day::date work_date,count(distinct s.id)::int schedules,count(distinct a.id)::int attended
        from generate_series(?::date,?::date,interval '1 day') d(day)
        left join workforce_schedules s on s.work_date=d.day::date and s.tenant_id=? and s.store_id=? and s.status='scheduled' and (?::uuid is null or s.employee_id=?::uuid)
        left join workforce_attendance_sessions a on a.schedule_id=s.id and a.tenant_id=s.tenant_id
        group by d.day order by d.day
        """,(r,n)->new CalendarDay(r.getObject("work_date",LocalDate.class),r.getInt("schedules"),r.getInt("attended")),from,to,tenant,store,employee,employee);}
    public void save(UUID tenant,UUID store,CalendarEvent e){
        int changed=jdbc.update("""
            insert into workforce_calendar_events(id,tenant_id,store_id,event_date,kind,name,enabled,version) values(?,?,?,?,?,?,?,?)
            on conflict(id) do update set event_date=excluded.event_date,kind=excluded.kind,name=excluded.name,enabled=excluded.enabled,version=excluded.version,updated_at=now()
            where workforce_calendar_events.tenant_id=excluded.tenant_id and workforce_calendar_events.store_id=excluded.store_id
            """,e.id(),tenant,store,e.date(),e.kind(),e.name(),e.enabled(),e.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
    private CalendarEvent map(ResultSet r,int n)throws SQLException{return new CalendarEvent(r.getObject("id",UUID.class),r.getObject("event_date",LocalDate.class),r.getString("kind"),r.getString("name"),r.getBoolean("enabled"),r.getLong("version"));}
}
