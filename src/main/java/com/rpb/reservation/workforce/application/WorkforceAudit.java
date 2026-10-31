package com.rpb.reservation.workforce.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WorkforceAudit {
    private final AuditLogRepositoryPort logs;
    private final ObjectMapper mapper;
    public WorkforceAudit(AuditLogRepositoryPort logs,ObjectMapper mapper){this.logs=logs;this.mapper=mapper;}
    public void changed(CurrentActor actor,UUID store,String operation,UUID target,Object before,Object after){
        try {
            var data=new LinkedHashMap<String,Object>();data.put("before",before);data.put("after",after);
            logs.append(new StoreScope(new TenantId(actor.tenantId()),store),new AuditLog(UUID.randomUUID(),"workforce."+operation,
                "workforce",target,"staff",actor.actorType(),actor.actorId(),mapper.writeValueAsString(data)));
        } catch(JsonProcessingException e){throw new IllegalStateException("workforce_audit_serialization",e);}
    }
}
