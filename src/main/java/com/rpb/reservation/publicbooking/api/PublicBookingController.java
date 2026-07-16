package com.rpb.reservation.publicbooking.api;

import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.publicbooking.application.PublicBookingApplicationService;
import com.rpb.reservation.publicbooking.application.PublicBookingAuthProvider;
import com.rpb.reservation.publicbooking.application.PublicBookingContext;
import com.rpb.reservation.publicbooking.application.PublicBookingCreateCommand;
import com.rpb.reservation.publicbooking.application.PublicBookingCreateResult;
import com.rpb.reservation.publicbooking.application.PublicBookingError;
import com.rpb.reservation.publicbooking.application.PublicBookingSettings;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/booking")
public class PublicBookingController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PublicBookingApplicationService service;
    private final CustomerAuthCookieService cookieService;

    public PublicBookingController(PublicBookingApplicationService service, CustomerAuthCookieService cookieService) {
        this.service = service;
        this.cookieService = cookieService;
    }

    @GetMapping("/context")
    public ResponseEntity<?> context(
        @PathVariable UUID storeId,
        @RequestParam(value = "businessDate", required = false) String businessDate
    ) {
        return service.getContext(storeId, businessDate)
            .<ResponseEntity<?>>map(context -> ResponseEntity.ok(PublicBookingContextResponse.from(context)))
            .orElseGet(() -> publicError(PublicBookingError.STORE_NOT_FOUND));
    }

    @PostMapping("/reservations")
    public ResponseEntity<?> createReservation(
        @PathVariable UUID storeId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) PublicBookingCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        PublicBookingCreateRequest nonNullRequest = request == null
            ? new PublicBookingCreateRequest(null, null, null, null, null, null, null, null)
            : request;
        PublicBookingCreateResult result = service.createBooking(new PublicBookingCreateCommand(
            storeId,
            nonNullRequest.partySize(),
            nonNullRequest.reservedStartAt(),
            nonNullRequest.businessDate(),
            nonNullRequest.customerName(),
            nonNullRequest.customerNickname(),
            nonNullRequest.customerEmail(),
            nonNullRequest.phoneE164(),
            nonNullRequest.note(),
            idempotencyKey,
            cookieService.readSessionToken(servletRequest).orElse(null)
        ));
        if (!result.success()) {
            return publicError(result.error());
        }
        return ResponseEntity.status(result.reservation().replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .body(PublicBookingCreateResponse.from(result.reservation()));
    }

    private static ResponseEntity<PublicBookingErrorResponse> publicError(PublicBookingError error) {
        HttpStatus status = switch (error) {
            case STORE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LOGIN_REQUIRED, INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case BOOKING_DISABLED, INVALID_BOOKING_WINDOW, RESERVATION_REJECTED -> HttpStatus.CONFLICT;
            case PERSISTENCE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new PublicBookingErrorResponse(false, error.name().toLowerCase()));
    }

    public record PublicBookingCreateRequest(
        Integer partySize,
        Instant reservedStartAt,
        LocalDate businessDate,
        String customerName,
        String customerNickname,
        String customerEmail,
        String phoneE164,
        String note
    ) {
        public PublicBookingCreateRequest(
            Integer partySize,
            Instant reservedStartAt,
            LocalDate businessDate,
            String phoneE164,
            String note
        ) {
            this(partySize, reservedStartAt, businessDate, null, null, null, phoneE164, note);
        }
    }

    public record PublicBookingContextResponse(
        boolean success,
        StoreResponse store,
        SettingsResponse settings,
        LocalDate businessDate,
        List<TimeSlotResponse> timeSlots,
        boolean emailAuthEnabled,
        List<AuthProviderResponse> authProviders
    ) {
        static PublicBookingContextResponse from(PublicBookingContext context) {
            return new PublicBookingContextResponse(
                true,
                StoreResponse.from(context.store()),
                SettingsResponse.from(context.settings()),
                context.businessDate(),
                context.timeSlots().stream().map(TimeSlotResponse::from).toList(),
                context.emailAuthEnabled(),
                context.authProviders().stream().map(AuthProviderResponse::from).toList()
            );
        }
    }

    public record StoreResponse(
        UUID storeId,
        String storeName,
        String timezone,
        String shareAddress,
        String googleMapUrl,
        String shareContactPhone,
        String shareEmail,
        String whatsappBusinessPhoneE164
    ) {
        static StoreResponse from(PublicBookingStoreProfile store) {
            return new StoreResponse(
                store.scope().storeId().value(),
                store.storeName(),
                store.timezone(),
                store.shareAddress(),
                store.googleMapUrl(),
                store.shareContactPhone(),
                store.shareEmail(),
                store.whatsappBusinessPhoneE164()
            );
        }
    }

    public record SettingsResponse(
        boolean enabled,
        boolean requireCustomerLogin,
        String defaultQuotaMode,
        Integer defaultQuotaPercent,
        Integer defaultTableCount,
        Integer defaultGuestCount,
        int minLeadMinutes,
        int maxAdvanceDays
    ) {
        static SettingsResponse from(PublicBookingSettings settings) {
            return new SettingsResponse(
                settings.enabled(),
                settings.requireCustomerLogin(),
                settings.defaultQuotaMode(),
                settings.defaultQuotaPercent(),
                settings.defaultTableCount(),
                settings.defaultGuestCount(),
                settings.minLeadMinutes(),
                settings.maxAdvanceDays()
            );
        }
    }

    public record TimeSlotResponse(
        String periodKey,
        String displayName,
        LocalDate businessDate,
        LocalTime localTime,
        Instant startAt,
        boolean nextDay,
        boolean selectable
    ) {
        static TimeSlotResponse from(ReservationTimeSlot slot) {
            return new TimeSlotResponse(
                slot.periodKey(),
                slot.displayName(),
                slot.businessDate(),
                slot.time(),
                slot.startAt(),
                slot.nextDay(),
                slot.selectable()
            );
        }
    }

    public record AuthProviderResponse(
        String provider,
        String clientId
    ) {
        static AuthProviderResponse from(PublicBookingAuthProvider provider) {
            return new AuthProviderResponse(provider.provider(), provider.clientId());
        }
    }

    public record PublicBookingCreateResponse(
        boolean success,
        UUID reservationId,
        String reservationCode,
        String status,
        int partySize,
        LocalDate businessDate,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt
    ) {
        static PublicBookingCreateResponse from(ReservationCreateResult result) {
            return new PublicBookingCreateResponse(
                true,
                result.reservationId(),
                result.reservationCode(),
                result.status(),
                result.partySize(),
                result.businessDate(),
                result.reservedStartAt(),
                result.reservedEndAt(),
                result.holdUntilAt()
            );
        }
    }

    public record PublicBookingErrorResponse(boolean success, String error) {
    }
}
