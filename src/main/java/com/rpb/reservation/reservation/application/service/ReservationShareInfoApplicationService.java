package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationShareInfo;
import com.rpb.reservation.reservation.application.ReservationShareInfoError;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareTokenPort;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoRow;
import com.rpb.reservation.reservation.application.query.ReservationShareInfoQuery;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationShareInfoApplicationService {
    private static final String CHANNEL_MANUAL_COPY = "manual_copy";

    private final StoreRepositoryPort storeRepository;
    private final ReservationShareInfoReadPort readPort;
    private final ReservationPublicShareTokenPort publicShareTokenPort;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final ReservationShareTemplateSeedService templateSeedService;
    private final PhoneMaskingPolicy phoneMaskingPolicy;
    private final StoreShareDateTimeFormatter dateTimeFormatter;
    private final Supplier<String> tokenSupplier;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();

    @Autowired
    public ReservationShareInfoApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationShareInfoReadPort readPort,
        ReservationPublicShareTokenPort publicShareTokenPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter
    ) {
        this(
            storeRepository,
            readPort,
            publicShareTokenPort,
            templateRenderer,
            templateSeedService,
            phoneMaskingPolicy,
            dateTimeFormatter,
            ReservationShareInfoApplicationService::newToken
        );
    }

    public ReservationShareInfoApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationShareInfoReadPort readPort,
        ReservationPublicShareTokenPort publicShareTokenPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter,
        Supplier<String> tokenSupplier
    ) {
        this.storeRepository = storeRepository;
        this.readPort = readPort;
        this.publicShareTokenPort = publicShareTokenPort;
        this.templateRenderer = templateRenderer;
        this.templateSeedService = templateSeedService;
        this.phoneMaskingPolicy = phoneMaskingPolicy;
        this.dateTimeFormatter = dateTimeFormatter;
        this.tokenSupplier = tokenSupplier;
    }

    @Transactional
    public ReservationShareInfoResult getShareInfo(ReservationShareInfoQuery query) {
        ReservationShareInfoError validationError = validate(query);
        if (validationError != null) {
            return ReservationShareInfoResult.failure(validationError);
        }

        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), new StoreId(query.storeId()));
        Store store = storeRepository.findById(scope).orElse(null);
        if (store == null) {
            return ReservationShareInfoResult.failure(ReservationShareInfoError.STORE_NOT_FOUND);
        }
        if (!scope.equals(store.scope())) {
            return ReservationShareInfoResult.failure(ReservationShareInfoError.STORE_SCOPE_MISMATCH);
        }

        RuleDecision storeAccess = storeAccessPolicy.decide(scope, query.actorId(), query.actorType());
        if (!storeAccess.accepted()) {
            return ReservationShareInfoResult.failure(ReservationShareInfoError.STORE_ACCESS_DENIED);
        }

        try {
            ReservationShareInfoRow row = readPort.findByReservationId(scope, query.reservationId()).orElse(null);
            if (row == null) {
                return ReservationShareInfoResult.failure(ReservationShareInfoError.RESERVATION_NOT_FOUND);
            }
            return ReservationShareInfoResult.success(toShareInfo(scope, row));
        } catch (RuntimeException exception) {
            return ReservationShareInfoResult.failure(ReservationShareInfoError.PERSISTENCE_ERROR);
        }
    }

    private ReservationShareInfo toShareInfo(StoreScope scope, ReservationShareInfoRow row) {
        Map<String, String> variables = variables(row);
        String defaultTemplate = templateSeedService.defaultTemplate();
        String template = hasText(row.reservationShareTemplate())
            ? row.reservationShareTemplate()
            : defaultTemplate;
        String shareText;
        if (templateRenderer.unknownVariables(template).isEmpty()) {
            shareText = templateRenderer.render(template, variables);
        } else {
            shareText = templateRenderer.render(defaultTemplate, variables);
        }
        String maskedPhone = variables.get("maskedPhone");
        String token = publicShareTokenPort.ensureActiveToken(scope, row.reservationId(), tokenSupplier.get());
        String storeName = variables.get("storeName");
        String date = variables.get("reservationDate");
        String time = variables.get("reservationTime");
        String partySize = variables.get("partySize");
        return new ReservationShareInfo(
            row.reservationId(),
            clean(row.reservationNo()),
            CHANNEL_MANUAL_COPY,
            shareText,
            maskedPhone,
            hasText(row.customerPhoneE164()),
            false,
            null,
            token,
            sharePath(token),
            shareTitle(storeName),
            shareSummary(date, time, partySize)
        );
    }

    private Map<String, String> variables(ReservationShareInfoRow row) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String allowedVariable : ReservationShareTemplateCatalog.allowedVariables()) {
            variables.put(allowedVariable, "");
        }
        variables.put("storeName", firstText(row.shareDisplayName(), row.storeDisplayName()));
        variables.put("reservationNo", clean(row.reservationNo()));
        variables.put("reservationDate", dateTimeFormatter.formatDate(row.reservedStartAt(), row.storeTimezone()));
        variables.put("reservationTime", dateTimeFormatter.formatTime(row.reservedStartAt(), row.storeTimezone()));
        variables.put("partySize", Integer.toString(row.partySize()));
        variables.put("tableCode", firstText(row.tableCode(), "待确认"));
        variables.put("holdMinutes", holdMinutes(row.reservedStartAt(), row.holdUntilAt()));
        variables.put("contactName", firstText(row.customerName(), row.customerNickname()));
        variables.put("guestSalutation", "先生/女士");
        variables.put("maskedPhone", phoneMaskingPolicy.mask(row.customerPhoneE164()));
        variables.put("storeAddress", clean(row.shareAddress()));
        variables.put("googleMapUrl", clean(row.googleMapUrl()));
        variables.put("storePhone", clean(row.shareContactPhone()));
        variables.put("arrivalNote", clean(row.reservationShareNote()));
        return variables;
    }

    private static String holdMinutes(Instant reservedStartAt, Instant holdUntilAt) {
        if (reservedStartAt == null || holdUntilAt == null) {
            return "15";
        }
        long minutes = Duration.between(reservedStartAt, holdUntilAt).toMinutes();
        return minutes > 0 ? Long.toString(minutes) : "15";
    }

    private static ReservationShareInfoError validate(ReservationShareInfoQuery query) {
        if (
            query == null
                || query.tenantId() == null
                || query.storeId() == null
                || query.reservationId() == null
                || query.actorId() == null
                || !hasText(query.actorType())
        ) {
            return ReservationShareInfoError.INVALID_COMMAND;
        }
        return null;
    }

    private static String firstText(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        return clean(second);
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String sharePath(String token) {
        return "/reservation-share/" + token;
    }

    static String shareTitle(String storeName) {
        return firstText(storeName, "门店") + " 订位确认";
    }

    static String shareSummary(String date, String time, String partySize) {
        String dateTime = (clean(date) + " " + clean(time)).trim();
        String party = hasText(partySize) ? partySize.trim() + "人" : "";
        if (!dateTime.isBlank() && !party.isBlank()) {
            return dateTime + " · " + party;
        }
        return dateTime.isBlank() ? party : dateTime;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        Holder.RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class Holder {
        private static final SecureRandom RANDOM = new SecureRandom();
    }
}
