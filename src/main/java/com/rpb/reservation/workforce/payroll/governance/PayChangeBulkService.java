package com.rpb.reservation.workforce.payroll.governance;

import static com.rpb.reservation.workforce.payroll.governance.PayChangeBulkDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.PayrollProfileValidation;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** No surrounding transaction: failed items roll back independently of completed decisions. */
@Service public class PayChangeBulkService {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PayChangeBulkService.class);
 private final WorkforceAccess access;private final PayChangeService changes;private final TransactionTemplate itemTransaction;
 public PayChangeBulkService(WorkforceAccess access,PayChangeService changes,PlatformTransactionManager manager){this.access=access;this.changes=changes;itemTransaction=new TransactionTemplate(manager);itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
 public Response action(CurrentActor actor,UUID store,Command command){
  access.enter(actor,store);if(command==null||command.companyId()==null||command.items()==null||command.items().isEmpty()||command.items().size()>100)throw WorkforceException.invalid();
  var month=command.month()==null||command.month().isBlank()?null:PayrollProfileValidation.month(command.month());
  var ids=new HashSet<UUID>();for(var item:command.items())if(item!=null&&item.requestId()!=null&&!ids.add(item.requestId()))throw WorkforceException.invalid();
  var results=new ArrayList<Result>();
  for(var item:command.items()){
   try{
    var r=itemTransaction.execute(tx->{
     if(item==null||item.requestId()==null||!Set.of("approve","reject").contains(item.action()==null?"":item.action()))throw WorkforceException.invalid();
     var request=changes.detail(actor,store,item.requestId());
     if(!request.companyId().equals(command.companyId()))throw WorkforceException.forbidden();
     if(!PayChangeMonthFilter.matches(request,month))throw WorkforceException.conflict();
     return changes.action(actor,store,item.requestId(),new PayChangeDtos.Action(item.commandId(),item.expectedRevision(),item.expectedHash(),item.action(),item.note()));
    });
    results.add(new Result(item.requestId(),item.commandId(),r.status().equals("stale")?"stale":"success",r,null,200));
   }catch(WorkforceException e){results.add(failed(item,e.code(),e.status()));}
   catch(RuntimeException e){LOG.warn("Payroll bulk decision failed for request {}",item==null?null:item.requestId(),e);results.add(failed(item,"WORKFORCE_PAYROLL_BULK_ITEM_FAILED",500));}
  }
  return new Response(List.copyOf(results));
 }
 private Result failed(Item item,String code,int status){return new Result(item==null?null:item.requestId(),item==null?null:item.commandId(),"failed",null,code,status);}
}
