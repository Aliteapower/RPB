package com.rpb.reservation.reservation.integration;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;

final class TestCurrentActorProvider implements CurrentActorProvider {
    private final ThreadLocal<CurrentActor> actor = new ThreadLocal<>();

    void set(CurrentActor currentActor) {
        actor.set(currentActor);
    }

    void clear() {
        actor.remove();
    }

    @Override
    public Optional<CurrentActor> currentActor() {
        return Optional.ofNullable(actor.get());
    }
}
