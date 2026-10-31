package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.common.scope.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import com.rpb.reservation.customerauth.application.CustomerActivationState;

public interface CustomerActivationPort {
    record Challenge(UUID id,String codeHash,int attempts,Instant expiresAt){}
    void issue(StoreScope scope,UUID customer,UUID actor,UUID id,String hash,Instant expiresAt);
    Optional<Challenge> latest(StoreScope scope,UUID customer);
    void fail(StoreScope scope,UUID id);
    void consumeAll(TenantScope scope,UUID customer);
    void audit(StoreScope scope,UUID customer,UUID actor,String action);
    Map<UUID,CustomerActivationState> states(TenantScope scope,Set<UUID> customers);
}
