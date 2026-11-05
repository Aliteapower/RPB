package com.rpb.reservation.wallet.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rpb.reservation.cards.application.CardDtos.Entry;
import com.rpb.reservation.cards.entitlement.CardHoldingService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-only history display. The existing card query still owns authorization and expiry settlement. */
@Service
@Transactional
public class WalletCardEntryQuery {
    private final CardHoldingService cards;
    private final CustomerDirectoryQuery customers;

    public WalletCardEntryQuery(CardHoldingService cards, CustomerDirectoryQuery customers) {
        this.cards = cards;
        this.customers = customers;
    }

    public record StaffEntry(@JsonUnwrapped Entry entry, String customerName, String customerPhone) {}

    public List<StaffEntry> staff(StoreScope scope, CurrentActor actor, UUID customerId, UUID before) {
        // This call may settle past daily allowances; do not make the surrounding transaction read-only.
        var entries = cards.entries(scope, actor, customerId, before);
        if (entries.isEmpty()) return List.of();
        var ids = entries.stream().map(Entry::customerId).filter(Objects::nonNull).collect(Collectors.toSet());
        var contacts = customers.activeByIds(scope.tenantScope(), ids).stream()
            .collect(Collectors.toMap(CustomerManagementItem::id, Function.identity()));
        return entries.stream().map(entry -> {
            var contact = contacts.get(entry.customerId());
            return new StaffEntry(entry, contact == null ? null : contact.displayName(),
                contact == null ? null : contact.phoneE164());
        }).toList();
    }
}
