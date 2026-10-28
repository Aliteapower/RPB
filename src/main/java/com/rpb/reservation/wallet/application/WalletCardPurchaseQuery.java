package com.rpb.reservation.wallet.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rpb.reservation.cards.application.CardDtos.Purchase;
import com.rpb.reservation.cards.purchase.PurchaseService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-only display projection; payment commands and customer responses keep their existing DTOs. */
@Service
@Transactional(readOnly = true)
public class WalletCardPurchaseQuery {
    private final PurchaseService purchases;
    private final CustomerDirectoryQuery customers;

    public WalletCardPurchaseQuery(PurchaseService purchases, CustomerDirectoryQuery customers) {
        this.purchases = purchases;
        this.customers = customers;
    }

    public record StaffPurchase(@JsonUnwrapped Purchase order, String customerName, String customerPhone, boolean refundable) {}

    public List<StaffPurchase> staff(StoreScope scope, CurrentActor actor, UUID before) {
        var orders = purchases.staff(scope, actor, before);
        if (orders.isEmpty()) return List.of();
        var ids = orders.stream().map(Purchase::customerId).collect(Collectors.toSet());
        var refundable = purchases.refundable(scope, actor, orders.stream().map(Purchase::id).collect(Collectors.toSet()));
        var contacts = customers.activeByIds(scope.tenantScope(), ids).stream()
            .collect(Collectors.toMap(CustomerManagementItem::id, Function.identity()));
        return orders.stream().map(order -> {
            var contact = contacts.get(order.customerId());
            return new StaffPurchase(order, contact == null ? null : contact.displayName(),
                contact == null ? null : contact.phoneE164(), refundable.contains(order.id()));
        }).toList();
    }
}
