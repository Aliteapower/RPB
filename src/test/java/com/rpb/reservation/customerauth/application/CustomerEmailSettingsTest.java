package com.rpb.reservation.customerauth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerEmailSettingsTest {

    @Test
    void usableForLoginCodeRequiresEnabledSmtpWithAddressHostAndPort() {
        assertThat(new CustomerEmailSettings(
            true,
            "smtp",
            "booking@example.com",
            "Booking",
            "smtp.example.com",
            587,
            "smtp-user",
            null,
            true,
            true
        ).usableForLoginCode()).isTrue();

        assertThat(new CustomerEmailSettings(
            true,
            "smtp",
            "booking@example.com",
            "Booking",
            "smtp.example.com",
            587,
            null,
            null,
            true,
            false
        ).usableForLoginCode()).isTrue();

        assertThat(new CustomerEmailSettings(
            true,
            "smtp",
            "booking@example.com",
            "Booking",
            "smtp.example.com",
            587,
            "smtp-user",
            null,
            true,
            false
        ).usableForLoginCode()).isFalse();

        assertThat(CustomerEmailSettings.disabled().usableForLoginCode()).isFalse();
        assertThat(new CustomerEmailSettings(
            true,
            "smtp",
            "booking@example.com",
            "Booking",
            null,
            587,
            "smtp-user",
            null,
            true,
            true
        ).usableForLoginCode()).isFalse();
    }
}
