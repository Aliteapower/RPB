package com.rpb.reservation.walkin.api;

import java.util.Optional;

public interface CurrentActorProvider {
    Optional<CurrentActor> currentActor();
}
