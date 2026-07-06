package com.rpb.reservation.publicbooking.api;

import com.rpb.reservation.common.web.HostPrefixContext;
import com.rpb.reservation.common.web.HostPrefixContextResolver;
import com.rpb.reservation.publicbooking.application.PublicBookingEntryApplicationService;
import com.rpb.reservation.publicbooking.application.PublicBookingEntryError;
import com.rpb.reservation.publicbooking.application.PublicBookingEntryResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicBookingEntryController {
    private final PublicBookingEntryApplicationService service;
    private final HostPrefixContextResolver hostPrefixContextResolver;

    public PublicBookingEntryController(
        PublicBookingEntryApplicationService service,
        HostPrefixContextResolver hostPrefixContextResolver
    ) {
        this.service = service;
        this.hostPrefixContextResolver = hostPrefixContextResolver;
    }

    @GetMapping("/api/v1/public/booking-entry")
    public ResponseEntity<?> resolveEntry(HttpServletRequest request) {
        HostPrefixContext context = hostPrefixContextResolver.resolve(request);
        if (!context.isTenant()) {
            return entryError(PublicBookingEntryError.TENANT_CONTEXT_REQUIRED);
        }
        PublicBookingEntryResult result = service.resolveTenantEntry(context.tenantCode());
        if (!result.success()) {
            return entryError(result.error());
        }
        return ResponseEntity.ok(new PublicBookingEntryResponse(
            true,
            context.tenantCode(),
            result.store().scope().storeId().value()
        ));
    }

    private static ResponseEntity<PublicBookingEntryErrorResponse> entryError(PublicBookingEntryError error) {
        HttpStatus status = switch (error) {
            case TENANT_CONTEXT_REQUIRED -> HttpStatus.BAD_REQUEST;
            case STORE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case MULTIPLE_ENABLED_STORES -> HttpStatus.CONFLICT;
        };
        String code = switch (error) {
            case TENANT_CONTEXT_REQUIRED -> "tenant_context_required";
            case STORE_NOT_FOUND -> "store_not_found";
            case MULTIPLE_ENABLED_STORES -> "multiple_enabled_stores";
        };
        return ResponseEntity.status(status).body(new PublicBookingEntryErrorResponse(false, code));
    }

    public record PublicBookingEntryResponse(boolean success, String tenantCode, UUID storeId) {
    }

    public record PublicBookingEntryErrorResponse(boolean success, String error) {
    }
}
