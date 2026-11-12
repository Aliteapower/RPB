package com.rpb.reservation.workforce.statutory;

import static com.rpb.reservation.workforce.statutory.StatutoryPolicies.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatutoryPolicyRepository {
 private final JdbcTemplate jdbc; private final WorkforceJson json;
 public StatutoryPolicyRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public List<Policy> list(UUID tenant){return jdbc.query("select * from workforce_statutory_policies where tenant_id=? order by effective_from desc,name,id",(r,n)->new Policy(r.getObject("id",UUID.class),r.getString("name"),r.getInt("year"),r.getString("currency"),r.getObject("effective_from",LocalDate.class),r.getObject("effective_to",LocalDate.class),r.getBoolean("enabled"),r.getLong("version"),json.read(r.getString("config"),StatutoryPolicyConfig.class)),tenant);}
 public Policy resolve(UUID tenant,YearMonth month){var rows=list(tenant).stream().filter(p->p.enabled()&&!p.effectiveFrom().isAfter(month.atDay(1))&&!p.effectiveTo().isBefore(month.atEndOfMonth())).toList();if(rows.size()>1)throw WorkforceException.conflict();return rows.isEmpty()?null:rows.getFirst();}
 public List<Policy> versions(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_statutory_policy_revisions where tenant_id=? and policy_id=? order by version desc",(r,n)->json.read(r.getString(1),Policy.class),tenant,id);}
 public void lock(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.statutory-policy:"+tenant);}
 public void save(UUID tenant,Policy p){int n=jdbc.update("insert into workforce_statutory_policies(id,tenant_id,name,year,currency,effective_from,effective_to,enabled,version,config) values(?,?,?,?,?,?,?,?,?,?::jsonb) on conflict(id) do update set name=excluded.name,year=excluded.year,currency=excluded.currency,effective_from=excluded.effective_from,effective_to=excluded.effective_to,enabled=excluded.enabled,version=excluded.version,config=excluded.config,updated_at=now() where workforce_statutory_policies.tenant_id=excluded.tenant_id",p.id(),tenant,p.name(),p.year(),p.currency(),p.effectiveFrom(),p.effectiveTo(),p.enabled(),p.version(),json.write(p.config()));if(n!=1)throw WorkforceException.conflict();jdbc.update("insert into workforce_statutory_policy_revisions(policy_id,tenant_id,version,snapshot) values(?,?,?,?::jsonb)",p.id(),tenant,p.version(),json.write(p));}
}
