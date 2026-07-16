package com.rpb.reservation.customerauth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthIntegrationManagementPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerAuthIntegrationAdminServiceTest {

    private final StoreScope scope = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000902")),
        UUID.fromString("20000000-0000-0000-0000-000000000902")
    );

    @Test
    void emailSettingsPreserveExistingSecretWhenSubmittedSecretIsBlankAndNeverReturnSecret() {
        FakeIntegrationRepository repository = new FakeIntegrationRepository();
        repository.emailSettings = Optional.of(new CustomerEmailSettings(
            true,
            "smtp",
            "old@example.com",
            "Old Sender",
            "smtp.old.example.com",
            587,
            "old-user",
            "existing-secret",
            true,
            true
        ));
        CustomerAuthIntegrationAdminService service = new CustomerAuthIntegrationAdminService(repository, repository, repository);

        CustomerEmailSettings saved = service.saveEmailSettings(scope, new CustomerEmailSettingsCommand(
            true,
            "booking@example.com",
            "Booking",
            "smtp.example.com",
            587,
            "smtp-user",
            " ",
            true
        ));

        assertThat(repository.savedEmailSettings.smtpPassword()).isEqualTo("existing-secret");
        assertThat(saved.secretConfigured()).isTrue();
        assertThat(saved.smtpPassword()).isNull();
        assertThat(saved.fromEmail()).isEqualTo("booking@example.com");
    }

    @Test
    void oauthSettingsPreserveExistingSecretWhenSubmittedSecretIsBlankAndNeverReturnSecret() {
        FakeIntegrationRepository repository = new FakeIntegrationRepository();
        repository.oauthSettings = Optional.of(new CustomerOAuthProviderSettings(
            true,
            "facebook",
            "old-client",
            "existing-secret",
            true
        ));
        CustomerAuthIntegrationAdminService service = new CustomerAuthIntegrationAdminService(repository, repository, repository);

        CustomerOAuthProviderSettings saved = service.saveOAuthProviderSettings(scope, new CustomerOAuthProviderSettingsCommand(
            "facebook",
            true,
            "new-client",
            ""
        ));

        assertThat(repository.savedOAuthSettings.clientSecret()).isEqualTo("existing-secret");
        assertThat(saved.secretConfigured()).isTrue();
        assertThat(saved.clientSecret()).isNull();
        assertThat(saved.clientId()).isEqualTo("new-client");
    }

    private static final class FakeIntegrationRepository implements
        CustomerEmailSettingsRepositoryPort,
        CustomerOAuthSettingsRepositoryPort,
        CustomerAuthIntegrationManagementPort {

        private Optional<CustomerEmailSettings> emailSettings = Optional.empty();
        private Optional<CustomerOAuthProviderSettings> oauthSettings = Optional.empty();
        private CustomerEmailSettings savedEmailSettings;
        private CustomerOAuthProviderSettings savedOAuthSettings;

        @Override
        public Optional<CustomerEmailSettings> findEmailSettings(UUID tenantId, UUID storeId) {
            return emailSettings;
        }

        @Override
        public Optional<CustomerOAuthProviderSettings> findProviderSettings(UUID tenantId, UUID storeId, String provider) {
            return oauthSettings.filter(settings -> settings.provider().equals(provider));
        }

        @Override
        public CustomerEmailSettings saveEmailSettings(StoreScope scope, CustomerEmailSettings settings) {
            savedEmailSettings = settings;
            emailSettings = Optional.of(settings);
            return settings;
        }

        @Override
        public CustomerOAuthProviderSettings saveOAuthProviderSettings(StoreScope scope, CustomerOAuthProviderSettings settings) {
            savedOAuthSettings = settings;
            oauthSettings = Optional.of(settings);
            return settings;
        }
    }
}
