package com.rpb.reservation.workforce.calendar;

import static com.rpb.reservation.workforce.calendar.CalendarDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CalendarService {
    private final WorkforceAccess access;
    private final AttendanceAccess attendanceAccess;
    private final SchedulingRepository schedules;
    private final CalendarRepository repository;
    private final WorkforceAudit audit;
    public CalendarService(WorkforceAccess access,AttendanceAccess attendanceAccess,SchedulingRepository schedules,CalendarRepository repository,WorkforceAudit audit){this.access=access;this.attendanceAccess=attendanceAccess;this.schedules=schedules;this.repository=repository;this.audit=audit;}
    public CalendarPage list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
        access.enter(actor,store);ScheduleService.range(from,to);
        UUID employee=attendanceAccess.manager(actor,store)?null:attendanceAccess.self(actor,store).id();
        return new CalendarPage(repository.list(actor.tenantId(),store,from,to),repository.days(actor.tenantId(),store,from,to,employee),schedules.timezone(actor.tenantId(),store),repository.restSwaps(actor.tenantId(),store,from,to,employee));
    }
    @Transactional public CalendarEvent save(CurrentActor actor,UUID store,UUID id,CalendarWrite c){
        access.sourceWrite(actor);
        access.enter(actor,store);access.manage(actor,store);
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.date()==null||!Set.of("public_holiday","store_rest").contains(c.kind()==null?"":c.kind())||c.name()==null||c.name().isBlank()||c.name().trim().length()>100||c.enabled()==null)throw WorkforceException.invalid();
        schedules.lock("workforce.calendar:"+actor.tenantId()+":"+store);
        var old=repository.find(actor.tenantId(),store,id).orElse(null);
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.date().equals(c.date())&&old.kind().equals(c.kind())&&old.name().equals(c.name().trim())&&old.enabled()==c.enabled())return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        var next=new CalendarEvent(id,c.date(),c.kind(),c.name().trim(),c.enabled(),c.expectedVersion()+1);
        repository.save(actor.tenantId(),store,next);audit.changed(actor,store,"calendar.save",id,old,next);return next;
    }
}
