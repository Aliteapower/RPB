package com.rpb.reservation.workforce.leave;

import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeaveTypeRepository {
    private final JdbcTemplate jdbc;
    public LeaveTypeRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<LeaveType> list(UUID tenant,UUID store){return jdbc.query("select id,name,paid,enabled,version from workforce_leave_types where tenant_id=? and store_id=? order by name,id",(r,n)->new LeaveType(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getBoolean(4),r.getLong(5)),tenant,store);}
    public Optional<LeaveType> find(UUID tenant,UUID store,UUID id){return list(tenant,store).stream().filter(t->t.id().equals(id)).findFirst();}
    public void save(UUID tenant,UUID store,LeaveType type){
        int changed=jdbc.update("""
            insert into workforce_leave_types(id,tenant_id,store_id,name,paid,enabled,version) values(?,?,?,?,?,?,?)
            on conflict(id) do update set name=excluded.name,paid=excluded.paid,enabled=excluded.enabled,version=excluded.version,updated_at=now()
            where workforce_leave_types.tenant_id=excluded.tenant_id and workforce_leave_types.store_id=excluded.store_id
            """,type.id(),tenant,store,type.name(),type.paid(),type.enabled(),type.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
}
