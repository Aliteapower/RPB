package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentIntentRepository;
import com.rpb.reservation.payment.provider.PayNowQrPayloadBuilder;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentIntentServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
    private final CurrentActor actor = CurrentActor.storeStaff(
        TENANT_ID,
        ACTOR_ID,
        "tenant_staff",
        Set.of("store_staff"),
        Set.of("payment.intent.create"),
        Set.of(STORE_ID)
    );
    private PaymentMethodProfileService profileService;
    private InMemoryPaymentIntentRepository repository;
    private PaymentIntentService service;

    @BeforeEach
    void setUp() {
        profileService = mock(PaymentMethodProfileService.class);
        repository = new InMemoryPaymentIntentRepository();
        service = new PaymentIntentService(
            profileService,
            repository,
            new PayNowQrPayloadBuilder(),
            new PaymentBusinessDayService(
                repository,
                Clock.fixed(Instant.parse("2026-08-05T04:10:00Z"), ZoneOffset.UTC)
            ),
            Clock.fixed(Instant.parse("2026-08-05T04:10:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsQuickPayIntentSessionReferenceAndQrPayload() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfile()));
        repository.openBusinessDay = new PaymentBusinessDay(
            LocalDate.parse("2026-08-04"),
            "open",
            OffsetDateTime.parse("2026-08-04T16:30:00Z"),
            null
        );

        PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-20260805-001",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("18.80"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor);

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isFalse();
        assertThat(result.intent().sourceType()).isEqualTo("quick_pay");
        assertThat(result.intent().intentNo()).isEqualTo("PIT-202608-0001");
        assertThat(result.intent().paymentReference()).matches("QP202608040001[ACDEFGHJKMNPQRTVWXY]{4}");
        assertThat(result.intent().paymentReference()).isEqualTo("QP202608040001XRTD");
        assertThat(result.session().displayNumber()).isEqualTo(1);
        assertThat(result.session().businessDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(result.session().sessionNo()).startsWith("PRS-");
        assertThat(result.session().qrPayloadsJson()).contains(result.intent().paymentReference());
        assertThat(result.nextDisplayNumber()).isEqualTo(2);
    }

    @Test
    void createsQuickPayAgainstLatestOpenBusinessDayAcrossCalendarDate() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfile()));
        repository.openBusinessDay = new PaymentBusinessDay(
            LocalDate.parse("2026-08-04"),
            "open",
            OffsetDateTime.parse("2026-08-04T16:30:00Z"),
            null
        );

        PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-cross-date",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("8.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor);

        assertThat(result.session().businessDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(repository.allocatedBusinessDate).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(repository.openedBusinessDate).isNull();
    }

    @Test
    void autoOpensTodayWhenNoBusinessDayIsOpenSoQrCreationStillSucceeds() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfile()));

        PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-auto-open",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("9.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor);

        assertThat(result.session().businessDate()).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(repository.openedBusinessDate).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(repository.allocatedBusinessDate).isEqualTo(LocalDate.parse("2026-08-05"));
    }

    @Test
    void endDayClosesCurrentBusinessDayBeforeNextQuickPayOpensToday() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfile()));
        repository.openBusinessDay = new PaymentBusinessDay(
            LocalDate.parse("2026-08-04"),
            "open",
            OffsetDateTime.parse("2026-08-04T16:30:00Z"),
            null
        );
        PaymentBusinessDayService businessDayService = new PaymentBusinessDayService(
            repository,
            Clock.fixed(Instant.parse("2026-08-05T00:05:00Z"), ZoneOffset.UTC)
        );

        PaymentBusinessDay closed = businessDayService.endDay(scope, actor);

        assertThat(closed.businessDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(closed.status()).isEqualTo("closed");
        assertThat(closed.closedAt()).isEqualTo(OffsetDateTime.parse("2026-08-05T00:05:00Z"));
        assertThat(repository.closedBusinessDate).isEqualTo(LocalDate.parse("2026-08-04"));

        PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-after-end-day",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("9.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor);

        assertThat(result.session().businessDate()).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(repository.openedBusinessDate).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(repository.allocatedBusinessDate).isEqualTo(LocalDate.parse("2026-08-05"));
    }

    @Test
    void openTodayReturnsCurrentOpenBusinessDayUntilEndDayIsDone() {
        repository.openBusinessDay = new PaymentBusinessDay(
            LocalDate.parse("2026-08-04"),
            "open",
            OffsetDateTime.parse("2026-08-04T16:30:00Z"),
            null
        );
        PaymentBusinessDayService businessDayService = new PaymentBusinessDayService(
            repository,
            Clock.fixed(Instant.parse("2026-08-05T00:05:00Z"), ZoneOffset.UTC)
        );

        PaymentBusinessDay businessDay = businessDayService.openToday(scope, actor);

        assertThat(businessDay.businessDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(businessDay.status()).isEqualTo("open");
        assertThat(repository.openedBusinessDate).isNull();
    }

    @Test
    void appliesQuickPayReferencePrefixAndDailyStartNumberFromProfileConfig() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfileWithConfig(
            "{\"quickPay\":{\"referencePrefix\":\"AB\",\"dailyStartNumber\":10,\"presetAmounts\":[5,10,20]}}"
        )));

        PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-20260805-002",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("5.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor);

        assertThat(result.intent().paymentReference()).matches("AB202608050011[ACDEFGHJKMNPQRTVWXY]{4}");
        assertThat(result.session().displayNumber()).isEqualTo(11);
        assertThat(result.nextDisplayNumber()).isEqualTo(12);
    }

    @Test
    void rejectsRequestedDisplayNumberAboveCompactReferenceLimitWithBusinessError() {
        assertThatThrownBy(() -> service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-display-overflow-requested",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("5.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            10000,
            "{}"
        ), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void rejectsAutomaticDisplayNumberOverflowWithBusinessError() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfileWithConfig(
            "{\"quickPay\":{\"referencePrefix\":\"QP\",\"dailyStartNumber\":9999}}"
        )));
        repository.allocatedDisplayNumber = 1;

        assertThatThrownBy(() -> service.createQuickPay(scope, new PaymentIntentCreateCommand(
            "quick-pay-display-overflow-auto",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("5.00"),
            "SGD",
            "COUNTER-1",
            "Alice",
            null,
            "{}"
        ), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void findsSessionBySessionNoForAccessibleStore() {
        PaymentSession session = sampleSession();
        repository.session = session;

        PaymentSession result = service.findSessionByNo(scope, session.sessionNo(), actor);

        assertThat(result).isEqualTo(session);
    }

    @Test
    void manuallyConfirmsPendingQuickPaySessionAsPaid() {
        PaymentManualConfirmResult confirmed = sampleManualConfirmResult(false, false, "paid", "paid");
        repository.manualConfirmResult = Optional.of(confirmed);

        PaymentManualConfirmResult result = service.manualConfirmQuickPay(scope, new PaymentManualConfirmCommand(
            " PRS-ABCDEF1234567890 ",
            " manual-confirm-001 ",
            " T1 "
        ), actor);

        assertThat(result).isEqualTo(confirmed);
        assertThat(repository.lastManualConfirmSessionNo).isEqualTo("PRS-ABCDEF1234567890");
        assertThat(repository.lastManualConfirmIdempotencyKey).isEqualTo("manual-confirm-001");
        assertThat(repository.lastManualConfirmTerminalCode).isEqualTo("T1");
        assertThat(repository.lastManualConfirmActorId).isEqualTo(ACTOR_ID);
    }

    @Test
    void rejectsBlankManualConfirmIdempotencyKey() {
        assertThatThrownBy(() -> service.manualConfirmQuickPay(scope, new PaymentManualConfirmCommand(
            "PRS-ABCDEF1234567890",
            " ",
            "T1"
        ), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void rejectsManualConfirmWhenRepositoryCannotConfirmSession() {
        repository.manualConfirmResult = Optional.empty();

        assertThatThrownBy(() -> service.manualConfirmQuickPay(scope, new PaymentManualConfirmCommand(
            "PRS-ABCDEF1234567890",
            "manual-confirm-missing",
            "T1"
        ), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.PAYMENT_INTENT_STATE_CONFLICT);
    }

    @Test
    void returnsTerminalConfigWithoutExposingPayNowMerchantDetails() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfileWithConfig(
            "{\"quickPay\":{\"referencePrefix\":\"AB\",\"dailyStartNumber\":3,\"presetAmounts\":[1,2.5]}}"
        )));

        PaymentQuickPayConfig result = service.findTerminalConfig(scope, actor);

        assertThat(result.referencePrefix()).isEqualTo("AB");
        assertThat(result.dailyStartNumber()).isEqualTo(3);
        assertThat(result.presetAmounts()).containsExactly(new BigDecimal("1"), new BigDecimal("2.5"));
    }

    @Test
    void rejectsSessionLookupForActorOutsideStoreScope() {
        CurrentActor foreignActor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "tenant_staff",
            Set.of("store_staff"),
            Set.of("payment.intent.view"),
            Set.of(UUID.fromString("20000000-0000-0000-0000-000000000002"))
        );

        assertThatThrownBy(() -> service.findSessionByNo(scope, "PRS-ABC", foreignActor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void findsQuickPayRecordsWithNormalizedQuery() {
        QuickPayRecord record = sampleQuickPayRecord();
        repository.quickPayRecords = List.of(record);

        List<QuickPayRecord> result = service.findQuickPayRecords(scope, new QuickPayRecordQuery(
            LocalDate.parse("2026-08-05"),
            "PENDING",
            " T1 ",
            " Alice ",
            " QP-202608 ",
            500
        ), actor);

        assertThat(result).containsExactly(record);
        assertThat(repository.lastRecordQuery).isEqualTo(new QuickPayRecordQuery(
            LocalDate.parse("2026-08-05"),
            "pending",
            "T1",
            "Alice",
            "QP-202608",
            200
        ));
    }

    @Test
    void rejectsUnknownQuickPayRecordStatus() {
        assertThatThrownBy(() -> service.findQuickPayRecords(scope, new QuickPayRecordQuery(
            null,
            "unknown",
            null,
            null,
            null,
            80
        ), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting("code")
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    private PaymentMethodProfile activeUenProfile() {
        return activeUenProfileWithConfig("{}");
    }

    private PaymentMethodProfile activeUenProfileWithConfig(String configJson) {
        return new PaymentMethodProfile(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            TENANT_ID,
            STORE_ID,
            "paynow",
            "active",
            "uen",
            null,
            "202012345A",
            "RPB Demo Restaurant",
            "SGD",
            configJson,
            0,
            java.time.OffsetDateTime.parse("2026-08-05T04:00:00Z"),
            java.time.OffsetDateTime.parse("2026-08-05T04:00:00Z")
        );
    }

    private PaymentSession sampleSession() {
        return new PaymentSession(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            TENANT_ID,
            STORE_ID,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "PRS-ABCDEF1234567890",
            7,
            java.time.LocalDate.parse("2026-08-05"),
            "pending",
            "{\"version\":1,\"payloads\":{\"paynow\":{\"payload\":\"000201\"}}}",
            java.time.OffsetDateTime.parse("2026-08-05T04:12:00Z"),
            0
        );
    }

    private QuickPayRecord sampleQuickPayRecord() {
        return new QuickPayRecord(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "PIT-202608-0001",
            "PRS-ABCDEF1234567890",
            7,
            LocalDate.parse("2026-08-05"),
            new BigDecimal("18.80"),
            "SGD",
            "QP-202608-0007-ABCD",
            "pending",
            "pending",
            "T1",
            "Alice",
            OffsetDateTime.parse("2026-08-05T04:10:00Z"),
            OffsetDateTime.parse("2026-08-05T04:12:00Z")
        );
    }

    private PaymentManualConfirmResult sampleManualConfirmResult(
        boolean replayed,
        boolean alreadyConfirmed,
        String intentStatus,
        String sessionStatus
    ) {
        PaymentIntent intent = new PaymentIntent(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            TENANT_ID,
            STORE_ID,
            "PIT-202608-0001",
            "quick_pay",
            null,
            "paynow",
            new BigDecimal("18.80"),
            "SGD",
            "QP202608050007XRTD",
            intentStatus,
            OffsetDateTime.parse("2026-08-05T04:12:00Z"),
            1
        );
        PaymentSession session = new PaymentSession(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            TENANT_ID,
            STORE_ID,
            intent.id(),
            "PRS-ABCDEF1234567890",
            7,
            LocalDate.parse("2026-08-05"),
            sessionStatus,
            "{\"version\":1,\"payloads\":{\"paynow\":{\"payload\":\"000201\"}}}",
            OffsetDateTime.parse("2026-08-05T04:12:00Z"),
            1
        );
        return new PaymentManualConfirmResult(true, replayed, alreadyConfirmed, intent, session);
    }

    private static final class InMemoryPaymentIntentRepository implements PaymentIntentRepository {
        private PaymentSession session;
        private PaymentBusinessDay openBusinessDay;
        private LocalDate openedBusinessDate;
        private LocalDate closedBusinessDate;
        private LocalDate allocatedBusinessDate;
        private List<QuickPayRecord> quickPayRecords = List.of();
        private QuickPayRecordQuery lastRecordQuery;
        private int allocatedDisplayNumber = 1;
        private Optional<PaymentManualConfirmResult> manualConfirmResult = Optional.empty();
        private String lastManualConfirmSessionNo;
        private String lastManualConfirmIdempotencyKey;
        private String lastManualConfirmTerminalCode;
        private UUID lastManualConfirmActorId;

        @Override
        public Optional<PaymentIntentCreateResult> findCreateResultByIdempotencyKey(StoreScope scope, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public Optional<PaymentSession> findSessionByNo(StoreScope scope, String sessionNo) {
            return session != null && session.sessionNo().equals(sessionNo)
                ? Optional.of(session)
                : Optional.empty();
        }

        @Override
        public List<QuickPayRecord> findQuickPayRecords(StoreScope scope, QuickPayRecordQuery query) {
            lastRecordQuery = query;
            return quickPayRecords;
        }

        @Override
        public Optional<PaymentManualConfirmResult> manualConfirmQuickPay(
            StoreScope scope,
            String sessionNo,
            String idempotencyKey,
            UUID actorId,
            String terminalCode
        ) {
            lastManualConfirmSessionNo = sessionNo;
            lastManualConfirmIdempotencyKey = idempotencyKey;
            lastManualConfirmTerminalCode = terminalCode;
            lastManualConfirmActorId = actorId;
            return manualConfirmResult;
        }

        @Override
        public int nextIntentSequence(StoreScope scope, java.time.YearMonth period) {
            return 1;
        }

        @Override
        public int allocateDisplayNumber(StoreScope scope, java.time.LocalDate businessDate, Integer requestedDisplayNumber) {
            allocatedBusinessDate = businessDate;
            return requestedDisplayNumber == null ? allocatedDisplayNumber : requestedDisplayNumber;
        }

        @Override
        public Optional<PaymentBusinessDay> findOpenBusinessDay(StoreScope scope) {
            return Optional.ofNullable(openBusinessDay);
        }

        @Override
        public PaymentBusinessDay openBusinessDay(StoreScope scope, LocalDate businessDate, OffsetDateTime openedAt) {
            openedBusinessDate = businessDate;
            openBusinessDay = new PaymentBusinessDay(businessDate, "open", openedAt, null);
            return openBusinessDay;
        }

        @Override
        public Optional<PaymentBusinessDay> closeOpenBusinessDay(StoreScope scope, OffsetDateTime closedAt) {
            if (openBusinessDay == null) {
                return Optional.empty();
            }
            closedBusinessDate = openBusinessDay.businessDate();
            PaymentBusinessDay closed = new PaymentBusinessDay(
                openBusinessDay.businessDate(),
                "closed",
                openBusinessDay.openedAt(),
                closedAt
            );
            openBusinessDay = null;
            return Optional.of(closed);
        }

        @Override
        public PaymentIntentCreateResult createIntentWithSession(
            StoreScope scope,
            PaymentIntentDraft intent,
            PaymentSessionDraft session
        ) {
            PaymentIntent savedIntent = new PaymentIntent(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                scope.tenantId().value(),
                scope.storeId().value(),
                intent.intentNo(),
                intent.sourceType(),
                intent.sourceId(),
                intent.method(),
                intent.amount(),
                intent.currency(),
                intent.paymentReference(),
                "pending",
                intent.expiresAt(),
                0
            );
            PaymentSession savedSession = new PaymentSession(
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                scope.tenantId().value(),
                scope.storeId().value(),
                savedIntent.id(),
                session.sessionNo(),
                session.displayNumber(),
                session.businessDate(),
                "pending",
                session.qrPayloadsJson(),
                session.expiresAt(),
                0
            );
            return new PaymentIntentCreateResult(true, false, savedIntent, savedSession, savedSession.displayNumber() + 1);
        }
    }
}
