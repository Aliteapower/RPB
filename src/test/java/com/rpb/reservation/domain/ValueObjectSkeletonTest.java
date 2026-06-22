package com.rpb.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.I18nKey;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValueObjectSkeletonTest {

    @Test
    void partySizeMustBePositive() {
        assertThat(new PartySize(4).value()).isEqualTo(4);
        assertThatThrownBy(() -> new PartySize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capacityRangeMustBeOrderedAndCanCheckPartySize() {
        CapacityRange range = new CapacityRange(2, 6);

        assertThat(range.includes(new PartySize(4))).isTrue();
        assertThat(range.includes(new PartySize(7))).isFalse();
        assertThatThrownBy(() -> new CapacityRange(6, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeRangeRequiresEndAfterStart() {
        Instant start = Instant.parse("2026-06-19T10:00:00Z");
        Instant end = Instant.parse("2026-06-19T11:30:00Z");

        assertThat(new TimeRange(start, end).end()).isEqualTo(end);
        assertThatThrownBy(() -> new TimeRange(end, start)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullableE164PhoneSupportsNoPhoneCustomer() {
        assertThat(E164Phone.empty().isPresent()).isFalse();
        assertThat(new E164Phone("+6591234567").isPresent()).isTrue();
        assertThatThrownBy(() -> new E164Phone("91234567")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringBackedValuesRejectBlankInput() {
        assertThat(new I18nKey("reservation.status.confirmed").value()).isEqualTo("reservation.status.confirmed");
        assertThat(new IdempotencyKey("idem-1").value()).isEqualTo("idem-1");
        assertThatThrownBy(() -> new I18nKey(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessDateAndQueueNumberAreLightweightValues() {
        assertThat(new BusinessDate(LocalDate.of(2026, 6, 19)).value()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(new QueueTicketNumber(12).value()).isEqualTo(12);
        assertThatThrownBy(() -> new QueueTicketNumber(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
