package com.rpb.reservation.cleaning.command;

import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for cleaning completion intent.
 *
 * <p>The command records intent only; resource release and turnover metrics remain separate concerns.</p>
 */
public record CompleteCleaningCommand(
        StoreScope storeScope,
        CleaningId cleaningId,
        Optional<IdempotencyKey> idempotencyKey) {

    public CompleteCleaningCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(cleaningId, "cleaningId must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "cleaning.complete.command";
    }
}
