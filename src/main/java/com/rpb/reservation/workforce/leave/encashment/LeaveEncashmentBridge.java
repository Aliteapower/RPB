package com.rpb.reservation.workforce.leave.encashment;

import static com.rpb.reservation.workforce.leave.encashment.LeaveEncashmentDtos.*;
import com.rpb.reservation.workforce.leave.account.LeaveBalanceEngine;
import com.rpb.reservation.workforce.compliance.ComplianceRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.PayrollDtos.Item;
import com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Request;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.springframework.stereotype.Component;
import java.util.*;

/** All callers hold the existing workforce source lock and employee lock in the same transaction. */
@Component
public class LeaveEncashmentBridge {
    private final LeaveEncashmentRepository repo;
    private final LeaveBalanceEngine balances;
    private final ComplianceRepository policies;
    public LeaveEncashmentBridge(LeaveEncashmentRepository repo,LeaveBalanceEngine balances,ComplianceRepository policies) {
        this.repo=repo; this.balances=balances; this.policies=policies;
    }
    public boolean isEncashment(Item i) { return i.eventEvidence()!=null && "leave_encashment".equals(i.eventEvidence().businessType()); }
    public List<Item> items(Request r) {
        var items=new ArrayList<Item>();
        r.content().months().forEach(m->items.addAll(m.after().items()));
        if(r.content().supplemental()!=null)r.content().supplemental().forEach(p->items.addAll(p.items()));
        return items.stream().filter(this::isEncashment).toList();
    }
    public void validate(UUID tenant,UUID company,UUID employee,List<Item> items,boolean approved) {
        var seen=new HashSet<UUID>();
        for(var i:items)if(isEncashment(i)) {
            var d=repo.find(tenant,i.eventEvidence().eventId()).orElseThrow(()->new WorkforceException("LEAVE_ENCASHMENT_SOURCE_REQUIRED",409));
            if(!seen.add(d.id())||!d.companyId().equals(company)||!d.employeeId().equals(employee)||!d.item().equals(i)
                    || !(approved?Set.of("approved","consumed"):Set.of("reserved","approved","consumed")).contains(d.status()))
                throw new WorkforceException("LEAVE_ENCASHMENT_SOURCE_CHANGED",409);
        }
    }
    public void releaseRemoved(CurrentActor a,Request r) {if(r.employeeId()==null)return;var current=items(r).stream().map(i->i.eventEvidence().eventId()).collect(java.util.stream.Collectors.toSet());for(var d:repo.list(a.tenantId(),r.employeeId()))if(r.id().equals(d.requestId())&&d.status().equals("reserved")&&!current.contains(d.id())){balances.transition(a,d.id(),"released","Removed from wage-change draft");repo.save(a.tenantId(),state(d,"released",null));}}
    public void approved(CurrentActor a,Request r) {
        validate(a.tenantId(),r.companyId(),r.employeeId(),items(r),false);
        for(var i:items(r)) {
            var d=repo.find(a.tenantId(),i.eventEvidence().eventId()).orElseThrow();
            if(!d.status().equals("reserved"))continue;
            if(!r.id().equals(d.requestId())||a.actorId().equals(d.creatorId()))throw WorkforceException.forbidden();
            if(!d.status().equals("reserved"))throw WorkforceException.conflict();
            repo.save(a.tenantId(),state(d,"approved",null));
        }
    }
    public void rejected(CurrentActor a,Request r) {
        for(var i:items(r)) {
            var d=repo.find(a.tenantId(),i.eventEvidence().eventId()).orElseThrow();
            if(r.id().equals(d.requestId())&&d.status().equals("reserved")) {
                balances.transition(a,d.id(),"released","Encashment request rejected");
                repo.save(a.tenantId(),state(d,"released",null));
            }
        }
    }
    public void consume(CurrentActor a,UUID company,UUID employee,UUID batch,String channel,List<Item> items) {
        validate(a.tenantId(),company,employee,items,true);
        for(var i:items)if(isEncashment(i)) {
            var id=i.eventEvidence().eventId(); policies.lock(a.tenantId(),"encashment:"+id);
            var d=repo.find(a.tenantId(),id).orElseThrow();
            if(d.status().equals("consumed")) {
                if(!batch.equals(d.consumedBy()))throw new WorkforceException("LEAVE_ENCASHMENT_ALREADY_CONSUMED",409);
                continue;
            }
            if(!d.request().route().equals(channel)&&!(d.request().route().equals("monthly")&&channel.equals("segment")))throw WorkforceException.conflict();
            repo.consume(a.tenantId(),id,batch,channel,a.actorId());
            balances.transition(a,id,"used","Formal payroll "+channel+" "+batch);
            repo.save(a.tenantId(),state(d,"consumed",batch));
        }
    }
    public void assertVoidable(UUID tenant,UUID batch) {
        if(repo.consumed(tenant,batch))throw new WorkforceException("LEAVE_ENCASHMENT_FORMAL_CORRECTION_REQUIRED",409);
    }
    public Document state(Document d,String state,UUID consumed) {
        return new Document(d.id(),d.employeeId(),d.companyId(),d.accountId(),d.creatorId(),d.request(),d.valuation(),d.item(),state,d.requestId(),consumed);
    }
}
