package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Complete fresh source read plus authorized historical projections. This service never freezes or writes facts. */
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PayrollProgressService {
    private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollRepository repository;
    private final PayrollSources sources;private final PayrollIncremental incremental;
    public PayrollProgressService(WorkforceAccess gate,PayrollAccess access,PayrollRepository repository,PayrollSources sources,PayrollCalculationService calculator){
        this.gate=gate;this.access=access;this.repository=repository;this.sources=sources;this.incremental=new PayrollIncremental(calculator);
    }
    public ChangeReport changes(CurrentActor actor,UUID store,UUID id){
        gate.enter(actor,store);var batch=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        access.enter(actor,store,batch.company(),PayrollViews.stores(batch.calculation()));
        var computation=repository.computation(actor.tenantId(),id).orElse(null);
        var cache=computation==null?null:computation.cache();
        if(cache!=null)access.enter(actor,store,batch.company(),PayrollViews.stores(cache.snapshot()));
        if(computation!=null){
            if(computation.comparisonStores()==null&&computation.lastCalculation()!=null&&computation.lastCalculation().available())throw WorkforceException.forbidden();
            access.enter(actor,store,batch.company(),computation.comparisonStores()==null?Set.of():computation.comparisonStores());
        }
        var capture=sources.capture(actor.tenantId(),batch.company(),batch.month(),repository.inputs(actor.tenantId(),id));
        access.enter(actor,store,batch.company(),capture.stores());
        var previous=repository.previous(actor.tenantId(),batch.company(),batch.month()).orElse(null);
        if(previous!=null)access.enter(actor,store,previous.company(),PayrollViews.stores(previous.calculation()));
        var last=computation==null||computation.lastCalculation()==null?PayrollChanges.unavailable(batch.month().toString()):PayrollChanges.readOnly(computation.lastCalculation());
        var prior=PayrollChanges.compare(previous==null?null:previous.calculation(),previous==null?null:previous.version(),batch.calculation());
        if(!prior.available())prior=PayrollChanges.unavailable(batch.month().minusMonths(1).toString());
        var baseline=batch.calculation()!=null?batch.calculation():cache==null?null:cache.snapshot();
        boolean sourceChanged=baseline!=null&&!Objects.equals(baseline.sourceFingerprint(),capture.fingerprint());
        var pending=new ArrayList<PendingChange>();
        if(Set.of("draft","calculated").contains(batch.status())){
            var current=incremental.cache(capture,batch.month(),baseline);var names=new LinkedHashMap<UUID,String>();
            if(cache!=null&&cache.snapshot()!=null)cache.snapshot().lines().forEach(l->names.put(l.employeeId(),l.employeeName()));
            capture.lines().forEach(l->names.put(l.employee().id(),l.employee().name()));
            var ids=current.lineKeys().keySet();var old=cache==null||cache.lineKeys()==null?Set.<UUID>of():cache.lineKeys().keySet();
            boolean global=cache!=null&&(!PayrollIncremental.ENGINE_KEY.equals(cache.engineKey())||!Objects.equals(cache.globalKey(),current.globalKey()));
            for(var changed:incremental.changed(capture,batch.month(),cache)){
                String reason=cache==null||cache.snapshot()==null?"baseline_unavailable":!ids.contains(changed)?"removed":!old.contains(changed)?"added":global?"global":"source";
                pending.add(new PendingChange(changed,names.getOrDefault(changed,""),reason));
            }
        }
        return new ChangeReport(id,batch.version(),batch.status(),sourceChanged,last,prior,List.copyOf(pending),computation==null?0:computation.recalculated(),computation==null?0:computation.reused());
    }
}
