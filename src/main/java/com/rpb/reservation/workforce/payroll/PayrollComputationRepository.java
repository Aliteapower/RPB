package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.application.WorkforceJson;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cache writes participate in the wage commit; cache absence/incompatible JSON falls back to full compute. */
final class PayrollComputationRepository {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 PayrollComputationRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 Optional<PayrollComputation.State> find(UUID tenant,UUID batch){
  return jdbc.query("select snapshot from workforce_pay_computations where tenant_id=? and batch_id=?",(r,n)->{
   try{return json.read(r.getString(1),PayrollComputation.State.class);}catch(IllegalStateException incompatible){return null;}
  },tenant,batch).stream().filter(Objects::nonNull).findFirst();
 }
 void save(PayrollRepository.State batch,PayrollComputation.State state){
  jdbc.update("""
   insert into workforce_pay_computations(tenant_id,batch_id,company_id,snapshot) values(?,?,?,?::jsonb)
   on conflict(tenant_id,batch_id) do update set snapshot=excluded.snapshot,updated_at=now()
   where workforce_pay_computations.company_id=excluded.company_id
   """,batch.tenant(),batch.id(),batch.company(),json.write(state));
 }
}
