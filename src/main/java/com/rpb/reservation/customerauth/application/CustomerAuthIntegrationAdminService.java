package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthIntegrationManagementPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuthIntegrationAdminService {

    private static final String PROVIDER_SMTP = "smtp";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_FACEBOOK = "facebook";

    private final CustomerEmailSettingsRepositoryPort emailSettingsRepository;
    private final CustomerOAuthSettingsRepositoryPort oauthSettingsRepository;
    private final CustomerAuthIntegrationManagementPort managementPort;

    public CustomerAuthIntegrationAdminService(
        CustomerEmailSettingsRepositoryPort emailSettingsRepository,
        CustomerOAuthSettingsRepositoryPort oauthSettingsRepository,
        CustomerAuthIntegrationManagementPort managementPort
    ) {
        this.emailSettingsRepository = emailSettingsRepository;
        this.oauthSettingsRepository = oauthSettingsRepository;
        this.managementPort = managementPort;
    }

    @Transactional(readOnly = true)
    public CustomerEmailSettings getEmailSettings(StoreScope scope) {
        return scrubEmailSecret(emailSettingsRepository
            .findEmailSettings(scope.tenantId().value(), scope.storeId().value())
            .orElse(CustomerEmailSettings.disabled()));
    }

    @Transactional
    public CustomerEmailSettings saveEmailSettings(StoreScope scope, CustomerEmailSettingsCommand command) {
        CustomerEmailSettings current = emailSettingsRepository
            .findEmailSettings(scope.tenantId().value(), scope.storeId().value())
            .orElse(CustomerEmailSettings.disabled());
        CustomerEmailSettings next = new CustomerEmailSettings(
            optional(command == null ? null : command.enabled(), current.enabled()),
            PROVIDER_SMTP,
            trimToNull(command == null ? null : command.fromEmail()),
            trimToNull(command == null ? null : command.fromName()),
            trimToNull(command == null ? null : command.smtpHost()),
            bounded(command == null ? null : command.smtpPort(), current.smtpPort(), 1, 65535),
            trimToNull(command == null ? null : command.smtpUsername()),
            firstSecret(command == null ? null : command.smtpPassword(), current.smtpPassword()),
            optional(command == null ? null : command.smtpStartTls(), current.smtpStartTls()),
            hasText(command == null ? null : command.smtpPassword()) || current.secretConfigured()
        );
        return scrubEmailSecret(managementPort.saveEmailSettings(scope, next));
    }

    @Transactional(readOnly = true)
    public CustomerOAuthProviderSettings getOAuthProviderSettings(StoreScope scope, String provider) {
        String normalizedProvider = normalizeOAuthProvider(provider);
        return scrubOAuthSecret(oauthSettingsRepository
            .findProviderSettings(scope.tenantId().value(), scope.storeId().value(), normalizedProvider)
            .orElse(CustomerOAuthProviderSettings.disabled(normalizedProvider)));
    }

    @Transactional
    public CustomerOAuthProviderSettings saveOAuthProviderSettings(
        StoreScope scope,
        CustomerOAuthProviderSettingsCommand command
    ) {
        String provider = normalizeOAuthProvider(command == null ? null : command.provider());
        CustomerOAuthProviderSettings current = oauthSettingsRepository
            .findProviderSettings(scope.tenantId().value(), scope.storeId().value(), provider)
            .orElse(CustomerOAuthProviderSettings.disabled(provider));
        CustomerOAuthProviderSettings next = new CustomerOAuthProviderSettings(
            optional(command == null ? null : command.enabled(), current.enabled()),
            provider,
            trimToNull(command == null ? null : command.clientId()),
            firstSecret(command == null ? null : command.clientSecret(), current.clientSecret()),
            hasText(command == null ? null : command.clientSecret()) || current.secretConfigured()
        );
        return scrubOAuthSecret(managementPort.saveOAuthProviderSettings(scope, next));
    }

    private static CustomerEmailSettings scrubEmailSecret(CustomerEmailSettings settings) {
        return new CustomerEmailSettings(
            settings.enabled(),
            settings.provider(),
            settings.fromEmail(),
            settings.fromName(),
            settings.smtpHost(),
            settings.smtpPort(),
            settings.smtpUsername(),
            null,
            settings.smtpStartTls(),
            settings.secretConfigured() || hasText(settings.smtpPassword())
        );
    }

    private static CustomerOAuthProviderSettings scrubOAuthSecret(CustomerOAuthProviderSettings settings) {
        return new CustomerOAuthProviderSettings(
            settings.enabled(),
            settings.provider(),
            settings.clientId(),
            null,
            settings.secretConfigured() || hasText(settings.clientSecret())
        );
    }

    private static String normalizeOAuthProvider(String provider) {
        String normalized = trimToNull(provider);
        normalized = normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
        if (PROVIDER_GOOGLE.equals(normalized) || PROVIDER_FACEBOOK.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("oauth_provider_invalid");
    }

    private static boolean optional(Boolean candidate, boolean fallback) {
        return candidate == null ? fallback : candidate;
    }

    private static int bounded(Integer candidate, int fallback, int min, int max) {
        int value = candidate == null ? fallback : candidate;
        return Math.max(min, Math.min(max, value));
    }

    private static String firstSecret(String candidate, String fallback) {
        String normalized = trimToNull(candidate);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
