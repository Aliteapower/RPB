package com.rpb.reservation.workforce.scheduling.rules;
import com.rpb.reservation.workforce.application.WorkforceJson;import com.rpb.reservation.workforce.domain.WorkforceException;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class RuleTemplateRepository {
 public record Template(UUID id,String name,boolean enabled,long version,AttendanceRules rules){}
 private final JdbcTemplate jdbc;private final WorkforceJson json;public RuleTemplateRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public List<Template> list(UUID tenant){return jdbc.query("select * from workforce_attendance_rule_templates where tenant_id=? order by name,id",(r,n)->new Template(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("enabled"),r.getLong("version"),json.read(r.getString("rules"),AttendanceRules.class)),tenant);}
 public void lock(UUID tenant,UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.rule-template:"+tenant+":"+id);}
 public void save(UUID tenant,Template t){int count=jdbc.update("insert into workforce_attendance_rule_templates(id,tenant_id,name,rules,enabled,version) values(?,?,?,?::jsonb,?,?) on conflict(id) do update set name=excluded.name,rules=excluded.rules,enabled=excluded.enabled,version=excluded.version,updated_at=now() where workforce_attendance_rule_templates.tenant_id=excluded.tenant_id",t.id(),tenant,t.name(),json.write(t.rules()),t.enabled(),t.version());if(count!=1)throw WorkforceException.conflict();}
}
