package com.rpb.reservation.workforce.payroll.supplemental;

import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.governance.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository public class SupplementalRepository {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public SupplementalRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public String hash(Object value){return new PayChangeRepository(jdbc,json).hash(value);}
 public void lock(UUID tenant,UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.supplemental:"+tenant+":"+id);}
 public void commandLock(UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.supplemental.command:"+id);}
 public Optional<Batch> find(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_supplemental_batches where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),Batch.class),tenant,id).stream().findFirst();}
 public List<Batch> list(UUID tenant,UUID company){return jdbc.query("select snapshot from workforce_supplemental_batches where tenant_id=? and company_id=? order by created_at,id",(r,n)->json.read(r.getString(1),Batch.class),tenant,company);}
 public List<Batch> forMonth(UUID tenant,UUID company,YearMonth month){return jdbc.query("select snapshot from workforce_supplemental_batches where tenant_id=? and company_id=? and origin_month=? and status in ('confirmed','published') order by created_at,id",(r,n)->json.read(r.getString(1),Batch.class),tenant,company,month.atDay(1));}
 public List<Batch> recognised(UUID tenant,UUID company,UUID employee,int year){return jdbc.query("select snapshot from workforce_supplemental_batches where tenant_id=? and company_id=? and employee_id=? and extract(year from origin_month)=? and status in ('confirmed','published') order by created_at,id",(r,n)->json.read(r.getString(1),Batch.class),tenant,company,employee,year);}
 public void save(CurrentActor actor,UUID store,Batch b){jdbc.update("insert into workforce_supplemental_batches(id,tenant_id,company_id,employee_id,store_id,origin_month,settlement_month,status,version,request_id,revision,proposal_id,snapshot,created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(id) do update set status=excluded.status,version=excluded.version,snapshot=excluded.snapshot,updated_at=now() where workforce_supplemental_batches.tenant_id=excluded.tenant_id",b.id(),actor.tenantId(),b.companyId(),b.employeeId(),store,YearMonth.parse(b.originMonth()).atDay(1),YearMonth.parse(b.settlementMonth()).atDay(1),b.status(),b.version(),b.approval().requestId(),b.approval().revision(),b.proposal().id(),json.write(b),actor.actorId());}
 public record Approved(Proposal proposal,PayChangeDtos.Proof proof,LocalDate effectiveDate){}
 public boolean hasProposal(UUID tenant,UUID company,UUID request,long revision,UUID proposal){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_supplemental_batches where tenant_id=? and company_id=? and request_id=? and revision=? and proposal_id=? and status<>'voided')",Boolean.class,tenant,company,request,revision,proposal));}
 public Approved approved(UUID tenant,UUID company,UUID employee,UUID request,long revision,String hash,UUID proposal){
  var r=new PayChangeRepository(jdbc,json).find(tenant,request).orElseThrow(WorkforceException::missing);
  if(!r.companyId().equals(company)||!Objects.equals(r.employeeId(),employee)||!r.status().equals("approved")||r.revision()!=revision||!r.hash().equals(hash)||r.content().supplemental()==null)throw new WorkforceException("SUPPLEMENTAL_APPROVAL_REQUIRED",409);
  var p=r.content().supplemental().stream().filter(v->v.id().equals(proposal)).findFirst().orElseThrow(WorkforceException::missing);
  var proofs=jdbc.query("select snapshot,result from workforce_pay_change_proofs where tenant_id=? and company_id=? and request_id=? and revision=? and hash=? and kind='supplemental' and subject_id=?",(row,n)->{if(!json.read(row.getString(2),Proposal.class).equals(p))throw new WorkforceException("SUPPLEMENTAL_APPROVAL_REQUIRED",409);return json.read(row.getString(1),PayChangeDtos.Proof.class);},tenant,company,request,revision,hash,proposal);
  if(proofs.size()!=1)throw new WorkforceException("SUPPLEMENTAL_APPROVAL_REQUIRED",409);return new Approved(p,proofs.getFirst(),r.effectiveDate());
 }
 public <T> Optional<T> replay(CurrentActor a,UUID store,UUID subject,UUID id,String kind,Object payload,Class<T> type){return jdbc.query("select tenant_id,store_id,actor_id,subject_id,kind,payload,response from workforce_supplemental_commands where id=?",(r,n)->{if(!a.tenantId().equals(r.getObject(1,UUID.class))||!store.equals(r.getObject(2,UUID.class))||!a.actorId().equals(r.getObject(3,UUID.class))||!subject.equals(r.getObject(4,UUID.class))||!kind.equals(r.getString(5))||!json.read(r.getString(6),payload.getClass()).equals(payload))throw WorkforceException.conflict();return json.read(r.getString(7),type);},id).stream().findFirst();}
 public void command(CurrentActor a,UUID store,UUID company,UUID subject,UUID id,String kind,Object payload,Object response){jdbc.update("insert into workforce_supplemental_commands(id,tenant_id,company_id,store_id,actor_id,subject_id,kind,payload,response) values(?,?,?,?,?,?,?,?::jsonb,?::jsonb)",id,a.tenantId(),company,store,a.actorId(),subject,kind,json.write(payload),json.write(response));}
 public List<PayrollDtos.Payment> payments(UUID tenant,UUID batch){return jdbc.query("select snapshot from workforce_supplemental_payments where tenant_id=? and batch_id=? order by created_at,id",(r,n)->json.read(r.getString(1),PayrollDtos.Payment.class),tenant,batch);}
 public void payment(UUID tenant,Batch b,PayrollDtos.Payment p){jdbc.update("insert into workforce_supplemental_payments(id,tenant_id,company_id,batch_id,employee_id,snapshot,reverses_id) values(?,?,?,?,?,?::jsonb,?)",p.id(),tenant,b.companyId(),b.id(),b.employeeId(),json.write(p),p.reversesId());}
 public void consume(UUID tenant,Batch b){var annual=new AnnualWageRepository(jdbc,json,Clock.systemUTC());annual.lock(tenant,b.companyId(),b.employeeId(),YearMonth.parse(b.originMonth()).getYear());annual.subject(tenant,b.companyId(),b.employeeId(),YearMonth.parse(b.originMonth()).getYear());for(var i:b.proposal().items()){
  UUID id=i.eventEvidence().eventId();var event=new AnnualWageDtos.WageEvent(i.eventEvidence(),i.amount(),"supplemental:"+b.id());
  var old=jdbc.query("select snapshot,employee_id from workforce_wage_events where tenant_id=? and company_id=? and event_id=?",(r,n)->{var prior=json.read(r.getString(1),AnnualWageDtos.WageEvent.class);if(!b.employeeId().equals(r.getObject(2,UUID.class))||!prior.evidence().equals(event.evidence())||SupplementalMoney.number(prior.amount()).compareTo(SupplementalMoney.number(event.amount()))!=0||!prior.source().startsWith("supplemental:"))throw new WorkforceException("WAGE_EVENT_ALREADY_CONSUMED",409);return prior;},tenant,b.companyId(),id);
  if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_wage_event_consumptions where tenant_id=? and company_id=? and event_id=? and active)",Boolean.class,tenant,b.companyId(),id)))throw new WorkforceException("WAGE_EVENT_ALREADY_CONSUMED",409);
  if(old.isEmpty())jdbc.update("insert into workforce_wage_events(tenant_id,company_id,event_id,employee_id,year,snapshot) values(?,?,?,?,?,?::jsonb)",tenant,b.companyId(),id,b.employeeId(),YearMonth.parse(b.originMonth()).getYear(),json.write(event));
  jdbc.update("insert into workforce_wage_event_consumptions(tenant_id,company_id,employee_id,event_id,supplemental_id) values(?,?,?,?,?)",tenant,b.companyId(),b.employeeId(),id,b.id());
 }}
 public boolean consumed(UUID tenant,UUID company,UUID event){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_wage_event_consumptions where tenant_id=? and company_id=? and event_id=? and active) or exists(select 1 from workforce_wage_events where tenant_id=? and company_id=? and event_id=? and snapshot->>'source' like 'opening:%')",Boolean.class,tenant,company,event,tenant,company,event));}
 public void release(UUID tenant,UUID batch){jdbc.update("update workforce_wage_event_consumptions set active=false,released_at=now() where tenant_id=? and supplemental_id=? and active",tenant,batch);}
}
