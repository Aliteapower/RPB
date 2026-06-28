package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.reservation.application.ReservationPublicShare;
import com.rpb.reservation.reservation.application.ReservationPublicShareError;
import com.rpb.reservation.reservation.application.ReservationPublicShareResult;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareRow;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationPublicShareApplicationService {
    private final ReservationPublicShareReadPort readPort;
    private final StoreShareDateTimeFormatter dateTimeFormatter;
    private final Supplier<Instant> clock;

    @Autowired
    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        StoreShareDateTimeFormatter dateTimeFormatter
    ) {
        this(readPort, dateTimeFormatter, Instant::now);
    }

    public ReservationPublicShareApplicationService(
        ReservationPublicShareReadPort readPort,
        StoreShareDateTimeFormatter dateTimeFormatter,
        Supplier<Instant> clock
    ) {
        this.readPort = readPort;
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
            ReservationShareInfoApplicationService.shareSummary(date, time, partySize)
        );
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
