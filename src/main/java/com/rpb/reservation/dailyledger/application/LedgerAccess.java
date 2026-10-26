package com.rpb.reservation.dailyledger.application;

import com.rpb.reservation.appgate.application.AppGateAccessRequest;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.springframework.stereotype.Component;

@Component
public class LedgerAccess {
    private final AppGateService gate;
    public LedgerAccess(AppGateService gate){this.gate=gate;}
    public void require(StoreScope scope,CurrentActor actor,String permission) { check(scope,actor,permission,false); }
    public void requireHistory(StoreScope scope,CurrentActor actor,String permission) { check(scope,actor,permission,true); }
    private void check(StoreScope scope,CurrentActor actor,String permission,boolean history) {
        if(actor==null)throw new LedgerException("UNAUTHENTICATED",401,"请先登录");
        if(!scope.tenantId().value().equals(actor.tenantId())||!actor.canAccessStore(scope.storeId().value()))
            throw new LedgerException("FORBIDDEN",403,"无权访问此门店");
        var request=new AppGateAccessRequest("daily_ledger",actor.tenantId(),scope.storeId().value(),permission,actor);
        var decision=history?gate.evaluateHistory(request):gate.evaluate(request);
        if(!decision.allowed())throw new LedgerException("FORBIDDEN",403,"产品线未开通、已停用或无操作权限");
    }
}
