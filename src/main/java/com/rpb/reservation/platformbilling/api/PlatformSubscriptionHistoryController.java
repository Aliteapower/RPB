package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryEvent;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryService;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/events")
public class PlatformSubscriptionHistoryController {
    private final ProductSubscriptionHistoryService history;
    private final CurrentActorProvider actors;
    public PlatformSubscriptionHistoryController(ProductSubscriptionHistoryService history, CurrentActorProvider actors) {
        this.history = history;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<HistoryResponse> list(@PathVariable UUID tenantId, @PathVariable UUID subscriptionId) {
        PlatformBillingSecurity.requirePlatformAdmin(actors, "platform.billing.manage");
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(new HistoryResponse(history.list(tenantId, subscriptionId)));
    }

    @ExceptionHandler(PlatformBillingApiException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> apiException(PlatformBillingApiException exception) { return error(exception.code()); }
    @ExceptionHandler(PlatformBillingServiceException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> serviceException(PlatformBillingServiceException exception) {
        return error(PlatformBillingApiErrorCode.valueOf(exception.code().name()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> persistenceException(DataAccessException exception) { return error(PlatformBillingApiErrorCode.PERSISTENCE_ERROR); }
    private static ResponseEntity<PlatformBillingApiErrorResponse> error(PlatformBillingApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).header("Cache-Control", "private, no-store").body(PlatformBillingApiErrorResponse.of(code));
    }
    public record HistoryResponse(List<ProductSubscriptionHistoryEvent> events) {}
}
