package com.rpb.reservation.workforce.payroll;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
import static com.rpb.reservation.workforce.payroll.CdacBulk.*;
@Repository public class CdacBulkRepository {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public CdacBulkRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public org.springframework.transaction.support.TransactionTemplate transaction(){var t=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));t.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);return t;}
 public com.rpb.reservation.workforce.payroll.governance.PayChangeRepository changes(){return new com.rpb.reservation.workforce.payroll.governance.PayChangeRepository(jdbc,json);}
 public com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository publications(){return new com.rpb.reservation.workforce.payroll.governance.TemplatePublicationRepository(jdbc,json);}
 public void claim(CurrentActor actor,UUID store,UUID command,Write c){jdbc.update("insert into workforce_cdac_bulk_attempts(id,tenant_id,company_id,store_id,actor_id,payload) values(?,?,?,?,?,?::jsonb) on conflict(id) do nothing",command,actor.tenantId(),c.selection().companyId(),store,actor.actorId(),json.write(c));boolean exact=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_cdac_bulk_attempts where id=? and tenant_id=? and company_id=? and store_id=? and actor_id=? and payload=?::jsonb)",Boolean.class,command,actor.tenantId(),c.selection().companyId(),store,actor.actorId(),json.write(c)));if(!exact)throw WorkforceException.conflict();}
 public Optional<RequestResult> item(UUID command,UUID employee){return jdbc.query("select result from workforce_cdac_bulk_items where command_id=? and employee_id=?",(r,n)->json.read(r.getString(1),RequestResult.class),command,employee).stream().findFirst();}
 public void item(UUID command,RequestResult result){jdbc.update("insert into workforce_cdac_bulk_items(command_id,tenant_id,company_id,employee_id,result) select id,tenant_id,company_id,?,?::jsonb from workforce_cdac_bulk_attempts where id=? on conflict(command_id,employee_id) do nothing",result.employeeId(),json.write(result),command);}
 public void lock(UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.cdac.bulk:"+id);}
 public boolean eligible(UUID tenant,UUID company,UUID employee,YearMonth month){return Boolean.TRUE.equals(jdbc.queryForObject("""
  select exists(select 1 from workforce_employees e where e.tenant_id=? and e.id=? and (
   (e.hire_date<=? and (e.leave_date is null or e.leave_date>=?) and exists(
    select 1 from (select h.company_id,h.effective_date,coalesce(lead(h.effective_date) over(order by h.effective_date,h.version),date '9999-12-31') next_date
     from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id) h where h.company_id=? and h.effective_date<=? and h.next_date>?))
   or exists(select 1 from workforce_schedules s where s.tenant_id=e.tenant_id and s.employee_id=e.id and s.company_id=? and s.work_date between ? and ? and s.status='scheduled')))
  """,Boolean.class,tenant,employee,month.atEndOfMonth(),month.atDay(1),company,month.atEndOfMonth(),month.atDay(1),company,month.atDay(1),month.atEndOfMonth()));}
 public LocalDate latestConfirmed(UUID tenant,UUID company,UUID employee){return jdbc.queryForObject("""
  select max(period_month) from workforce_pay_batches b where tenant_id=? and company_id=? and status in ('confirmed','published')
  and exists(select 1 from jsonb_array_elements(coalesce(b.calculation->'lines','[]'::jsonb)) l where l->>'employeeId'=? or l->'profile'->>'employeeId'=?)
  """,LocalDate.class,tenant,company,employee.toString(),employee.toString());}
 public Optional<Result> replay(CurrentActor actor,UUID store,UUID id,Write c){return jdbc.query("select tenant_id,company_id,store_id,actor_id,payload,response from workforce_cdac_bulk_commands where id=?",(r,n)->{
  if(!actor.tenantId().equals(r.getObject(1,UUID.class))||!c.selection().companyId().equals(r.getObject(2,UUID.class))||!store.equals(r.getObject(3,UUID.class))||!actor.actorId().equals(r.getObject(4,UUID.class))||!c.equals(json.read(r.getString(5),Write.class)))throw WorkforceException.conflict();return json.read(r.getString(6),Result.class);
 },id).stream().findFirst();}
 public void save(CurrentActor actor,UUID store,UUID id,Write c,Result response){jdbc.update("insert into workforce_cdac_bulk_commands(id,tenant_id,company_id,store_id,actor_id,payload,response) values(?,?,?,?,?,?::jsonb,?::jsonb)",id,actor.tenantId(),c.selection().companyId(),store,actor.actorId(),json.write(c),json.write(response));}
}
