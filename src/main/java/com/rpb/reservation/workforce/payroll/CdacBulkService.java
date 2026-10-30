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
 private record Prepared(Preview preview,Map<UUID,PayProfile> sources){}
 private Prepared prepare(CurrentActor actor,Selection c){
  var date=YearMonth.parse(c.month()).atDay(1);var plans=configs.profiles(actor.tenantId(),c.companyId());var rows=new ArrayList<Row>();var tokens=new ArrayList<Object>();var sources=new HashMap<UUID,PayProfile>();
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
   if(source!=null)sources.put(id,source);
   rows.add(new Row(id,employee.name()+" · "+employee.employeeNo(),source==null?null:source.id(),source==null?null:source.effectiveFrom(),source==null||source.cdac()==null?"legacy":source.cdac().status(),next,status,reason));
   tokens.add(Arrays.asList(employee,history,confirmed));
  }
  String fingerprint=hash(json.write(Arrays.asList(c,tokens,rows)));int ready=(int)rows.stream().filter(r->r.status().equals("ready")).count(),unchanged=(int)rows.stream().filter(r->r.status().equals("unchanged")).count();
  return new Prepared(new Preview(fingerprint,c.month(),List.copyOf(rows),ready,unchanged,rows.size()-ready-unchanged),sources);
 }
 @Transactional public Result apply(CurrentActor actor,UUID store,UUID command,Write raw){
  if(command==null||raw==null||raw.fingerprint()==null||!raw.fingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();var c=normalize(raw.selection());access.enter(actor,store,c.companyId());gate.sourceWrite(actor);commands.lock(command);var write=new Write(c,raw.fingerprint());var replay=commands.replay(actor,store,command,write);if(replay.isPresent())return replay.get();
  for(var id:c.employeeIds()){access.employee(actor,c.companyId(),id);employees.lock(id);}
  var prepared=prepare(actor,c);var preview=prepared.preview();if(!preview.fingerprint().equals(raw.fingerprint()))throw new WorkforceException("WORKFORCE_CDAC_BULK_CHANGED",409);if(preview.ready()==0)throw WorkforceException.invalid();
  var saved=new ArrayList<UUID>();for(var row:preview.rows())if(row.status().equals("ready")){
   var old=prepared.sources().get(row.employeeId());var id=UUID.nameUUIDFromBytes((command+":"+row.employeeId()).getBytes(StandardCharsets.UTF_8));
   var next=new PayProfile(id,c.companyId(),old.employeeId(),old.employeeName(),YearMonth.parse(c.month()).atDay(1),old.templateId(),old.templateVersion(),old.rule(),old.cpf(),1,old.payment(),c.cdac());
   configs.saveProfile(actor.tenantId(),next);audit.changed(actor,store,"pay-profile.cdac-bulk",id,old,next);saved.add(id);
  }
  var result=new Result(preview,List.copyOf(saved));commands.save(actor,store,command,write,result);return result;
 }
 private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
