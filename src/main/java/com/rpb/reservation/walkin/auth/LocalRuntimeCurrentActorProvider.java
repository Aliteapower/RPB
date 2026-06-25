package com.rpb.reservation.walkin.auth;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Primary
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "rpb.local-auth", name = "enabled", havingValue = "true")
public class LocalRuntimeCurrentActorProvider implements CurrentActorProvider {
    private static final String TENANT_ID_HEADER = "X-Test-Tenant-Id";
    private static final String ACTOR_ID_HEADER = "X-Test-Actor-Id";
    private static final String ACTOR_TYPE_HEADER = "X-Test-Actor-Type";
    private static final String ACTOR_ROLE_HEADER = "X-Test-Actor-Role";
    private static final String PERMISSIONS_HEADER = "X-Test-Permissions";
    private static final String STORE_IDS_HEADER = "X-Test-Store-Ids";

    private final LocalAuthProperties properties;

    public LocalRuntimeCurrentActorProvider(LocalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<CurrentActor> currentActor() {
        Optional<CurrentActor> sessionActor = sessionActor();
        if (sessionActor.isPresent()) {
            return sessionActor;
        }

        HttpServletRequest request = currentRequest().orElse(null);
        UUID tenantId = uuidValue(header(request, TENANT_ID_HEADER)).orElse(properties.getTenantId());
        UUID actorId = uuidValue(header(request, ACTOR_ID_HEADER)).orElse(properties.getActorId());

        if (tenantId == null || actorId == null) {
            return Optional.empty();
        }

        String actorType = firstText(header(request, ACTOR_TYPE_HEADER), properties.getActorType()).orElse("staff");
        Set<String> roles = stringSet(header(request, ACTOR_ROLE_HEADER), properties.getRoles());
        Set<String> permissions = stringSet(header(request, PERMISSIONS_HEADER), properties.getPermissions());
        Set<UUID> storeIds = uuidSet(header(request, STORE_IDS_HEADER), properties.getStoreIds());

        return Optional.of(CurrentActor.storeStaff(tenantId, actorId, actorType, roles, permissions, storeIds));
    }

    private static Optional<CurrentActor> sessionActor() {
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

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static Optional<UUID> uuidValue(String value) {
        return firstText(value).map(UUID::fromString);
    }

    private static Optional<String> firstText(String... values) {
        return Arrays.stream(values)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .findFirst();
    }

    private static Set<String> stringSet(String headerValue, Iterable<String> configuredValues) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (headerValue != null && !headerValue.isBlank()) {
            Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(values::add);
            return values;
        }
        configuredValues.forEach(value -> {
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        });
        return values;
    }

    private static Set<UUID> uuidSet(String headerValue, Iterable<UUID> configuredValues) {
        LinkedHashSet<UUID> values = new LinkedHashSet<>();
        if (headerValue != null && !headerValue.isBlank()) {
            Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .forEach(values::add);
            return values;
        }
        configuredValues.forEach(value -> {
            if (value != null) {
                values.add(value);
            }
        });
        return values;
    }
}
