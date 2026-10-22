package com.rpb.reservation.wallet.application;

import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Set;

/** Read-only page query shared by bill and top-up lists. */
public record WalletHistoryQuery(int offset, String status) {
    public WalletHistoryQuery {
        if (offset < 0 || offset > 100000 || status==null || !Set.of("all", "pending", "completed", "closed").contains(status))
            throw WalletException.invalid();
    }
}
