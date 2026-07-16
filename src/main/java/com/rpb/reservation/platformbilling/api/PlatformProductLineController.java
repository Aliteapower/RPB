package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.PlatformProductLineCreateCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLineQuery;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePriceUpdate;
import com.rpb.reservation.platformbilling.application.PlatformProductLineService;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/product-lines")
public class PlatformProductLineController {
    private static final String PRODUCT_LINE_MANAGE = "platform.product_line.manage";

    private final PlatformProductLineService productLineService;
    private final CurrentActorProvider currentActorProvider;

    public PlatformProductLineController(
        PlatformProductLineService productLineService,
        CurrentActorProvider currentActorProvider
    ) {
        this.productLineService = productLineService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformProductLineListResponse> listProductLines(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, PRODUCT_LINE_MANAGE);
        return ResponseEntity.ok(PlatformProductLineListResponse.from(
            productLineService.searchProductLines(new PlatformProductLineQuery(keyword, status, page, size))
        ));
    }

    @PostMapping
    public ResponseEntity<PlatformProductLineResponse> createProductLine(
        @RequestBody(required = false) PlatformProductLineCreateRequest request
    ) {
        PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, PRODUCT_LINE_MANAGE);
        return ResponseEntity.ok(PlatformProductLineResponse.from(
            productLineService.createProductLine(toCommand(request))
        ));
    }

    @PatchMapping("/{appKey}")
    public ResponseEntity<PlatformProductLineResponse> updateProductLine(
        @PathVariable String appKey,
        @RequestBody(required = false) PlatformProductLineMutationRequest request
    ) {
        PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, PRODUCT_LINE_MANAGE);
        return ResponseEntity.ok(PlatformProductLineResponse.from(
            productLineService.updateProductLine(appKey, toCommand(request))
        ));
    }

    @PatchMapping("/{appKey}/prices")
    public ResponseEntity<PlatformProductLineResponse> updateProductLinePrices(
        @PathVariable String appKey,
        @RequestBody(required = false) PlatformProductLinePriceRequests.UpdatePricesRequest request
    ) {
        PlatformBillingSecurity.requirePlatformAdmin(currentActorProvider, PRODUCT_LINE_MANAGE);
        return ResponseEntity.ok(PlatformProductLineResponse.from(
            productLineService.updateProductLinePrices(appKey, toPriceUpdates(request))
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

    private static PlatformProductLineMutationCommand toCommand(PlatformProductLineMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformProductLineMutationCommand(
            request.displayName(),
            request.status(),
            request.defaultEntryRoute(),
            request.description(),
            request.sortOrder()
        );
    }

    private static PlatformProductLineCreateCommand toCommand(PlatformProductLineCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformProductLineCreateCommand(
            request.appKey(),
            request.displayName(),
            request.status(),
            request.defaultEntryRoute(),
            request.description(),
            request.sortOrder()
        );
    }

    private static List<PlatformProductLinePriceUpdate> toPriceUpdates(PlatformProductLinePriceRequests.UpdatePricesRequest request) {
        if (request == null || request.prices() == null) {
            return List.of();
        }
        return request.prices().stream()
            .map(item -> new PlatformProductLinePriceUpdate(
                item.billingCycle(),
                item.amount(),
                item.currency(),
                item.status(),
                item.version()
            ))
            .toList();
    }

    private static ResponseEntity<PlatformBillingApiErrorResponse> apiError(PlatformBillingApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformBillingApiErrorResponse.of(code));
    }
}
