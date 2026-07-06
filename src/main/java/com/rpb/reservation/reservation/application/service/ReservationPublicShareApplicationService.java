package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nMessageResolver;
import com.rpb.reservation.reservation.application.ReservationPublicShare;
import com.rpb.reservation.reservation.application.ReservationPublicShareError;
import com.rpb.reservation.reservation.application.ReservationPublicShareResult;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareRow;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.rpb.reservation.reservation.application.service.ReservationShareRuntimeTextResolver.ReservationShareRuntimeText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationPublicShareApplicationService {
    private final ReservationPublicShareReadPort readPort;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final ReservationShareRuntimeTextResolver runtimeTextResolver;
    private final PhoneMaskingPolicy phoneMaskingPolicy;
    private final StoreShareDateTimeFormatter dateTimeFormatter;
    private final Supplier<Instant> clock;

    @Autowired
    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        I18nMessageResolver i18nMessageResolver,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter
    ) {
        this(
            readPort,
            templateRenderer,
            templateSeedService,
            i18nMessageResolver,
            phoneMaskingPolicy,
            dateTimeFormatter,
            Instant::now
        );
    }

    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        I18nMessageResolver i18nMessageResolver,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter,
        Supplier<Instant> clock
    ) {
        this.readPort = readPort;
        this.templateRenderer = templateRenderer;
        this.runtimeTextResolver = new ReservationShareRuntimeTextResolver(templateSeedService, i18nMessageResolver);
        this.phoneMaskingPolicy = phoneMaskingPolicy;
        this.dateTimeFormatter = dateTimeFormatter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationPublicShareResult getPublicShare(String token) {
        return getPublicShare(token, null);
    }

    @Transactional(readOnly = true)
    public ReservationPublicShareResult getPublicShare(String token, String locale) {
        if (!hasText(token)) {
            return ReservationPublicShareResult.failure(ReservationPublicShareError.INVALID_TOKEN);
        }

        try {
            ReservationPublicShareRow row = readPort.findByToken(token.trim()).orElse(null);
            if (row == null) {
                return ReservationPublicShareResult.failure(ReservationPublicShareError.TOKEN_NOT_FOUND);
            }
            if ("revoked".equals(row.status())) {
                return ReservationPublicShareResult.failure(ReservationPublicShareError.TOKEN_REVOKED);
            }
            if (row.expiresAt() != null && !row.expiresAt().isAfter(clock.get())) {
                return ReservationPublicShareResult.failure(ReservationPublicShareError.TOKEN_EXPIRED);
            }
            if (row.reservationId() == null) {
                return ReservationPublicShareResult.failure(ReservationPublicShareError.RESERVATION_NOT_FOUND);
            }
            if (row.tenantId() == null || row.storeId() == null) {
                return ReservationPublicShareResult.failure(ReservationPublicShareError.PERSISTENCE_ERROR);
            }

            return ReservationPublicShareResult.success(toShare(row, locale));
        } catch (RuntimeException exception) {
            return ReservationPublicShareResult.failure(ReservationPublicShareError.PERSISTENCE_ERROR);
        }
    }

    private ReservationPublicShare toShare(ReservationPublicShareRow row, String locale) {
        StoreScope scope = new StoreScope(new TenantId(row.tenantId()), new StoreId(row.storeId()));
        ReservationShareRuntimeText runtimeText = runtimeTextResolver.resolve(
            scope,
            locale,
            row.reservationShareNote(),
            row.reservationShareTemplate()
        );
        String storeName = firstText(row.shareDisplayName(), row.storeDisplayName());
        String date = dateTimeFormatter.formatDate(row.reservedStartAt(), row.storeTimezone());
        String time = dateTimeFormatter.formatTime(row.reservedStartAt(), row.storeTimezone());
        String tableCode = clean(row.tableCode());
        boolean tablePending = tableCode.isBlank();
        String partySize = Integer.toString(row.partySize());
        String displayTableCode = tablePending ? runtimeText.tablePendingLabel() : tableCode;
        Map<String, String> variables = variables(row, runtimeText, storeName, date, time, displayTableCode);

        return new ReservationPublicShare(
            clean(row.reservationNo()),
            storeName,
            date,
            time,
            row.partySize(),
            displayTableCode,
            tablePending,
            clean(runtimeText.arrivalNote()),
            clean(row.shareContactPhone()),
            clean(row.shareEmail()),
            clean(row.whatsappBusinessPhoneE164()),
            clean(row.shareAddress()),
            clean(row.googleMapUrl()),
            runtimeText.shareTitle(storeName),
            runtimeText.shareSummary(date, time, partySize),
            renderShareText(runtimeText, variables)
        );
    }

    private String renderShareText(ReservationShareRuntimeText runtimeText, Map<String, String> variables) {
        String template = runtimeText.template();
        if (templateRenderer.unknownVariables(template).isEmpty()) {
            return templateRenderer.render(template, variables);
        }
        return templateRenderer.render(runtimeText.defaultTemplate(), variables);
    }

    private Map<String, String> variables(
        ReservationPublicShareRow row,
        ReservationShareRuntimeText runtimeText,
        String storeName,
        String date,
        String time,
        String tableCode
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String allowedVariable : ReservationShareTemplateCatalog.supportedVariables()) {
            variables.put(allowedVariable, "");
        }
        variables.put("storeName", clean(storeName));
        variables.put("reservationNo", clean(row.reservationNo()));
        variables.put("reservationDate", clean(date));
        variables.put("reservationTime", clean(time));
        variables.put("partySize", Integer.toString(row.partySize()));
        variables.put("tableCode", clean(tableCode));
        variables.put("holdMinutes", holdMinutes(row.reservedStartAt(), row.holdUntilAt()));
        variables.put("contactName", firstText(row.customerName(), row.customerNickname()));
        variables.put("guestSalutation", runtimeText.guestSalutation());
        variables.put("maskedPhone", phoneMaskingPolicy.mask(row.customerPhoneE164()));
        variables.put("storeAddress", clean(row.shareAddress()));
        variables.put("googleMapUrl", clean(row.googleMapUrl()));
        variables.put("storePhone", clean(row.shareContactPhone()));
        variables.put("arrivalNote", clean(runtimeText.arrivalNote()));
        ReservationShareTemplateCatalog.applyLegacyAliases(variables);
        return variables;
    }

    private static String holdMinutes(Instant reservedStartAt, Instant holdUntilAt) {
        if (reservedStartAt == null || holdUntilAt == null) {
            return "15";
        }
        long minutes = Duration.between(reservedStartAt, holdUntilAt).toMinutes();
        return minutes > 0 ? Long.toString(minutes) : "15";
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
}
