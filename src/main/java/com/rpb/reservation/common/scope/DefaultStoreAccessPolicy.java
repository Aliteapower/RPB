package com.rpb.reservation.common.scope;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultStoreAccessPolicy implements StoreAccessPolicy {

    @Override
    public RuleDecision decide(StoreAccessInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision decide(StoreScope scope, UUID actorId, String actorType) {
        if (scope == null || actorId == null || actorType == null || actorType.isBlank()) {
            return RuleDecision.deny("store_access_denied");
        }
        return RuleDecision.allow();
    }
}
