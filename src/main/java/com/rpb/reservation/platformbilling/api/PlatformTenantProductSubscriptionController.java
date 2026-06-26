package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformBillingOperator;
import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionService;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionStatusCommand;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/product-subscriptions")
public class PlatformTenantProductSubscriptionController {
    private static final String BILLING_MANAGE = "platform.billing.manage";

    private final ProductSubscriptionService subscriptionService;
    private final CurrentActorProvider currentActorProvider;

    public PlatformTenantProductSubscriptionController(
        ProductSubscriptionService subscriptionService,
        CurrentActorProvider currentActorProvider
    ) {
        this.subscriptionService = subscriptionService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<ProductSubscriptionListResponse> listSubscriptions(@PathVariable UUID tenantId) {
        PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, BILLING_MANAGE);
        return ResponseEntity.ok(ProductSubscriptionListResponse.from(subscriptionService.listSubscriptions(tenantId)));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ProductSubscriptionResponse> purchase(
        @PathVariable UUID tenantId,
        @RequestBody(required = false) ProductSubscriptionMutationRequest request
    ) {
        PlatformBillingOperator operator = requireOperator();
        return ResponseEntity.status(201).body(ProductSubscriptionResponse.from(
            subscriptionService.purchase(tenantId, toCommand(request), operator)
        ));
    }

    @PostMapping("/{subscriptionId}/renew")
    public ResponseEntity<ProductSubscriptionResponse> renew(
        @PathVariable UUID tenantId,
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) ProductSubscriptionMutationRequest request
    ) {
        PlatformBillingOperator operator = requireOperator();
        return ResponseEntity.ok(ProductSubscriptionResponse.from(
            subscriptionService.renew(tenantId, subscriptionId, toCommand(request), operator)
        ));
    }

    @PostMapping("/{subscriptionId}/suspend")
    public ResponseEntity<ProductSubscriptionResponse> suspend(
        @PathVariable UUID tenantId,
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) ProductSubscriptionStatusRequest request
    ) {
        PlatformBillingOperator operator = requireOperator();
        return ResponseEntity.ok(ProductSubscriptionResponse.from(
            subscriptionService.suspend(tenantId, subscriptionId, toStatusCommand(request), operator)
        ));
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<ProductSubscriptionResponse> cancel(
        @PathVariable UUID tenantId,
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) ProductSubscriptionStatusRequest request
    ) {
        PlatformBillingOperator operator = requireOperator();
        return ResponseEntity.ok(ProductSubscriptionResponse.from(
            subscriptionService.cancel(tenantId, subscriptionId, toStatusCommand(request), operator)
        ));
    }

    @PostMapping("/{subscriptionId}/convert-from-legacy")
    public ResponseEntity<ProductSubscriptionResponse> convertFromLegacy(
        @PathVariable UUID tenantId,
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) ProductSubscriptionMutationRequest request
    ) {
        PlatformBillingOperator operator = requireOperator();
        return ResponseEntity.ok(ProductSubscriptionResponse.from(
            subscriptionService.convertFromLegacy(tenantId, subscriptionId, toCommand(request), operator)
        ));
    }

    @ExceptionHandler(PlatformBillingApiException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> handleApiException(PlatformBillingApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PlatformBillingServiceException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> handleServiceException(PlatformBillingServiceException exception) {
        return apiError(PlatformBillingApiErrorCode.valueOf(exception.code().name()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PlatformBillingApiErrorResponse> handlePersistenceException() {
        return apiError(PlatformBillingApiErrorCode.PERSISTENCE_ERROR);
    }

    private PlatformBillingOperator requireOperator() {
        CurrentActor actor = PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, BILLING_MANAGE);
        return PlatformBillingSecurity.operator(actor);
    }

    private static ProductSubscriptionCommand toCommand(ProductSubscriptionMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductSubscriptionCommand(
            request.idempotencyKey(),
            request.appKey(),
            request.billingCycle(),
            request.currentPeriodStart(),
            request.currentPeriodEnd(),
            request.amount(),
            request.currency(),
            request.paymentNote(),
            request.durationCount(),
            request.version()
        );
    }

    private static ProductSubscriptionStatusCommand toStatusCommand(ProductSubscriptionStatusRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductSubscriptionStatusCommand(request.idempotencyKey(), request.paymentNote(), request.version());
    }

    private static ResponseEntity<PlatformBillingApiErrorResponse> apiError(PlatformBillingApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformBillingApiErrorResponse.of(code));
    }
}
