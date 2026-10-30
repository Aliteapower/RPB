package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class ApprovalBatchService {
 private final WorkforceAccess gate;private final ApprovalService approvals;private final TransactionTemplate itemTransaction;
 public ApprovalBatchService(WorkforceAccess gate,ApprovalService approvals,PlatformTransactionManager transactions){this.gate=gate;this.approvals=approvals;itemTransaction=new TransactionTemplate(transactions);itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
 public Result commit(CurrentActor actor,UUID store,Commit c){
  gate.enter(actor,store);gate.manage(actor,store);if(c==null||c.note()==null||c.note().isBlank()||c.note().trim().length()>500||c.items()==null||c.items().isEmpty()||c.items().size()>100)throw WorkforceException.invalid();var ids=new HashSet<UUID>();
  for(var i:c.items())if(i==null||i.scheduleId()==null||!ids.add(i.scheduleId())||i.expectedVersion()==null||i.expectedVersion()<0||i.sourceFingerprint()==null||!i.sourceFingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();
  var result=new ArrayList<Outcome>();
  for(var i:c.items()){
   try{itemTransaction.execute(status->{var detail=approvals.detail(actor,store,i.scheduleId());var row=detail.row();var old=row.approval();
    boolean replay=old!=null&&old.version()==i.expectedVersion()+1&&old.sourceFingerprint().equals(i.sourceFingerprint())&&old.reviewerId().equals(actor.actorId())&&old.note().equals(c.note().trim())&&(old.decisions()==null||old.decisions().isEmpty());
    if(!Boolean.TRUE.equals(detail.canReview()))throw new WorkforceException(detail.reviewBlock()==null?"WORKFORCE_FORBIDDEN":detail.reviewBlock(),403);
    if(!replay&&!ApprovalBoardService.normal(row))throw new WorkforceException("WORKFORCE_APPROVAL_BLOCKED",409);
    approvals.approve(actor,store,i.scheduleId(),new ApprovalWrite(i.expectedVersion(),i.sourceFingerprint(),c.note().trim(),List.of()));return null;});result.add(new Outcome(i.scheduleId(),true,"OK"));}
   catch(WorkforceException e){result.add(new Outcome(i.scheduleId(),false,e.code()));}
  }
  int succeeded=(int)result.stream().filter(Outcome::success).count();return new Result(result,succeeded,result.size()-succeeded);
 }
}
