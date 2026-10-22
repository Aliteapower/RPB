package com.rpb.reservation.wallet.application;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.domain.WalletException;
import org.springframework.stereotype.Component;

@Component
public class WalletAccess {
    private final AppGateService gate;
    private final WalletDirectory directory;
    public WalletAccess(AppGateService gate,WalletDirectory directory) {this.gate=gate;this.directory=directory;}
    public void staff(StoreScope scope,CurrentActor actor,String permission,boolean newTransaction) {
        if(actor==null) throw new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录");
        if(!scope.tenantId().value().equals(actor.tenantId()) || !actor.canAccessStore(scope.storeId().value()) || !actor.hasPermission(permission)) throw WalletException.forbidden();
        directory.store(scope);
        if(newTransaction) require(gate.evaluate(new AppGateAccessRequest("wallet",actor.tenantId(),scope.storeId().value(),permission,actor)));
    }
    public void customer(StoreScope scope,CustomerAuthPrincipal principal,boolean newTransaction) {
        if(principal==null) throw new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录顾客账号");
        if(!scope.tenantId().value().equals(principal.tenantId())) throw WalletException.forbidden();
        directory.store(scope); directory.customer(scope.tenantScope(),principal.customerId());
        if(newTransaction) require(commercial(scope));
    }
    public AppGateDecision commercial(StoreScope scope) {return gate.evaluateCommercial("wallet",scope.tenantId().value(),scope.storeId().value());}
    public void own(WalletViews.Account account,CustomerAuthPrincipal principal) {
        if(!account.tenantId().equals(principal.tenantId()) || !account.customerId().equals(principal.customerId())) throw WalletException.missing();
    }
    private void require(AppGateDecision decision) {
        if(!decision.allowed()) throw new WalletException(decision.denyReason().name(),403,"钱包产品未开通、已到期、停用或无操作权限");
    }
}
