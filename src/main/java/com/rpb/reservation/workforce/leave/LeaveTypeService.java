package com.rpb.reservation.workforce.leave;

import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.SchedulingRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class LeaveTypeService {
    private final WorkforceAccess access;
    private final SchedulingRepository locks;
    private final LeaveTypeRepository repository;
    private final WorkforceAudit audit;
    public LeaveTypeService(WorkforceAccess access,SchedulingRepository locks,LeaveTypeRepository repository,WorkforceAudit audit){this.access=access;this.locks=locks;this.repository=repository;this.audit=audit;}
    public List<LeaveType> list(CurrentActor actor,UUID store){access.enter(actor,store);return repository.list(actor.tenantId(),store);}
    @Transactional public LeaveType save(CurrentActor actor,UUID store,UUID id,LeaveTypeWrite c){
        access.enter(actor,store);access.manage(actor,store);
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.name()==null||c.name().isBlank()||c.name().trim().length()>100||c.paid()==null||c.enabled()==null)throw WorkforceException.invalid();
        locks.lock("workforce.leave-type:"+id);
        var old=repository.find(actor.tenantId(),store,id).orElse(null);
        if(old!=null&&(c.expectedVersion()==0||old.version()==c.expectedVersion()+1)&&old.name().equals(c.name().trim())&&old.paid()==c.paid()&&old.enabled()==c.enabled())return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        var next=new LeaveType(id,c.name().trim(),c.paid(),c.enabled(),c.expectedVersion()+1);
        repository.save(actor.tenantId(),store,next);audit.changed(actor,store,"leave-type.save",id,old,next);return next;
    }
}
