package com.rpb.reservation.workforce.compliance;
import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.LocalDate;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository public class ComplianceRepository {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public ComplianceRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public void lock(UUID tenant,String key){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.compliance:"+tenant+":"+key);}
 public List<Policy> policies(UUID tenant){return jdbc.query("select distinct on(id) snapshot from workforce_compliance_policies where tenant_id=? order by id,version desc",(r,n)->json.read(r.getString(1),Policy.class),tenant);}
 public List<Policy> publishedPolicies(UUID tenant){return jdbc.query("select distinct on(id) snapshot from workforce_compliance_policies where tenant_id=? and status='published' order by id,version desc",(r,n)->json.read(r.getString(1),Policy.class),tenant);}
 public List<Policy> versions(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_compliance_policies where tenant_id=? and id=? order by version desc",(r,n)->json.read(r.getString(1),Policy.class),tenant,id);}
 public Optional<Policy> policy(UUID tenant,UUID id,long version){return jdbc.query("select snapshot from workforce_compliance_policies where tenant_id=? and id=? and version=?",(r,n)->json.read(r.getString(1),Policy.class),tenant,id,version).stream().findFirst();}
 public void policy(CurrentActor a,Policy p){jdbc.update("insert into workforce_compliance_policies(id,tenant_id,version,name,effective_from,effective_to,status,snapshot,created_by) values(?,?,?,?,?,?,?,?::jsonb,?)",p.id(),a.tenantId(),p.version(),p.name(),p.effectiveFrom(),p.effectiveTo(),p.status(),json.write(p),a.actorId());}
 public List<Contract> contracts(UUID tenant,UUID employee){return jdbc.query("select snapshot from workforce_employment_contracts where tenant_id=? and employee_id=? order by effective_from,version",(r,n)->json.read(r.getString(1),Contract.class),tenant,employee);}
 public Optional<Contract> contract(UUID tenant,UUID employee,LocalDate on){return jdbc.query("select snapshot from workforce_employment_contracts where tenant_id=? and employee_id=? and effective_from<=? order by effective_from desc limit 1",(r,n)->json.read(r.getString(1),Contract.class),tenant,employee,on).stream().findFirst();}
 public void contract(CurrentActor a,Contract c){jdbc.update("insert into workforce_employment_contracts(id,tenant_id,employee_id,company_id,version,effective_from,policy_id,policy_version,snapshot,created_by) values(?,?,?,?,?,?,?,?,?::jsonb,?)",c.id(),a.tenantId(),c.employeeId(),c.companyId(),c.version(),c.effectiveFrom(),c.policyId(),c.policyVersion(),json.write(c),a.actorId());}
 public <T>Optional<T> replay(CurrentActor a,UUID store,UUID id,String kind,Object request,Class<T> type){var rows=jdbc.query("select store_id,actor_id,kind,request_json::text,response_json::text from workforce_compliance_commands where tenant_id=? and id=?",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5)},a.tenantId(),id);if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!store.equals(row[0])||!a.actorId().equals(row[1])||!kind.equals(row[2])||!json.read((String)row[3],com.fasterxml.jackson.databind.JsonNode.class).equals(json.read(json.write(request),com.fasterxml.jackson.databind.JsonNode.class)))throw WorkforceException.conflict();return Optional.of(json.read((String)row[4],type));}
 public void receipt(CurrentActor a,UUID store,UUID id,String kind,Object request,Object result){jdbc.update("insert into workforce_compliance_commands(tenant_id,id,store_id,actor_id,kind,request_json,response_json) values(?,?,?,?,?,?::jsonb,?::jsonb)",a.tenantId(),id,store,a.actorId(),kind,json.write(request),json.write(result));}
}
