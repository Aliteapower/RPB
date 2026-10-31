package com.rpb.reservation.workforce.documents.expiry;
import static com.rpb.reservation.workforce.documents.expiry.DocumentExpiryDtos.*;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.core.namedparam.*;import org.springframework.stereotype.Repository;
@Repository
public class DocumentExpiryRepository {
 private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
 public DocumentExpiryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 public String timezone(UUID tenant,UUID store){return jdbc.queryForList("select timezone from stores where tenant_id=? and id=? and status='active' and deleted_at is null",String.class,tenant,store).stream().findFirst().orElseThrow(com.rpb.reservation.workforce.domain.WorkforceException::missing);}
 private String scope(){
  return """
   from workforce_employee_documents d
   join workforce_employees e on e.id=d.employee_id and e.tenant_id=d.tenant_id
   join workforce_document_types t on t.id=d.type_id and t.tenant_id=d.tenant_id
   where (:employee::uuid is null or e.id=:employee::uuid) and d.tenant_id=:tenant and d.deleted_at is null and d.expires_on is not null and d.expires_on<=:until
   and exists(select 1 from workforce_work_stores w where w.tenant_id=e.tenant_id and w.employee_id=e.id and w.store_id=:store and w.ended_on is null)
   and not exists(select 1 from workforce_work_stores w where w.tenant_id=e.tenant_id and w.employee_id=e.id and w.ended_on is null and w.store_id not in (:managed))
   """;
 }
 public Page list(UUID tenant,UUID store,Set<UUID> managed,String timezone,LocalDate today,int days,String status,int limit,int offset){return list(tenant,store,managed,timezone,today,days,status,limit,offset,null);}
 public Page list(UUID tenant,UUID store,Set<UUID> managed,String timezone,LocalDate today,int days,String status,int limit,int offset,UUID employee){
  var params=new MapSqlParameterSource("tenant",tenant).addValue("employee",employee).addValue("store",store).addValue("managed",managed.stream().sorted().toList()).addValue("today",today).addValue("until",today.plusDays(days)).addValue("status",status).addValue("limit",limit).addValue("offset",offset);
  String scope=scope();
  var counts=named.queryForMap("select count(*) filter(where d.expires_on>=:today) as due,count(*) filter(where d.expires_on<:today) as expired "+scope,params);
  long due=((Number)counts.get("due")).longValue(),expired=((Number)counts.get("expired")).longValue();
  String selected=" and (:status='all' or (:status='expired' and d.expires_on<:today) or (:status='due' and d.expires_on>=:today)) ";
  var rows=named.query("select d.id,e.id,e.employee_no,e.name,t.name,d.expires_on "+scope+selected+" order by d.expires_on,e.employee_no,e.id,d.id limit :limit offset :offset",params,(r,n)->{var date=r.getObject(6,LocalDate.class);return new Item(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),date,ChronoUnit.DAYS.between(today,date),date.isBefore(today)?"expired":"due");});
  return new Page(store,timezone,today,days,status,due,expired,status.equals("all")?due+expired:status.equals("due")?due:expired,limit,offset,rows);
 }
 public EmployeePage employeeCounts(UUID tenant,UUID store,Set<UUID> managed,String timezone,LocalDate today,int days,List<UUID> ids){
  var params=new MapSqlParameterSource("tenant",tenant).addValue("employee",null).addValue("store",store).addValue("managed",managed.stream().sorted().toList()).addValue("today",today).addValue("until",today.plusDays(days)).addValue("ids",ids);
  var rows=named.query("select e.id,count(*) filter(where d.expires_on>=:today),count(*) filter(where d.expires_on<:today) "+scope()+" and e.id in (:ids) group by e.id order by e.id",params,(r,n)->new EmployeeCount(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3)));
  return new EmployeePage(store,timezone,today,days,rows);
 }
}
