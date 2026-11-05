package com.rpb.reservation.wallet.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.wallet.domain.WalletException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WalletApiIdentity {
    private final CurrentActorProvider staff;
    private final CustomerAuthApplicationService customers;
    private final CustomerAuthCookieService cookies;
    public WalletApiIdentity(CurrentActorProvider staff,CustomerAuthApplicationService customers,CustomerAuthCookieService cookies) {this.staff=staff;this.customers=customers;this.cookies=cookies;}
    public CurrentActor staff() {
        var actor=staff.currentActor().orElseThrow(()->new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        if(actor.tenantId()==null || "platform_admin".equals(actor.actorType()))throw WalletException.forbidden();return actor;
    }
    public CustomerAuthPrincipal customer(HttpServletRequest request) {
        return cookies.readSessionToken(request).flatMap(customers::authenticateSession).orElseThrow(()->new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录顾客账号"));
    }
    public StoreScope scope(UUID tenant,UUID store) {return new StoreScope(new TenantId(tenant),store);}
}
