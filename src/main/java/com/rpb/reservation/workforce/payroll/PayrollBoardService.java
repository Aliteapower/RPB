package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollBoardDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PayrollBoardService {
 private final PayrollAccess access;private final PayrollRepository repository;private final PayrollSources sources;private final PayrollCalculationService calculator;private final Clock clock;
 public PayrollBoardService(PayrollAccess access,PayrollRepository repository,PayrollSources sources,PayrollCalculationService calculator,Clock clock){this.access=access;this.repository=repository;this.sources=sources;this.calculator=calculator;this.clock=clock;}
 public Board read(CurrentActor actor,UUID store,UUID company,String value){
  access.enter(actor,store,company);var month=PayrollValues.month(value);var batch=repository.forMonth(actor.tenantId(),company,month).orElse(null);
  var saved=batch==null?null:batch.calculation();if(saved!=null)access.enter(actor,store,company,PayrollViews.stores(saved));
  boolean frozen=batch!=null&&Set.of("confirmed","published").contains(batch.status());
  var rows=new ArrayList<Row>();var issues=new LinkedHashSet<String>();boolean changed=false;
  if(frozen){
   issues.addAll(saved.issues());for(var line:saved.lines())rows.add(row(line,line.positionName(),line.issues(),true,List.of(),null));
  }else{
   var capture=sources.capture(actor.tenantId(),company,month,batch==null?List.of():repository.inputs(actor.tenantId(),batch.id()));access.enter(actor,store,company,capture.stores());
   var validation=calculator.calculate(capture,month);issues.addAll(validation.issues());changed=saved!=null&&!saved.sourceFingerprint().equals(capture.fingerprint());
   var validLines=new HashMap<UUID,PayrollLine>();validation.lines().forEach(l->validLines.put(l.employeeId(),l));
   var employment=repository.employmentSources(actor.tenantId(),company,capture.lines().stream().map(l->l.employee().id()).toList(),month).periods();
   var savedLines=new HashMap<UUID,PayrollLine>();if(saved!=null)saved.lines().forEach(l->savedLines.put(l.employeeId(),l));
   for(var source:capture.lines()){
    var approvals=source.sources().stream().filter(r->r.evidence()!=null&&!"current".equals(r.evidence().approvalState())).map(r->new ApprovalTarget(r.scheduleId(),r.storeId(),r.workDate(),r.evidence().approvalState())).toList();
    var periods=employment.getOrDefault(source.employee().id(),List.of());var suggested=periods.size()==1?periods.getFirst().from():null;
    var check=validLines.get(source.employee().id());var line=savedLines.get(source.employee().id());
    if(line==null){var p=source.profile();rows.add(new Row(source.employee().id(),source.employee().name(),source.employee().employeeNo(),source.employee().jobTitle(),p==null?null:p.rule().basis(),p==null?null:p.id(),null,null,null,check.issues(),null,null,approvals,suggested));}
    else rows.add(row(line,source.employee().jobTitle(),check.issues(),!changed&&check.issues().isEmpty(),approvals,suggested));
   }
  }
  var totals=saved!=null&&!changed&&issues.isEmpty()?PayBreakdown.of(saved):null;
  var formalSegments=repository.segmentFormal(actor.tenantId(),company,month);var segmentCurrent=new HashMap<UUID,PayrollSources.CapturedLine>();if(!formalSegments.isEmpty()){var all=sources.captureIncludingSegments(actor.tenantId(),company,month);access.enter(actor,store,company,all.stores());all.lines().forEach(l->segmentCurrent.put(l.employee().id(),l));}
  var segments=formalSegments.stream().map(s->{access.enter(actor,store,company,PayrollViews.stores(s.calculation().cumulative()));return new SegmentSummary(s.id(),s.employee(),s.calculation().cumulative().lines().getFirst().employeeName(),s.from(),s.cutoff(),s.status(),s.calculation().delta().gross(),s.calculation().delta().net(),com.rpb.reservation.workforce.payroll.segment.SegmentSourceChanges.changed(s,segmentCurrent.get(s.employee())));}).toList();
  return new Board(company,month.toString(),batch==null?null:batch.id(),batch==null?null:batch.version(),batch==null?null:batch.status(),frozen,changed,clock.instant(),List.copyOf(issues),List.copyOf(rows),totals,segments);
 }
 private static Row row(PayrollLine line,String position,List<String> issues,boolean usable,List<ApprovalTarget> approvals,LocalDate suggested){
  var p=line.profile();var breakdown=usable?PayBreakdown.of(line):null;
  String deductions=breakdown==null||breakdown.employeeCpf()==null||breakdown.cdac()==null||breakdown.otherDeductions()==null?null:new BigDecimal(breakdown.employeeCpf()).add(new BigDecimal(breakdown.cdac())).add(new BigDecimal(breakdown.otherDeductions())).setScale(2).toPlainString();
  return new Row(line.employeeId(),line.employeeName(),line.employeeNo(),position,p==null?null:p.rule().basis(),p==null?null:p.id(),breakdown==null?null:breakdown.gross(),deductions,breakdown==null?null:breakdown.net(),List.copyOf(issues),usable?line:null,breakdown,approvals,suggested);
 }
}
