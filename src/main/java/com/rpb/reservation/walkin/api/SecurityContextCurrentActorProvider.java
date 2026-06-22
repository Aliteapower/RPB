package com.rpb.reservation.walkin.api;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentActorProvider implements CurrentActorProvider {

    @Override
    public Optional<CurrentActor> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentActor currentActor) {
            return Optional.of(currentActor);
        }
        Object details = authentication.getDetails();
        if (details instanceof CurrentActor currentActor) {
            return Optional.of(currentActor);
        }
        return Optional.empty();
    }
}
