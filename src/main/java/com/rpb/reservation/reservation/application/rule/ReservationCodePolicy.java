package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.reservation.value.ReservationCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

public final class ReservationCodePolicy {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final IntSupplier sequenceSupplier;

    public ReservationCodePolicy() {
        this(() -> ThreadLocalRandom.current().nextInt(1, 10_000));
    }

    public ReservationCodePolicy(IntSupplier sequenceSupplier) {
        this.sequenceSupplier = Objects.requireNonNull(sequenceSupplier, "reservation_code_sequence_supplier_required");
    }

    public ReservationCode next(LocalDate businessDate) {
        int sequence = Math.floorMod(sequenceSupplier.getAsInt(), 10_000);
        if (sequence == 0) {
            sequence = 1;
        }
        return new ReservationCode("R-" + DATE_FORMAT.format(businessDate) + "-" + "%04d".formatted(sequence));
    }
}
