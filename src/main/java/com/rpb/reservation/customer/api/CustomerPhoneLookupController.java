package com.rpb.reservation.customer.api;

import static com.rpb.reservation.appgate.domain.AppGateRequiredPermission.CUSTOMER_LOOKUP;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.CustomerPhoneLookupQuery;
import com.rpb.reservation.customer.application.CustomerPhoneLookupResult;
import com.rpb.reservation.customer.application.service.CustomerPhoneLookupApplicationService;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/customers")
public class CustomerPhoneLookupController {
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final CustomerPhoneLookupApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final CustomerPhoneLookupApiErrorMapper errorMapper;

    @Autowired
    public CustomerPhoneLookupController(
        CustomerPhoneLookupApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        CustomerPhoneLookupApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/phone-lookup")
    @RequireAppGate(appKey = "reservation_queue", permission = CUSTOMER_LOOKUP)
    public ResponseEntity<?> lookupCustomerByPhone(
        @PathVariable UUID storeId,
        @RequestParam(value = "phoneE164", required = false) String phoneE164
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(CustomerPhoneLookupApiErrorCode.FORBIDDEN);
        }

        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(CUSTOMER_LOOKUP)) {
            return errorMapper.toResponse(CustomerPhoneLookupApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(CustomerPhoneLookupApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        E164Phone phone = parsePhone(phoneE164);
        if (phone == null) {
            return errorMapper.toResponse(CustomerPhoneLookupApiErrorCode.INVALID_PHONE_E164);
        }

        CustomerPhoneLookupQuery query = new CustomerPhoneLookupQuery(
            new TenantScope(new TenantId(actor.tenantId())),
            phone
        );
        CustomerPhoneLookupResult result = applicationService.lookup(query);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(CustomerPhoneLookupResponse.from(result));
    }

    private static E164Phone parsePhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.isBlank()) {
            return null;
        }

        try {
            return new E164Phone(phoneE164.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
