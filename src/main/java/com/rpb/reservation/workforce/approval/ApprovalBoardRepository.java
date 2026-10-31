package com.rpb.reservation.workforce.approval;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import java.time.LocalDate;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
@Repository
public class ApprovalBoardRepository {
 public record Subject(UUID id,UUID accountId,UUID positionId,String positionName,boolean manager){}
 private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
 public ApprovalBoardRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 public Map<UUID,Subject> subjects(UUID tenant,List<UUID> ids){
  var result=new HashMap<UUID,Subject>();if(ids.isEmpty())return result;
  var rows=named.query("""
   select e.id,e.account_id,e.position_id,p.name,
    (exists(select 1 from auth_account_roles r join auth_accounts a on a.id=r.account_id where a.tenant_id=e.tenant_id and a.id=e.account_id and r.deleted_at is null and r.role_code in ('tenant_admin','store_manager'))
     or exists(select 1 from workforce_administrators w where w.tenant_id=e.tenant_id and w.account_id=e.account_id and w.employee_manage=true)) manager
   from workforce_employees e left join workforce_positions p on p.id=e.position_id and p.tenant_id=e.tenant_id
   where e.tenant_id=:tenant and e.id in (:ids)
   """,new MapSqlParameterSource("tenant",tenant).addValue("ids",ids),(r,n)->new Subject(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getBoolean(5)));
  rows.forEach(s->result.put(s.id(),s));return result;
 }
 public List<UUID> companySchedules(UUID tenant,UUID company,LocalDate from,LocalDate to){return jdbc.queryForList("select id from workforce_schedules where tenant_id=? and company_id=? and deleted_at is null and status='scheduled' and work_date between ? and ? order by work_date,id limit 50001",UUID.class,tenant,company,from,to);}
 public boolean ownsStore(UUID tenant,UUID store){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from stores where tenant_id=? and id=?)",Boolean.class,tenant,store));}
 public String storeName(UUID tenant,UUID store){return jdbc.queryForObject("select display_name from stores where tenant_id=? and id=?",String.class,tenant,store);}
}
