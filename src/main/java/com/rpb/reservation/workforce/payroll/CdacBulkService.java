package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.CdacBulk.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

/** Batch eligibility orchestration; individual salary values and history are copied, never rewritten. */
@Service @Transactional(readOnly=true)
public class CdacBulkService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollConfigurationRepository configs;private final WorkforceEmployeeRepository employees;private final PayrollRepository payroll;private final CdacBulkRepository commands;private final WorkforceAudit audit;private final WorkforceJson json;
 public CdacBulkService(WorkforceAccess gate,PayrollAccess access,PayrollConfigurationRepository configs,WorkforceEmployeeRepository employees,PayrollRepository payroll,CdacBulkRepository commands,WorkforceAudit audit,WorkforceJson json){this.gate=gate;this.access=access;this.configs=configs;this.employees=employees;this.payroll=payroll;this.commands=commands;this.audit=audit;this.json=json;}
 private Selection normalize(Selection c){
  if(c==null||c.companyId()==null||c.employeeIds()==null||c.employeeIds().isEmpty()||c.employeeIds().size()>500||c.employeeIds().stream().anyMatch(Objects::isNull)||c.cdac()==null||!Set.of("applicable","exempt","manual").contains(c.cdac().status()==null?"":c.cdac().status()))throw WorkforceException.invalid();
  var month=PayrollValues.month(c.month());String ref=PayrollValues.text(c.cdac().reference(),500,!c.cdac().status().equals("applicable"));
  return new Selection(c.companyId(),month.toString(),c.employeeIds().stream().distinct().sorted().toList(),new CdacProfile(c.cdac().status(),ref));
 }
 public Preview preview(CurrentActor actor,UUID store,Selection raw){var c=normalize(raw);access.enter(actor,store,c.companyId());return prepare(actor,c).preview();}
 private record Prepared(Preview preview,Map<UUID,String> baselines){}
 private Prepared prepare(CurrentActor actor,Selection c){
  var date=YearMonth.parse(c.month()).atDay(1);var plans=configs.profiles(actor.tenantId(),c.companyId());var rows=new ArrayList<Row>();var tokens=new ArrayList<Object>();var baselines=new HashMap<UUID,String>();
  for(var id:c.employeeIds()){
   access.employee(actor,c.companyId(),id);var employee=employees.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
   var history=plans.stream().filter(p->p.employeeId().equals(id)).toList();var source=history.stream().filter(p->!p.effectiveFrom().isAfter(date)).max(Comparator.comparing(PayProfile::effectiveFrom)).orElse(null);
   var next=history.stream().map(PayProfile::effectiveFrom).filter(d->d.isAfter(date)).min(Comparator.naturalOrder()).orElse(null);var confirmed=commands.latestConfirmed(actor.tenantId(),c.companyId(),id);
   String status="ready",reason="";
   if(!commands.eligible(actor.tenantId(),c.companyId(),id,YearMonth.parse(c.month()))){status="blocked";reason="EMPLOYEE_NOT_EFFECTIVE";}
   else if(source==null){status="blocked";reason="PROFILE_MISSING";}
   else if(confirmed!=null&&!confirmed.isBefore(date)){status="blocked";reason="CONFIRMED_MONTH";}
   else if(Objects.equals(source.cdac(),c.cdac())){status="unchanged";reason="UNCHANGED";}
   else if(history.stream().anyMatch(p->!p.effectiveFrom().isBefore(date)&&YearMonth.from(p.effectiveFrom()).equals(YearMonth.from(date)))){status="blocked";reason="TARGET_MONTH_EXISTS";}
   else try{CdacPayroll.profile(c.cdac(),source.cpf(),source.rule());}catch(WorkforceException e){status="blocked";reason=source.rule().items().stream().anyMatch(i->i.kind().equals("cdac"))&&!c.cdac().status().equals("manual")?"CDAC_DUPLICATE":"CDAC_PROFILE_INVALID";}
   var row=new Row(id,employee.name()+" · "+employee.employeeNo(),source==null?null:source.id(),source==null?null:source.effectiveFrom(),source==null||source.cdac()==null?"legacy":source.cdac().status(),next,status,reason);rows.add(row);
   var template=source==null?null:configs.findTemplate(actor.tenantId(),source.templateId()).orElse(null);var document=source==null||source.cpf().prDocumentId()==null?null:configs.prDocument(actor.tenantId(),id,source.cpf().prDocumentId()).orElse(null);
   var token=Arrays.asList(employee,history,confirmed,template,document);tokens.add(token);baselines.put(id,hash(json.write(Arrays.asList(c.companyId(),c.month(),c.cdac(),token,row))));
  }
  String fingerprint=hash(json.write(Arrays.asList(c,tokens,rows)));int ready=(int)rows.stream().filter(r->r.status().equals("ready")).count(),unchanged=(int)rows.stream().filter(r->r.status().equals("unchanged")).count();
  return new Prepared(new Preview(fingerprint,c.month(),List.copyOf(rows),ready,unchanged,rows.size()-ready-unchanged),Map.copyOf(baselines));
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED) public Result apply(CurrentActor actor,UUID store,UUID command,Write raw){
  if(command==null||raw==null||raw.fingerprint()==null||!raw.fingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();var c=normalize(raw.selection());access.enter(actor,store,c.companyId());for(var id:c.employeeIds())access.employee(actor,c.companyId(),id);
  String reason=PayrollValues.text(raw.reason(),1000,false);var write=new Write(c,raw.fingerprint(),reason.isEmpty()?null:reason);var transaction=commands.transaction();
  var replay=transaction.execute(tx->{commands.lock(command);var completed=commands.replay(actor,store,command,write);if(completed.isPresent())return completed.get();if(reason.isEmpty())throw WorkforceException.invalid();commands.claim(actor,store,command,write);return null;});if(replay!=null)return replay;
  var prepared=transaction.execute(tx->prepare(actor,c));if(!prepared.preview().fingerprint().equals(raw.fingerprint()))throw new WorkforceException("WORKFORCE_CDAC_BULK_CHANGED",409);if(prepared.preview().ready()==0)throw WorkforceException.invalid();
  var results=new ArrayList<RequestResult>();for(var row:prepared.preview().rows())if(row.status().equals("ready")){
   RequestResult result;
   try{result=transaction.execute(tx->{commands.lock(UUID.nameUUIDFromBytes((command+":"+row.employeeId()).getBytes(StandardCharsets.UTF_8)));var done=commands.item(command,row.employeeId());if(done.isPresent())return done.get();gate.sourceWrite(actor);
    var requests=commands.changes();requests.lock("subject:"+actor.tenantId()+":"+c.companyId()+":"+row.employeeId());configs.lockDefaults(actor.tenantId(),c.companyId());employees.lock(row.employeeId());var individual=prepare(actor,new Selection(c.companyId(),c.month(),List.of(row.employeeId()),c.cdac()));var current=individual.preview().rows().getFirst();if(!current.status().equals("ready")||!Objects.equals(individual.baselines().get(row.employeeId()),prepared.baselines().get(row.employeeId())))throw new WorkforceException("WORKFORCE_CDAC_BULK_CHANGED",409);
    // Never merge/submit another open aggregate whose complete proposal was not in this preview.
    var date=YearMonth.parse(c.month()).atDay(1);if(requests.open(actor.tenantId(),c.companyId(),row.employeeId(),null).stream().anyMatch(r->r.effectiveDate().equals(date)))throw WorkforceException.conflict();
    var old=configs.findProfile(actor.tenantId(),row.sourceProfileId()).orElseThrow(WorkforceException::missing);var id=UUID.nameUUIDFromBytes((command+":"+row.employeeId()).getBytes(StandardCharsets.UTF_8));var pubs=commands.publications();var clock=Clock.systemUTC();var service=new com.rpb.reservation.workforce.payroll.governance.PayChangeService(gate,access,requests,configs,pubs,employees,audit,new com.rpb.reservation.workforce.payroll.governance.PayChangeDrafts(requests,configs,pubs,employees,clock),clock);
    var profile=new ProfileWrite(0L,c.companyId(),old.employeeId(),date,old.templateId(),old.rule(),old.cpf(),old.payment(),c.cdac());var r=service.save(actor,store,id,new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Write(0L,c.companyId(),old.employeeId(),date,null,profile,List.of(),null,write.reason()));r=service.action(actor,store,r.id(),new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Action(UUID.nameUUIDFromBytes((id+":submit").getBytes(StandardCharsets.UTF_8)),r.revision(),r.hash(),"submit",""));
    var item=new RequestResult(row.employeeId(),r.id(),r.status(),r.staleReason());commands.item(command,item);return item;});
   }catch(WorkforceException e){result=new RequestResult(row.employeeId(),null,e.code().equals("WORKFORCE_CDAC_BULK_CHANGED")?"stale":"failed",e.code());var failed=result;transaction.execute(tx->{commands.item(command,failed);return null;});}catch(RuntimeException e){result=new RequestResult(row.employeeId(),null,"failed","WORKFORCE_PAYROLL_CHANGE_FAILED");var failed=result;transaction.execute(tx->{commands.item(command,failed);return null;});}
   results.add(result);
  }
  var result=new Result(prepared.preview(),List.of(),List.copyOf(results));return transaction.execute(tx->{commands.lock(command);var prior=commands.replay(actor,store,command,write);if(prior.isPresent())return prior.get();commands.save(actor,store,command,write,result);return result;});
 }
 private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
