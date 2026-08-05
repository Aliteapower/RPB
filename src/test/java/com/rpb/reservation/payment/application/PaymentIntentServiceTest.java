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
import java.time.ZoneOffset;
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
            Clock.fixed(Instant.parse("2026-08-05T04:10:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsQuickPayIntentSessionReferenceAndQrPayload() {
        when(profileService.findEffectiveProfile(scope)).thenReturn(Optional.of(activeUenProfile()));

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
        assertThat(result.intent().paymentReference()).startsWith("QP-202608-0001-");
        assertThat(result.session().displayNumber()).isEqualTo(1);
        assertThat(result.session().sessionNo()).startsWith("PRS-");
        assertThat(result.session().qrPayloadsJson()).contains(result.intent().paymentReference());
        assertThat(result.nextDisplayNumber()).isEqualTo(2);
    }

    @Test
    void findsSessionBySessionNoForAccessibleStore() {
        PaymentSession session = sampleSession();
        repository.session = session;

        PaymentSession result = service.findSessionByNo(scope, session.sessionNo(), actor);

        assertThat(result).isEqualTo(session);
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

    private PaymentMethodProfile activeUenProfile() {
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
            "{}",
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

    private static final class InMemoryPaymentIntentRepository implements PaymentIntentRepository {
        private PaymentSession session;

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
        public int nextIntentSequence(StoreScope scope, java.time.YearMonth period) {
            return 1;
        }

        @Override
        public int allocateDisplayNumber(StoreScope scope, java.time.LocalDate businessDate, Integer requestedDisplayNumber) {
            return requestedDisplayNumber == null ? 1 : requestedDisplayNumber;
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
            return new PaymentIntentCreateResult(true, false, savedIntent, savedSession, 2);
        }
    }
}
