package com.rpb.reservation.common.time;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Store-local business date derived from Store timezone.
 */
public record BusinessDate(LocalDate value) {

    public BusinessDate {
        Objects.requireNonNull(value, "business_date_required");
    }
}
