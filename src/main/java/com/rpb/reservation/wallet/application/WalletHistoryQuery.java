package com.rpb.reservation.wallet.application;

import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Set;
import java.util.UUID;

/** Read-only page query shared by bill and top-up lists. */
public record WalletHistoryQuery(int offset, String status, String origin, UUID customerId) {
    public WalletHistoryQuery(int offset,String status){this(offset,status,null,null);}
    public WalletHistoryQuery {
        if (offset < 0 || offset > 100000 || status==null || !Set.of("all", "pending", "completed", "closed").contains(status))
            throw WalletException.invalid();
        if(origin!=null && !Set.of("staff","customer").contains(origin))throw WalletException.invalid();
    }
}
