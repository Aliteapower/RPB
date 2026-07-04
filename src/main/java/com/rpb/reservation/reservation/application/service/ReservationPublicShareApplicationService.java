package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.reservation.application.ReservationPublicShare;
import com.rpb.reservation.reservation.application.ReservationPublicShareError;
import com.rpb.reservation.reservation.application.ReservationPublicShareResult;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareRow;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationPublicShareApplicationService {
    private final ReservationPublicShareReadPort readPort;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final ReservationShareTemplateSeedService templateSeedService;
    private final PhoneMaskingPolicy phoneMaskingPolicy;
    private final StoreShareDateTimeFormatter dateTimeFormatter;
    private final Supplier<Instant> clock;

    @Autowired
    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter
    ) {
        this(readPort, templateRenderer, templateSeedService, phoneMaskingPolicy, dateTimeFormatter, Instant::now);
    }

    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        PhoneMaskingPolicy phoneMaskingPolicy,
        StoreShareDateTimeFormatter dateTimeFormatter,
        Supplier<Instant> clock
    ) {
        this.readPort = readPort;
        this.templateRenderer = templateRenderer;
        this.templateSeedService = templateSeedService;
        this.phoneMaskingPolicy = phoneMaskingPolicy;
        this.dateTimeFormatter = dateTimeFormatter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationPublicShareResult getPublicShare(String token) {
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

            return ReservationPublicShareResult.success(toShare(row));
        } catch (RuntimeException exception) {
            return ReservationPublicShareResult.failure(ReservationPublicShareError.PERSISTENCE_ERROR);
        }
    }

    private ReservationPublicShare toShare(ReservationPublicShareRow row) {
        String storeName = firstText(row.shareDisplayName(), row.storeDisplayName());
        String date = dateTimeFormatter.formatDate(row.reservedStartAt(), row.storeTimezone());
        String time = dateTimeFormatter.formatTime(row.reservedStartAt(), row.storeTimezone());
        String tableCode = clean(row.tableCode());
        boolean tablePending = tableCode.isBlank();
        String partySize = Integer.toString(row.partySize());
        Map<String, String> variables = variables(row, storeName, date, time, tablePending ? "待确认" : tableCode);

        return new ReservationPublicShare(
            clean(row.reservationNo()),
            storeName,
            date,
            time,
            row.partySize(),
            tablePending ? "待确认" : tableCode,
            tablePending,
            clean(row.reservationShareNote()),
            clean(row.shareContactPhone()),
            clean(row.shareAddress()),
            clean(row.googleMapUrl()),
            ReservationShareInfoApplicationService.shareTitle(storeName),
            ReservationShareInfoApplicationService.shareSummary(date, time, partySize),
            renderShareText(row, variables)
        );
    }

    private String renderShareText(ReservationPublicShareRow row, Map<String, String> variables) {
        String defaultTemplate = templateSeedService.defaultTemplate();
        String template = hasText(row.reservationShareTemplate())
            ? row.reservationShareTemplate()
            : defaultTemplate;

        if (templateRenderer.unknownVariables(template).isEmpty()) {
            return templateRenderer.render(template, variables);
        }
        return templateRenderer.render(defaultTemplate, variables);
    }

    private Map<String, String> variables(
        ReservationPublicShareRow row,
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
        variables.put("guestSalutation", "先生/女士");
        variables.put("maskedPhone", phoneMaskingPolicy.mask(row.customerPhoneE164()));
        variables.put("storeAddress", clean(row.shareAddress()));
        variables.put("googleMapUrl", clean(row.googleMapUrl()));
        variables.put("storePhone", clean(row.shareContactPhone()));
        variables.put("arrivalNote", clean(row.reservationShareNote()));
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
