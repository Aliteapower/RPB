package com.rpb.reservation.workforce.scheduling;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ShiftService {
    private final WorkforceAccess access;private final SchedulingRepository repository;private final WorkforceAudit audit;
    public ShiftService(WorkforceAccess access,SchedulingRepository repository,WorkforceAudit audit){this.access=access;this.repository=repository;this.audit=audit;}
    public List<Shift> list(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);return repository.shifts(actor.tenantId(),store);}
    @Transactional public Shift save(CurrentActor actor,UUID store,UUID id,ShiftWrite c){
        access.enter(actor,store);access.manage(actor,store);var next=validate(id,c);if(next.rules()!=null&&next.rules().sourceTemplateId()!=null){long source=repository.ruleTemplateVersion(actor.tenantId(),next.rules().sourceTemplateId());if(source==0)throw WorkforceException.missing();if(next.rules().sourceTemplateVersion()>source)throw WorkforceException.conflict();}repository.lock("workforce.shift:"+id);
        var old=repository.shift(actor.tenantId(),store,id).orElse(null);
        if(old!=null&&c.expectedVersion()==0&&withVersion(next,old.version()).equals(old))return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        repository.saveShift(actor.tenantId(),store,next);audit.changed(actor,store,"shift.save",id,old,next);return next;
    }
    private Shift withVersion(Shift s,long v){return new Shift(s.id(),s.name(),s.startTime(),s.endTime(),s.nextDay(),s.breakMode(),s.breakMinutes(),s.breakAfterMinutes(),s.breakStartOffset(),s.breakEndOffset(),s.breakPaid(),s.enabled(),v,s.rules(),s.startNextDay());}
    private Shift validate(UUID id,ShiftWrite c){
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.name()==null||c.name().isBlank()||c.name().trim().length()>80||c.nextDay()==null||c.breakPaid()==null||c.enabled()==null||!Set.of("none","duration","window","manual").contains(c.breakMode()==null?"":c.breakMode()))throw WorkforceException.invalid();
        LocalTime start,end;try{if(!c.startTime().matches("\\d{2}:\\d{2}")||!c.endTime().matches("\\d{2}:\\d{2}"))throw WorkforceException.invalid();start=LocalTime.parse(c.startTime());end=LocalTime.parse(c.endTime());}catch(Exception e){throw WorkforceException.invalid();}
        long length=Duration.between(start,end).toMinutes()+(c.nextDay()?1440:0);if(length<=0||length>1440)throw WorkforceException.invalid();
        if(c.rules()!=null)com.rpb.reservation.workforce.scheduling.rules.AttendanceRules.validate(c.rules(),length);
        int minutes=c.rules()==null?0:Objects.requireNonNullElse(c.breakMinutes(),0),after=c.rules()==null?0:Objects.requireNonNullElse(c.breakAfterMinutes(),0),from=c.rules()==null?0:Objects.requireNonNullElse(c.breakStartOffset(),0),to=c.rules()==null?0:Objects.requireNonNullElse(c.breakEndOffset(),0);
        if(c.rules()==null&&"duration".equals(c.breakMode())){minutes=c.breakMinutes()==null?0:c.breakMinutes();after=c.breakAfterMinutes()==null?0:c.breakAfterMinutes();if(minutes<=0||after<minutes||after>length)throw WorkforceException.invalid();}
        if(c.rules()==null&&"window".equals(c.breakMode())){from=c.breakStartOffset()==null?-1:c.breakStartOffset();to=c.breakEndOffset()==null?-1:c.breakEndOffset();if(from<0||to<=from||to>length)throw WorkforceException.invalid();}
        return new Shift(id,c.name().trim(),c.startTime(),c.endTime(),c.nextDay(),c.breakMode(),minutes,after,from,to,c.breakPaid(),c.enabled(),c.expectedVersion()+1,c.rules(),Boolean.TRUE.equals(c.startNextDay())?true:null);
    }
}




