package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.customerauth.application.CustomerAuthApplicationService;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodScheduleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBookingApplicationService {

    private final PublicBookingStoreRepositoryPort storeRepository;
    private final PublicBookingSettingsRepositoryPort settingsRepository;
    private final CustomerOAuthSettingsRepositoryPort oauthSettingsRepository;
    private final CustomerEmailSettingsRepositoryPort emailSettingsRepository;
    private final CustomerAuthApplicationService customerAuthService;
    private final ReservationCreateApplicationService reservationCreateService;
    private final ReservationMealPeriodScheduleService mealPeriodScheduleService;
    private final Clock clock;

    @Autowired
    public PublicBookingApplicationService(
        PublicBookingStoreRepositoryPort storeRepository,
        PublicBookingSettingsRepositoryPort settingsRepository,
        CustomerOAuthSettingsRepositoryPort oauthSettingsRepository,
        CustomerEmailSettingsRepositoryPort emailSettingsRepository,
        CustomerAuthApplicationService customerAuthService,
        ReservationCreateApplicationService reservationCreateService,
        ReservationMealPeriodRepositoryPort mealPeriodRepository
    ) {
        this(
            storeRepository,
            settingsRepository,
            oauthSettingsRepository,
            emailSettingsRepository,
            customerAuthService,
            reservationCreateService,
            mealPeriodRepository,
            Clock.systemUTC()
        );
    }

    public PublicBookingApplicationService(
        PublicBookingStoreRepositoryPort storeRepository,
        PublicBookingSettingsRepositoryPort settingsRepository,
        CustomerOAuthSettingsRepositoryPort oauthSettingsRepository,
        CustomerEmailSettingsRepositoryPort emailSettingsRepository,
        CustomerAuthApplicationService customerAuthService,
        ReservationCreateApplicationService reservationCreateService,
        ReservationMealPeriodRepositoryPort mealPeriodRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.settingsRepository = settingsRepository;
        this.oauthSettingsRepository = oauthSettingsRepository;
        this.emailSettingsRepository = emailSettingsRepository;
        this.customerAuthService = customerAuthService;
        this.reservationCreateService = reservationCreateService;
        this.mealPeriodScheduleService = new ReservationMealPeriodScheduleService(mealPeriodRepository);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PublicBookingContext> getContext(UUID storeId, String rawBusinessDate) {
        PublicBookingStoreProfile store = storeRepository.findActiveStoreProfileByStoreId(storeId).orElse(null);
        if (store == null) {
            return Optional.empty();
        }
        ZoneId zoneId = zoneId(store.timezone());
        LocalDate businessDate = resolveBusinessDate(rawBusinessDate, zoneId);
        if (businessDate == null) {
            return Optional.empty();
        }
        PublicBookingSettings settings = settingsRepository.findSettings(store.scope()).orElse(PublicBookingSettings.disabled());
        List<PublicBookingAvailabilityRule> availabilityRules = settingsRepository.findAvailabilityRules(store.scope());
        return Optional.of(new PublicBookingContext(
            store,
            settings,
            businessDate,
            applyAvailabilityRules(
                mealPeriodScheduleService.listSlots(store.scope(), zoneId.getId(), businessDate, Instant.now(clock)),
                availabilityRules
            ),
            emailAuthEnabled(store),
            publicAuthProviders(store)
        ));
    }

    private List<ReservationTimeSlot> applyAvailabilityRules(
        List<ReservationTimeSlot> slots,
        List<PublicBookingAvailabilityRule> availabilityRules
    ) {
        return slots.stream()
            .map(slot -> applyAvailabilityRule(slot, availabilityRules))
            .toList();
    }

    private ReservationTimeSlot applyAvailabilityRule(
        ReservationTimeSlot slot,
        List<PublicBookingAvailabilityRule> availabilityRules
    ) {
        if (!slot.selectable()) {
            return slot;
        }
        return PublicBookingAvailabilityRules
            .effectiveRule(availabilityRules, new BusinessDate(slot.businessDate()), slot.periodKey())
            .filter(rule -> PublicBookingSettings.MODE_CLOSED.equals(rule.quotaMode()))
            .map(rule -> new ReservationTimeSlot(
                slot.periodId(),
                slot.periodKey(),
                slot.displayName(),
                slot.businessDate(),
                slot.time(),
                slot.startAt(),
                slot.nextDay(),
                false
            ))
            .orElse(slot);
    }

    private boolean emailAuthEnabled(PublicBookingStoreProfile store) {
        return emailSettingsRepository
            .findEmailSettings(store.scope().tenantId().value(), store.scope().storeId().value())
            .filter(CustomerEmailSettings::usableForLoginCode)
            .isPresent();
    }

    private List<PublicBookingAuthProvider> publicAuthProviders(PublicBookingStoreProfile store) {
        return List.of("google", "facebook").stream()
            .map(provider -> oauthSettingsRepository
                .findProviderSettings(store.scope().tenantId().value(), store.scope().storeId().value(), provider)
                .filter(this::isPublicAuthProvider)
                .map(settings -> new PublicBookingAuthProvider(settings.provider(), settings.clientId())))
            .flatMap(Optional::stream)
            .toList();
    }

    private boolean isPublicAuthProvider(CustomerOAuthProviderSettings settings) {
        return settings.usableForPublicLogin();
    }

    @Transactional
    public PublicBookingCreateResult createBooking(PublicBookingCreateCommand command) {
        if (
            command == null
                || command.storeId() == null
                || command.partySize() == null
                || command.partySize() <= 0
                || command.reservedStartAt() == null
                || !hasText(command.idempotencyKey())
        ) {
            return PublicBookingCreateResult.failure(PublicBookingError.INVALID_REQUEST);
        }

        PublicBookingStoreProfile store = storeRepository.findActiveStoreProfileByStoreId(command.storeId()).orElse(null);
        if (store == null) {
            return PublicBookingCreateResult.failure(PublicBookingError.STORE_NOT_FOUND);
        }
        PublicBookingSettings settings = settingsRepository.findSettings(store.scope()).orElse(PublicBookingSettings.disabled());
        if (!settings.enabled()) {
            return PublicBookingCreateResult.failure(PublicBookingError.BOOKING_DISABLED);
        }

        CustomerAuthPrincipal principal = null;
        if (settings.requireCustomerLogin()) {
            principal = customerAuthService.authenticateSession(command.customerSessionToken()).orElse(null);
            if (principal == null) {
                return PublicBookingCreateResult.failure(PublicBookingError.LOGIN_REQUIRED);
            }
            if (!store.scope().tenantId().value().equals(principal.tenantId())) {
                return PublicBookingCreateResult.failure(PublicBookingError.INVALID_SESSION);
            }
        }

        ZoneId zoneId = zoneId(store.timezone());
        LocalDate businessDate = command.businessDate() == null
            ? command.reservedStartAt().atZone(zoneId).toLocalDate()
            : command.businessDate();
        if (!isInsideBookingWindow(command.reservedStartAt(), businessDate, zoneId, settings)) {
            return PublicBookingCreateResult.failure(PublicBookingError.INVALID_BOOKING_WINDOW);
        }

        ReservationCreateResult result = reservationCreateService.createReservation(new CreateReservationCommand(
            store.scope().tenantId().value(),
            store.scope().storeId().value(),
            command.partySize(),
            command.reservedStartAt(),
            null,
            businessDate,
            principal == null ? null : principal.customerId(),
            principal == null ? null : principal.displayName(),
            null,
            trimToNull(command.phoneE164()),
            trimToNull(command.note()),
            command.idempotencyKey().trim(),
            principal == null ? UUID.randomUUID() : principal.customerId(),
            OperationSource.CUSTOMER,
            null,
            OperationSource.PUBLIC_BOOKING,
            null,
            null,
            null
        ));
        if (!result.success()) {
            return PublicBookingCreateResult.failure(PublicBookingError.RESERVATION_REJECTED);
        }
        return PublicBookingCreateResult.success(result);
    }

    private boolean isInsideBookingWindow(
        Instant reservedStartAt,
        LocalDate businessDate,
        ZoneId zoneId,
        PublicBookingSettings settings
    ) {
        Instant now = Instant.now(clock);
        if (reservedStartAt.isBefore(now.plusSeconds(settings.minLeadMinutes() * 60L))) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return !businessDate.isAfter(today.plusDays(settings.maxAdvanceDays()));
    }

    private LocalDate resolveBusinessDate(String rawBusinessDate, ZoneId zoneId) {
        if (!hasText(rawBusinessDate)) {
            return LocalDate.now(clock.withZone(zoneId));
        }
        try {
            return LocalDate.parse(rawBusinessDate.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
