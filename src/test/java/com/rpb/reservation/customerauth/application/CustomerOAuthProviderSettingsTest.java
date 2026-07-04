package com.rpb.reservation.customerauth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerOAuthProviderSettingsTest {

    @Test
    void usableForPublicLoginRequiresEnabledClientAndFacebookSecret() {
        assertThat(new CustomerOAuthProviderSettings(
            true,
            "google",
            "google-client-id",
            null,
            false
        ).usableForPublicLogin()).isTrue();

        assertThat(new CustomerOAuthProviderSettings(
            true,
            "facebook",
            "facebook-app-id",
            null,
            false
        ).usableForPublicLogin()).isFalse();

        assertThat(new CustomerOAuthProviderSettings(
            true,
            "facebook",
            "facebook-app-id",
            null,
            true
        ).usableForPublicLogin()).isTrue();
    }
}
