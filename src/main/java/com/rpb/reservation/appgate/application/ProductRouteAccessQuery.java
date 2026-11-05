package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Route eligibility is separate from whether the administrator displays a menu entry. */
@Service
public class ProductRouteAccessQuery {
    private final AppGateService gate;

    public ProductRouteAccessQuery(AppGateService gate) { this.gate = gate; }

    public record ProductAccess(String appKey, boolean businessAllowed, boolean historyAllowed,
                                String historyRoute, String denialReason) {}

    public List<ProductAccess> find(CurrentActor actor, UUID store) {
        if (actor == null || actor.actorId() == null || actor.tenantId() == null || !actor.canAccessStore(store)
            || "customer".equals(actor.actorType()) || actor.roles().contains("platform_admin")) return List.of();
        boolean administrator = actor.roles().contains("tenant_admin") && actor.hasPermission("tenant.admin.manage");
        return ProductLineEntryRegistry.entries().stream().filter(entry -> !entry.appKey().isBlank())
            .map(entry -> access(entry, actor, store, administrator)).toList();
    }

    private ProductAccess access(ProductLineEntryRegistry.Entry entry, CurrentActor actor, UUID store, boolean administrator) {
        boolean permission = administrator || entry.permissions().stream().anyMatch(actor::hasPermission);
        // Normal AppGate validates the same actor/tenant/store and all commercial checks, ignoring entry_visible.
        var business = permission ? gate.evaluate(new AppGateAccessRequest(entry.appKey(), actor.tenantId(), store, null, actor)) : null;
        boolean businessAllowed = business != null && business.allowed();
        var historyPermissions = entry.historyRoute().isBlank() ? Set.<String>of() : entry.historyPermissions().stream()
            .filter(actor::hasPermission).filter(required -> gate.evaluateHistory(new AppGateAccessRequest(
                entry.appKey(), actor.tenantId(), store, required, actor)).allowed()).collect(Collectors.toSet());
        boolean historyAllowed = !historyPermissions.isEmpty();
        String historyRoute = historyAllowed ? AppGateService.historyRoute(entry, historyPermissions).replace(":storeId", store.toString()) : "";
        return new ProductAccess(entry.appKey(), businessAllowed, historyAllowed, historyRoute,
            businessAllowed ? null : permission ? business.denyReason().name() : AppGateDenyReason.PERMISSION_DENIED.name());
    }
}
