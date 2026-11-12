package com.rpb.reservation.workforce.leave.encashment;

import static com.rpb.reservation.workforce.leave.encashment.LeaveEncashmentDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class LeaveEncashmentRepository {
    private final JdbcTemplate jdbc;
    private final WorkforceJson json;
    public LeaveEncashmentRepository(JdbcTemplate jdbc, WorkforceJson json) { this.jdbc=jdbc; this.json=json; }
    public Optional<Document> find(UUID tenant, UUID id) {
        return jdbc.query("select snapshot from workforce_leave_encashments where tenant_id=? and id=?",
                (r,n)->json.read(r.getString(1),Document.class),tenant,id).stream().findFirst();
    }
    public List<Document> list(UUID tenant, UUID employee) {
        return jdbc.query("select snapshot from workforce_leave_encashments where tenant_id=? and employee_id=? order by created_at,id",
                (r,n)->json.read(r.getString(1),Document.class),tenant,employee);
    }
    public void create(UUID tenant,Document d) {
        jdbc.update("insert into workforce_leave_encashments(tenant_id,id,employee_id,company_id,account_id,status,snapshot) values(?,?,?,?,?,?,?::jsonb)",
                tenant,d.id(),d.employeeId(),d.companyId(),d.accountId(),d.status(),json.write(d));
    }
    public void save(UUID tenant,Document d) {
        jdbc.update("update workforce_leave_encashments set status=?,snapshot=?::jsonb where tenant_id=? and id=?",d.status(),json.write(d),tenant,d.id());
    }
    public void consume(UUID tenant,UUID id,UUID batch,String channel,UUID actor) {
        jdbc.update("insert into workforce_leave_encashment_consumption(tenant_id,source_id,batch_id,channel,actor_id) values(?,?,?,?,?)",tenant,id,batch,channel,actor);
    }
    public boolean consumed(UUID tenant,UUID batch) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_leave_encashment_consumption where tenant_id=? and batch_id=?)",Boolean.class,tenant,batch));
    }
}
