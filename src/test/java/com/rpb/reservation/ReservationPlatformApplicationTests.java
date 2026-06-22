package com.rpb.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservationPlatformApplicationTests {

    @Test
    void applicationEntryPointIsDefined() {
        assertThat(ReservationPlatformApplication.class).isNotNull();
    }
}
