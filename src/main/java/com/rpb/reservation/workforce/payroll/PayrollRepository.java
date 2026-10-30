package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.sql.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class PayrollRepository {
 public record State(UUID id,UUID tenant,UUID company,String companyName,YearMonth month,String status,long version,PayrollSnapshot calculation,UUID createdBy,UUID confirmedBy,Instant createdAt){}
 public record CompanyInfo(String name,String registrationNo,int payDay,boolean enabled,long version){}
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public PayrollRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public Optional<PayrollComputation.State> computation(UUID tenant,UUID batch){return new PayrollComputationRepository(jdbc,json).find(tenant,batch);}
 public void computation(State batch,PayrollComputation.State state){new PayrollComputationRepository(jdbc,json).save(batch,state);}
 /** Only the adjacent previous month is a comparison baseline; gaps are never silently bridged. */
 public Optional<State> previous(UUID tenant,UUID company,YearMonth month){return jdbc.query("select * from workforce_pay_batches where tenant_id=? and company_id=? and period_month=? and status in ('confirmed','published') order by created_at desc,id limit 1",this::state,tenant,company,month.minusMonths(1).atDay(1)).stream().findFirst();}
 public com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy statutoryPolicy(UUID tenant,YearMonth month){return new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json).resolve(tenant,month);}
 public void lock(UUID tenant,UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.pay.batch:"+tenant+":"+id);}
 public void lockSources(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.payroll.source:"+tenant);}
 public Optional<State> find(UUID tenant,UUID id){return jdbc.query("select * from workforce_pay_batches where tenant_id=? and id=?",this::state,tenant,id).stream().findFirst();}
 private State state(ResultSet r,int n)throws SQLException {String c=r.getString("calculation");return new State(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("company_id",UUID.class),r.getString("company_name"),YearMonth.from(r.getObject("period_month",LocalDate.class)),r.getString("status"),r.getLong("version"),c==null?null:json.read(c,PayrollSnapshot.class),r.getObject("created_by",UUID.class),r.getObject("confirmed_by",UUID.class),r.getTimestamp("created_at").toInstant());}
 public List<State> list(UUID tenant,UUID company,LocalDate earliest){return jdbc.query("select * from workforce_pay_batches where tenant_id=? and company_id=? and period_month>=? order by period_month desc,created_at desc limit 101",this::state,tenant,company,earliest);}
 public void create(UUID tenant,UUID company,UUID id,String name,YearMonth month,UUID actor,Instant now){jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,created_by,created_at) values(?,?,?,?,?,'draft',1,?,?)",id,tenant,company,name,month.atDay(1),actor,Timestamp.from(now));}
 public CompanyInfo company(UUID tenant,UUID company){return jdbc.query("select e.display_name,coalesce(c.registration_no,''),coalesce(c.pay_day,7),coalesce(c.enabled,true),coalesce(c.version,0) from operating_entities e left join workforce_company_settings c on c.tenant_id=e.tenant_id and c.company_id=e.id where e.tenant_id=? and e.id=?",(r,n)->new CompanyInfo(r.getString(1),r.getString(2),r.getInt(3),r.getBoolean(4),r.getLong(5)),tenant,company).stream().findFirst().orElseThrow(WorkforceException::missing);}
 public List<UUID> employeeIds(UUID tenant,UUID company,YearMonth month){return jdbc.queryForList("""
  select e.id from workforce_employees e where e.tenant_id=? and (
   (e.hire_date<=? and (e.leave_date is null or e.leave_date>=?) and exists(
    select 1 from (select h.company_id,h.effective_date,coalesce(lead(h.effective_date) over(order by h.effective_date,h.version),date '9999-12-31') next_date
      from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id) h where h.company_id=? and h.effective_date<=? and h.next_date>?))
   or exists(select 1 from workforce_schedules s where s.tenant_id=e.tenant_id and s.employee_id=e.id and s.company_id=? and s.work_date between ? and ? and s.status='scheduled'))
  order by e.employee_no,e.id limit 501
  """,UUID.class,tenant,month.atEndOfMonth(),month.atDay(1),company,month.atEndOfMonth(),month.atDay(1),company,month.atDay(1),month.atEndOfMonth());}
 public List<UUID> scheduleIds(UUID tenant,UUID company,LocalDate from,LocalDate to){return jdbc.queryForList("select id from workforce_schedules where tenant_id=? and company_id=? and work_date between ? and ? order by work_date,start_at,id limit 10001",UUID.class,tenant,company,from,to);}
 public LocalDate employmentStart(UUID tenant,UUID company,UUID employee,YearMonth month){return jdbc.query("""
  select greatest(h.effective_date,?::date) from
  (select company_id,effective_date,coalesce(lead(effective_date) over(order by effective_date,version),date '9999-12-31') next_date from workforce_company_history where tenant_id=? and employee_id=?) h
  where h.company_id=? and h.effective_date<=? and h.next_date>? order by h.effective_date limit 1
  """,(r,n)->r.getObject(1,LocalDate.class),month.atDay(1),tenant,employee,company,month.atEndOfMonth(),month.atDay(1)).stream().findFirst().orElse(month.atDay(1));}
 public record EmploymentPeriod(LocalDate from,LocalDate to){}
 public List<EmploymentPeriod> employmentPeriods(UUID tenant,UUID company,UUID employee,YearMonth month){return jdbc.query("""
  select greatest(e.hire_date,h.effective_date,?::date) first_day,
   least(coalesce(e.leave_date,?::date),h.next_date-1,?::date) last_day
  from workforce_employees e cross join lateral
   (select company_id,effective_date,coalesce(lead(effective_date) over(order by effective_date,version),date '9999-12-31') next_date
    from workforce_company_history where tenant_id=e.tenant_id and employee_id=e.id) h
  where e.tenant_id=? and e.id=? and h.company_id=?
   and greatest(e.hire_date,h.effective_date,?::date)<=least(coalesce(e.leave_date,?::date),h.next_date-1,?::date)
  order by first_day
  """,(r,n)->new EmploymentPeriod(r.getObject(1,LocalDate.class),r.getObject(2,LocalDate.class)),month.atDay(1),month.atEndOfMonth(),month.atEndOfMonth(),tenant,employee,company,month.atDay(1),month.atEndOfMonth(),month.atEndOfMonth());}
 /** Both historical company-start and hire/leave-clipped periods are retained: they have distinct semantics. */
 public record EmploymentSources(Map<UUID,LocalDate> starts,Map<UUID,List<EmploymentPeriod>> periods){}
 public EmploymentSources employmentSources(UUID tenant,UUID company,List<UUID> employees,YearMonth month){
  if(employees.isEmpty())return new EmploymentSources(Map.of(),Map.of());
  var args=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("tenant",tenant).addValue("company",company).addValue("employees",employees).addValue("from",month.atDay(1)).addValue("to",month.atEndOfMonth());
  var starts=new HashMap<UUID,LocalDate>();var periods=new HashMap<UUID,List<EmploymentPeriod>>();
  new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
   select e.id,greatest(h.effective_date,:from::date) company_start,
    greatest(e.hire_date,h.effective_date,:from::date) first_day,
    least(coalesce(e.leave_date,:to::date),h.next_date-1,:to::date) last_day
   from workforce_employees e cross join lateral
    (select company_id,effective_date,coalesce(lead(effective_date) over(order by effective_date,version),date '9999-12-31') next_date
     from workforce_company_history where tenant_id=e.tenant_id and employee_id=e.id) h
   where e.tenant_id=:tenant and e.id in (:employees) and h.company_id=:company
    and h.effective_date<=:to and h.next_date>:from
   order by e.id,h.effective_date
   """,args,r->{var id=r.getObject(1,UUID.class);starts.putIfAbsent(id,r.getObject(2,LocalDate.class));var first=r.getObject(3,LocalDate.class);var last=r.getObject(4,LocalDate.class);if(!first.isAfter(last))periods.computeIfAbsent(id,k->new ArrayList<>()).add(new EmploymentPeriod(first,last));});
  return new EmploymentSources(starts,periods);
 }
 public Set<UUID> singaporeStores(UUID tenant,Collection<UUID> stores){
  if(stores.isEmpty())return Set.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(stores);
  return new HashSet<>(jdbc.queryForList("select id from stores where tenant_id=? and id in ("+String.join(",",Collections.nCopies(stores.size(),"?"))+") and currency='SGD' and timezone='Asia/Singapore'",UUID.class,args.toArray()));
 }
 public boolean singaporeStore(UUID tenant,UUID store){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from stores where tenant_id=? and id=? and currency='SGD' and timezone='Asia/Singapore')",Boolean.class,tenant,store));}
 public List<UUID> companyStores(UUID tenant,UUID company){return jdbc.queryForList("select id from stores where tenant_id=? and operating_entity_id=? and status='active' and deleted_at is null order by id",UUID.class,tenant,company);}
 public List<LineInput> inputs(UUID tenant,UUID batch){return jdbc.query("select snapshot from workforce_pay_inputs where tenant_id=? and batch_id=? order by employee_id",(r,n)->json.read(r.getString(1),LineInput.class),tenant,batch);}
 public void input(State b,LineInput i){jdbc.update("insert into workforce_pay_inputs(batch_id,tenant_id,company_id,employee_id,snapshot,version) values(?,?,?,?,?::jsonb,?) on conflict(batch_id,employee_id) do update set snapshot=excluded.snapshot,version=excluded.version,updated_at=now() where workforce_pay_inputs.tenant_id=excluded.tenant_id and workforce_pay_inputs.company_id=excluded.company_id",b.id(),b.tenant(),b.company(),i.employeeId(),json.write(i),i.version());}
 public void update(State b,String status,PayrollSnapshot calculation,UUID actor,Instant now){jdbc.update("""
  update workforce_pay_batches set status=?,version=version+1,calculation=?::jsonb,
   confirmed_by=case when ?='confirmed' then ? else confirmed_by end,confirmed_at=case when ?='confirmed' then ? else confirmed_at end,
   published_by=case when ?='published' then ? else published_by end,published_at=case when ?='published' then ? else published_at end,updated_at=now()
  where tenant_id=? and id=? and version=?
  """,status,calculation==null?null:json.write(calculation),status,actor,status,Timestamp.from(now),status,actor,status,Timestamp.from(now),b.tenant(),b.id(),b.version());}
 public Optional<BatchDetail> replay(UUID tenant,UUID batch,UUID id,UUID actor,ActionWrite c){return jdbc.query("select batch_id,actor_id,payload,response from workforce_pay_commands where tenant_id=? and id=?",(r,n)->{if(!r.getObject(1,UUID.class).equals(batch)||!r.getObject(2,UUID.class).equals(actor)||!json.read(r.getString(3),ActionWrite.class).equals(c))throw WorkforceException.conflict();return json.read(r.getString(4),BatchDetail.class);},tenant,id).stream().findFirst();}
 public void command(UUID tenant,UUID batch,UUID id,UUID actor,ActionWrite c,BatchDetail response){jdbc.update("insert into workforce_pay_commands(id,tenant_id,batch_id,actor_id,action,payload,response) values(?,?,?,?,?,?::jsonb,?::jsonb)",id,tenant,batch,actor,c.action(),json.write(c),json.write(response));}
 public List<Payment> payments(UUID tenant,UUID batch){return jdbc.query("select * from workforce_pay_payments where tenant_id=? and batch_id=? order by created_at,id",(r,n)->new Payment(r.getObject("id",UUID.class),r.getObject("employee_id",UUID.class),r.getBigDecimal("amount").toPlainString(),r.getObject("payment_date",LocalDate.class),r.getString("method"),r.getString("reference"),r.getString("note"),r.getObject("reverses_id",UUID.class),r.getObject("actor_id",UUID.class),r.getTimestamp("created_at").toInstant(),r.getString("bank")),tenant,batch);}
 public Optional<Payment> payment(UUID tenant,UUID id){return jdbc.queryForList("select batch_id from workforce_pay_payments where tenant_id=? and id=?",UUID.class,tenant,id).stream().findFirst().flatMap(batch->payments(tenant,batch).stream().filter(p->p.id().equals(id)).findFirst());}
 public void payment(State b,Payment p){jdbc.update("insert into workforce_pay_payments(id,tenant_id,company_id,batch_id,employee_id,amount,payment_date,method,reference,note,reverses_id,actor_id,created_at,bank) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",p.id(),b.tenant(),b.company(),b.id(),p.employeeId(),new java.math.BigDecimal(p.amount()),p.paymentDate(),p.method(),p.reference(),p.note(),p.reversesId(),p.actorId(),Timestamp.from(p.createdAt()),p.bank());}
 public List<State> published(UUID tenant,UUID employee,int year){return jdbc.query("select b.* from workforce_pay_batches b where b.tenant_id=? and b.status='published' and b.period_month between ? and ? and exists(select 1 from jsonb_array_elements(b.calculation->'lines') l where l->>'employeeId'=?) order by b.period_month desc,b.id",this::state,tenant,LocalDate.of(year,1,1),LocalDate.of(year,12,31),employee.toString());}
}
