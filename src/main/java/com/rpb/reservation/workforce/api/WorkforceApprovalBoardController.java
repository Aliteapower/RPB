package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.approval.*;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import java.time.*;import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceApprovalBoardController extends WorkforceEndpointSupport {
 private final ApprovalBoardService board;private final ApprovalBatchService batches;private final ApprovalReadinessService readiness;
 public WorkforceApprovalBoardController(CurrentActorProvider actors,ApprovalBoardService board,ApprovalBatchService batches,ApprovalReadinessService readiness){super(actors);this.board=board;this.batches=batches;this.readiness=readiness;}
 @GetMapping("/approval-board") public Board board(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="all")String category,@RequestParam(required=false)UUID positionId,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){return board.board(actor(),storeId,new Filter(from,to,positionId,search,category),limit,offset);}
 @PutMapping("/approval-board/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Selection c){return board.preview(actor(),storeId,c);}
 @PutMapping("/approval-board/commit") public Result commit(@PathVariable UUID storeId,@RequestBody Commit c){return batches.commit(actor(),storeId,c);}
 @GetMapping("/approval-readiness") public Readiness readiness(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam YearMonth month){return readiness.check(actor(),storeId,companyId,month);}
}
