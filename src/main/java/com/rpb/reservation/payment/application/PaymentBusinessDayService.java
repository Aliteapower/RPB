package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentIntentRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentBusinessDayService {
    private static final ZoneId DEFAULT_STORE_ZONE = ZoneId.of("Asia/Singapore");
    private static final String STATUS_NOT_OPEN = "not_open";

    private final PaymentIntentRepository repository;
    private final Clock clock;

    @Autowired
    public PaymentBusinessDayService(PaymentIntentRepository repository) {
        this(repository, Clock.systemUTC());
    }

    PaymentBusinessDayService(PaymentIntentRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "payment_business_day_repository_required");
        this.clock = Objects.requireNonNull(clock, "payment_business_day_clock_required");
    }

    @Transactional(readOnly = true)
    public PaymentBusinessDay current(StoreScope scope, CurrentActor actor) {
        validateActor(scope, actor);
        return repository.findOpenBusinessDay(scope)
            .orElseGet(() -> new PaymentBusinessDay(today(), STATUS_NOT_OPEN, null, null));
    }

    @Transactional
    public PaymentBusinessDay openToday(StoreScope scope, CurrentActor actor) {
        validateActor(scope, actor);
        return openToday(scope);
    }

    @Transactional
    public PaymentBusinessDay openToday(StoreScope scope) {
        Objects.requireNonNull(scope, "payment_scope_required");
        return repository.findOpenBusinessDay(scope)
            .orElseGet(() -> repository.openBusinessDay(scope, today(), OffsetDateTime.now(clock)));
    }

    @Transactional
    public PaymentBusinessDay endDay(StoreScope scope, CurrentActor actor) {
        validateActor(scope, actor);
        return repository.closeOpenBusinessDay(scope, OffsetDateTime.now(clock))
            .orElseGet(() -> new PaymentBusinessDay(today(), STATUS_NOT_OPEN, null, null));
    }

    @Transactional
    public PaymentBusinessDay currentOrOpenToday(StoreScope scope) {
        Objects.requireNonNull(scope, "payment_scope_required");
        return repository.findOpenBusinessDay(scope).orElseGet(() -> openToday(scope));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DEFAULT_STORE_ZONE));
    }

    private static void validateActor(StoreScope scope, CurrentActor actor) {
        Objects.requireNonNull(scope, "payment_scope_required");
        if (actor == null || actor.tenantId() == null || !actor.tenantId().equals(scope.tenantId().value()) || !actor.canAccessStore(scope.storeId().value())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
    }
}
