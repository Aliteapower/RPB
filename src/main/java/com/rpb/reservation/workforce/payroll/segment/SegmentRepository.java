package com.rpb.reservation.workforce.payroll.segment;
import static com.rpb.reservation.workforce.payroll.segment.SegmentDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.PayrollDtos.Payment;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository public class SegmentRepository {
 public record State(UUID id,UUID tenant,UUID company,UUID employee,YearMonth month,LocalDate from,LocalDate cutoff,String status,long version,Write request,Calculation calculation){}
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public SegmentRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public String hash(Object x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.write(x).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public void lock(UUID tenant,UUID company,UUID employee,YearMonth month){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.segment:"+tenant+":"+company+":"+employee+":"+month);}
 public void commandLock(UUID tenant,UUID command){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.segment.command:"+tenant+":"+command);}
 private State read(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new State(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("company_id",UUID.class),r.getObject("employee_id",UUID.class),YearMonth.from(r.getObject("period_month",LocalDate.class)),r.getObject("from_date",LocalDate.class),r.getObject("cutoff",LocalDate.class),r.getString("status"),r.getLong("version"),json.read(r.getString("request_json"),Write.class),r.getString("snapshot")==null?null:json.read(r.getString("snapshot"),Calculation.class));}
 public Optional<State> find(UUID tenant,UUID id){return jdbc.query("select * from workforce_pay_segments where tenant_id=? and id=?",this::read,tenant,id).stream().findFirst();}
 public List<State> month(UUID tenant,UUID company,YearMonth month){return jdbc.query("select * from workforce_pay_segments where tenant_id=? and company_id=? and period_month=? order by employee_id,cutoff,id",this::read,tenant,company,month.atDay(1));}
 public List<State> formalYear(UUID tenant,UUID company,UUID employee,int year){return jdbc.query("select * from workforce_pay_segments where tenant_id=? and company_id=? and employee_id=? and period_month>=? and period_month<? and status in ('confirmed','published') order by period_month,cutoff,id",this::read,tenant,company,employee,LocalDate.of(year,1,1),LocalDate.of(year+1,1,1));}
 public List<State> formal(UUID tenant,UUID company,YearMonth month){return month(tenant,company,month).stream().filter(s->formal(s.status())).toList();}
 public static boolean formal(String status){return Set.of("confirmed","published").contains(status);}
 public List<State> mine(UUID tenant,UUID employee,int year){return jdbc.query("select * from workforce_pay_segments where tenant_id=? and employee_id=? and extract(year from period_month)=? and status='published' order by period_month desc,cutoff desc",this::read,tenant,employee,year);}
 public Optional<State> latest(UUID tenant,UUID company,UUID employee,YearMonth month){return formal(tenant,company,month).stream().filter(s->s.employee().equals(employee)).max(Comparator.comparing(State::cutoff));}
 public void create(CurrentActor a,UUID id,Write w,LocalDate from){jdbc.update("insert into workforce_pay_segments(id,tenant_id,company_id,employee_id,period_month,from_date,cutoff,status,version,request_json,created_by,updated_by) values(?,?,?,?,?,?,?,'draft',1,?::jsonb,?,?)",id,a.tenantId(),w.companyId(),w.employeeId(),YearMonth.parse(w.month()).atDay(1),from,w.cutoff(),json.write(w),a.actorId(),a.actorId());}
 public void update(CurrentActor a,State s,String status,Write w,Calculation c){if(jdbc.update("update workforce_pay_segments set status=?,version=version+1,request_json=?::jsonb,snapshot=?::jsonb,updated_by=?,updated_at=now() where tenant_id=? and id=? and version=?",status,json.write(w),c==null?null:json.write(c),a.actorId(),a.tenantId(),s.id(),s.version())!=1)throw WorkforceException.conflict();}
 public void claim(State s){for(var d=s.from();!d.isAfter(s.cutoff());d=d.plusDays(1))jdbc.update("insert into workforce_pay_segment_days(tenant_id,company_id,employee_id,covered_date,segment_id) values(?,?,?,?,?)",s.tenant(),s.company(),s.employee(),d,s.id());}
 public Optional<Detail> replay(CurrentActor a,UUID store,UUID id,UUID cmd,Action w){return jdbc.query("select segment_id,store_id,actor_id,request_json,response_json from workforce_pay_segment_commands where tenant_id=? and command_id=?",(r,n)->{if(!id.equals(r.getObject(1,UUID.class))||!store.equals(r.getObject(2,UUID.class))||!a.actorId().equals(r.getObject(3,UUID.class))||!w.equals(json.read(r.getString(4),Action.class)))throw WorkforceException.conflict();return json.read(r.getString(5),Detail.class);},a.tenantId(),cmd).stream().findFirst();}
 public void command(CurrentActor a,UUID store,UUID id,UUID cmd,Action w,Detail d){jdbc.update("insert into workforce_pay_segment_commands(tenant_id,command_id,segment_id,store_id,actor_id,request_json,response_json) values(?,?,?,?,?,?::jsonb,?::jsonb)",a.tenantId(),cmd,id,store,a.actorId(),json.write(w),json.write(d));}
 public List<Payment> payments(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_segment_payments where tenant_id=? and segment_id=? order by created_at,id",(r,n)->json.read(r.getString(1),Payment.class),tenant,id);}
 public Optional<Payment> payment(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_segment_payments where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),Payment.class),tenant,id).stream().findFirst();}
 public void payment(State s,Payment p){jdbc.update("insert into workforce_pay_segment_payments(id,tenant_id,segment_id,snapshot) values(?,?,?,?::jsonb)",p.id(),s.tenant(),s.id(),json.write(p));}
}
