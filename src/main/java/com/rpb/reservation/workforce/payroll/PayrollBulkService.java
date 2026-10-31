package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;

/** Bounded sequential orchestration. Each child command commits independently and can be replayed. */
@Service
public class PayrollBulkService {
    private final PayrollBatchService batches;private final PayrollProgressRepository repository;
    private final WorkforceAccess gate;private final PayrollTransactions transactions;
    public PayrollBulkService(PayrollBatchService batches,PayrollProgressRepository repository,WorkforceAccess gate,PlatformTransactionManager manager){
        this.batches=batches;this.repository=repository;this.gate=gate;this.transactions=new PayrollTransactions(manager);
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public BulkResult execute(CurrentActor actor,UUID store,UUID command,BulkWrite raw){
        if(command==null)throw WorkforceException.invalid();var payload=validate(raw);
        gate.enter(actor,store);
        return repository.withOperationLock(actor.tenantId(),command,()->executeLocked(actor,store,command,payload));
    }
    private BulkResult executeLocked(CurrentActor actor,UUID store,UUID command,BulkWrite payload){
        var prior=transactions.write(()->{gate.enter(actor,store);return repository.prepare(actor.tenantId(),command,store,actor.actorId(),payload);});
        if(prior!=null){authorize(actor,store,payload,prior);return prior;}
        var results=new ArrayList<BulkItem>();
        for(var entry:payload.entries()){
            try {
                var detail=batches.action(actor,store,entry.batchId(),childCommand(actor.tenantId(),command,entry.batchId()),new ActionWrite(entry.expectedVersion(),payload.action(),payload.note()));
                results.add(new BulkItem(entry.batchId(),"success",null,detail.batch()));
            }catch(WorkforceException e){results.add(new BulkItem(entry.batchId(),"failed",e.code(),null));}
            // Infrastructure/unknown failures intentionally escape: a retry resumes deterministic children.
        }
        var response=new BulkResult(command,payload.action(),List.copyOf(results));
        var saved=transactions.write(()->{gate.enter(actor,store);return repository.complete(actor.tenantId(),command,store,actor.actorId(),payload,response);});
        authorize(actor,store,payload,saved);return saved;
    }
    private void authorize(CurrentActor actor,UUID store,BulkWrite payload,BulkResult response){
        gate.enter(actor,store);
        for(var item:response.results())if("success".equals(item.status())){
            var entry=payload.entries().stream().filter(e->e.batchId().equals(item.batchId())).findFirst().orElseThrow(WorkforceException::conflict);
            // Replaying the original child also authorizes stores retained only in its historical receipt.
            batches.action(actor,store,item.batchId(),childCommand(actor.tenantId(),response.commandId(),item.batchId()),new ActionWrite(entry.expectedVersion(),payload.action(),payload.note()));
        }
    }
    static UUID childCommand(UUID tenant,UUID parent,UUID batch){return UUID.nameUUIDFromBytes(("workforce.pay.bulk.child:"+tenant+":"+parent+":"+batch).getBytes(StandardCharsets.UTF_8));}
    static BulkWrite validate(BulkWrite raw){
        if(raw==null||!Set.of("calculate","calculate_full","confirm").contains(raw.action()==null?"":raw.action())||raw.entries()==null||raw.entries().isEmpty()||raw.entries().size()>20)throw WorkforceException.invalid();
        var ids=new HashSet<UUID>();for(var entry:raw.entries())if(entry==null||entry.batchId()==null||entry.expectedVersion()==null||entry.expectedVersion()<1||!ids.add(entry.batchId()))throw WorkforceException.invalid();
        return new BulkWrite(raw.action(),PayrollValues.text(raw.note(),500,true),List.copyOf(raw.entries()));
    }
}
