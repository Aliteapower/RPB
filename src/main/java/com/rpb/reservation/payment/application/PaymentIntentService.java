package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.domain.PaymentReferenceGenerator;
import com.rpb.reservation.payment.persistence.PaymentIntentRepository;
import com.rpb.reservation.payment.provider.PayNowQrPayloadBuilder;
import com.rpb.reservation.payment.provider.PayNowQrPayloadRequest;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIntentService {
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of("quick_pay", "generic_merchant");
    private static final Set<String> RECORD_STATUSES = Set.of("pending", "awaiting_verification", "paid", "expired", "cancelled", "failed");
    private static final String METHOD_PAYNOW = "paynow";
    private static final String STATUS_ACTIVE = "active";
    private static final String CURRENCY_SGD = "SGD";
    private static final int SESSION_TTL_SECONDS = 120;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final PaymentMethodProfileService profileService;
    private final PaymentIntentRepository repository;
    private final PayNowQrPayloadBuilder qrPayloadBuilder;
    private final PaymentBusinessDayService businessDayService;
    private final Clock clock;

    @Autowired
    public PaymentIntentService(
        PaymentMethodProfileService profileService,
        PaymentIntentRepository repository,
        PayNowQrPayloadBuilder qrPayloadBuilder,
        PaymentBusinessDayService businessDayService
    ) {
        this(profileService, repository, qrPayloadBuilder, businessDayService, Clock.systemUTC());
    }

    PaymentIntentService(
        PaymentMethodProfileService profileService,
        PaymentIntentRepository repository,
        PayNowQrPayloadBuilder qrPayloadBuilder,
        PaymentBusinessDayService businessDayService,
        Clock clock
    ) {
        this.profileService = Objects.requireNonNull(profileService, "payment_profile_service_required");
        this.repository = Objects.requireNonNull(repository, "payment_intent_repository_required");
        this.qrPayloadBuilder = Objects.requireNonNull(qrPayloadBuilder, "paynow_qr_payload_builder_required");
        this.businessDayService = Objects.requireNonNull(businessDayService, "payment_business_day_service_required");
        this.clock = Objects.requireNonNull(clock, "payment_clock_required");
    }

    @Transactional
    public PaymentIntentCreateResult createQuickPay(StoreScope scope, PaymentIntentCreateCommand command, CurrentActor actor) {
        Objects.requireNonNull(scope, "payment_scope_required");
        validateActor(scope, actor);
        PaymentIntentCreateCommand normalized = normalized(command);
        validateCreateCommand(normalized);

        return repository.findCreateResultByIdempotencyKey(scope, normalized.idempotencyKey())
            .map(PaymentIntentCreateResult::asReplay)
            .orElseGet(() -> createNew(scope, normalized, actor));
    }

    @Transactional(readOnly = true)
    public PaymentSession findSessionByNo(StoreScope scope, String sessionNo, CurrentActor actor) {
        Objects.requireNonNull(scope, "payment_scope_required");
        validateActor(scope, actor);
        if (isBlank(sessionNo)) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return repository.findSessionByNo(scope, sessionNo.trim())
            .orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_SESSION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PaymentQuickPayConfig findTerminalConfig(StoreScope scope, CurrentActor actor) {
        Objects.requireNonNull(scope, "payment_scope_required");
        validateActor(scope, actor);
        return profileService.findEffectiveProfile(scope)
            .map(profile -> PaymentQuickPayConfig.fromJson(profile.configJson()))
            .orElseGet(PaymentQuickPayConfig::defaults);
    }

    @Transactional(readOnly = true)
    public List<QuickPayRecord> findQuickPayRecords(StoreScope scope, QuickPayRecordQuery query, CurrentActor actor) {
        Objects.requireNonNull(scope, "payment_scope_required");
        validateActor(scope, actor);
        return repository.findQuickPayRecords(scope, normalized(query));
    }

    private PaymentIntentCreateResult createNew(StoreScope scope, PaymentIntentCreateCommand command, CurrentActor actor) {
        PaymentMethodProfile profile = profileService.findEffectiveProfile(scope)
            .orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_NOT_FOUND));
        if (!STATUS_ACTIVE.equals(profile.status())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_DISABLED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plusSeconds(SESSION_TTL_SECONDS);
        YearMonth period = YearMonth.from(now);
        int sequence = repository.nextIntentSequence(scope, period);
        return repository.findCreateResultByIdempotencyKey(scope, command.idempotencyKey())
            .map(PaymentIntentCreateResult::asReplay)
            .orElseGet(() -> createNewAfterSequenceLock(scope, command, actor, profile, expiresAt, period, sequence));
    }

    private PaymentIntentCreateResult createNewAfterSequenceLock(
        StoreScope scope,
        PaymentIntentCreateCommand command,
        CurrentActor actor,
        PaymentMethodProfile profile,
        OffsetDateTime expiresAt,
        YearMonth period,
        int sequence
    ) {
        PaymentQuickPayConfig quickPayConfig = PaymentQuickPayConfig.fromJson(profile.configJson());
        String periodText = period.format(PERIOD_FORMATTER);
        String intentNo = "PIT-" + periodText + "-" + "%04d".formatted(sequence);
        LocalDate businessDate = businessDayService.currentOrOpenToday(scope).businessDate();
        int allocatedDisplayNumber = repository.allocateDisplayNumber(scope, businessDate, command.requestedDisplayNumber());
        int displayNumber = command.requestedDisplayNumber() == null
            ? allocatedDisplayNumber + quickPayConfig.dailyStartNumber()
            : allocatedDisplayNumber;
        String paymentReference = PaymentReferenceGenerator.generate(quickPayConfig.referencePrefix(), period, displayNumber);
        String qrPayload = qrPayloadBuilder.build(new PayNowQrPayloadRequest(
            profile.paynowType(),
            profile.paynowMobile(),
            profile.paynowUen(),
            profile.merchantName(),
            command.amount(),
            paymentReference
        ));
        String sessionNo = "PRS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        PaymentIntentDraft intent = new PaymentIntentDraft(
            intentNo,
            command.sourceType(),
            command.sourceId(),
            command.method(),
            command.amount(),
            command.currency(),
            paymentReference,
            expiresAt,
            command.idempotencyKey(),
            command.metadataJson(),
            actor.actorId()
        );
        PaymentSessionDraft session = new PaymentSessionDraft(
            sessionNo,
            displayNumber,
            businessDate,
            command.terminalCode(),
            command.cashierName(),
            qrPayloadsJson(qrPayload),
            expiresAt,
            command.idempotencyKey(),
            actor.actorId()
        );
        return repository.createIntentWithSession(scope, intent, session);
    }

    private static void validateActor(StoreScope scope, CurrentActor actor) {
        if (actor == null || actor.tenantId() == null || !actor.tenantId().equals(scope.tenantId().value()) || !actor.canAccessStore(scope.storeId().value())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static void validateCreateCommand(PaymentIntentCreateCommand command) {
        if (isBlank(command.idempotencyKey())
            || !SUPPORTED_SOURCE_TYPES.contains(command.sourceType())
            || !METHOD_PAYNOW.equals(command.method())
            || command.amount() == null
            || command.amount().compareTo(BigDecimal.ZERO) <= 0
            || !CURRENCY_SGD.equals(command.currency())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static PaymentIntentCreateCommand normalized(PaymentIntentCreateCommand command) {
        if (command == null) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return new PaymentIntentCreateCommand(
            trim(command.idempotencyKey()),
            trimLower(command.sourceType()),
            command.sourceId(),
            trimLower(command.method()),
            command.amount(),
            trim(command.currency()),
            trim(command.terminalCode()),
            trim(command.cashierName()),
            command.requestedDisplayNumber(),
            isBlank(command.metadataJson()) ? "{}" : command.metadataJson().trim()
        );
    }

    private static QuickPayRecordQuery normalized(QuickPayRecordQuery query) {
        QuickPayRecordQuery source = query == null
            ? new QuickPayRecordQuery(null, null, null, null, 80)
            : query;
        String status = trimLower(source.status());
        if (!isBlank(status) && !RECORD_STATUSES.contains(status)) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        int limit = Math.max(1, Math.min(source.limit() <= 0 ? 80 : source.limit(), 200));
        return new QuickPayRecordQuery(
            source.businessDate(),
            status,
            trim(source.terminalCode()),
            trim(source.search()),
            limit
        );
    }

    private static String qrPayloadsJson(String qrPayload) {
        return """
            {"version":1,"defaultDisplay":"paynow","payloads":{"paynow":{"type":"sgqr","payload":"%s","priority":1,"enabled":true}}}
            """.formatted(jsonEscape(qrPayload)).trim();
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimLower(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
