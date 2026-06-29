package com.rpb.reservation.publicbooking.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.customerauth.application.CustomerAuthIntegrationAdminService;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import com.rpb.reservation.customerauth.application.CustomerEmailSettingsCommand;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettingsCommand;
import com.rpb.reservation.publicbooking.application.PublicBookingAdminService;
import com.rpb.reservation.publicbooking.application.PublicBookingQuotaOverride;
import com.rpb.reservation.publicbooking.application.PublicBookingQuotaOverrideCommand;
import com.rpb.reservation.publicbooking.application.PublicBookingSettings;
import com.rpb.reservation.publicbooking.application.PublicBookingSettingsCommand;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorResponse;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.tenantadmin.api.TenantAdminScopeResolver;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/public-booking")
public class TenantAdminPublicBookingController {

    private final PublicBookingAdminService service;
    private final CustomerAuthIntegrationAdminService authIntegrationService;
    private final TenantAdminScopeResolver scopeResolver;

    public TenantAdminPublicBookingController(
        PublicBookingAdminService service,
        CustomerAuthIntegrationAdminService authIntegrationService,
        TenantAdminScopeResolver scopeResolver
    ) {
        this.service = service;
        this.authIntegrationService = authIntegrationService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/settings")
    public ResponseEntity<SettingsResponse> getSettings(@PathVariable UUID storeId) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(SettingsResponse.from(service.getSettings(scope)));
    }

    @PutMapping("/settings")
    public ResponseEntity<SettingsResponse> saveSettings(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PublicBookingSettingsRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        PublicBookingSettingsCommand command = request == null ? null : request.toCommand();
        return ResponseEntity.ok(SettingsResponse.from(service.saveSettings(scope, command)));
    }

    @PutMapping("/quota-overrides")
    public ResponseEntity<QuotaOverrideResponse> saveQuotaOverride(
        @PathVariable UUID storeId,
        @RequestBody PublicBookingQuotaOverrideRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        PublicBookingQuotaOverride override = service.saveQuotaOverride(scope, request.toCommand());
        return ResponseEntity.ok(QuotaOverrideResponse.from(override));
    }

    @GetMapping("/quota-overrides")
    public ResponseEntity<QuotaOverrideResponse> getQuotaOverride(
        @PathVariable UUID storeId,
        @RequestParam("businessDate") LocalDate businessDate,
        @RequestParam(value = "periodKey", required = false) String periodKey
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        Optional<PublicBookingQuotaOverride> override = service.findQuotaOverride(scope, new BusinessDate(businessDate), periodKey);
        return override
            .map(value -> ResponseEntity.ok(QuotaOverrideResponse.from(value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/customer-auth/email-settings")
    public ResponseEntity<EmailSettingsResponse> getEmailSettings(@PathVariable UUID storeId) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(EmailSettingsResponse.from(authIntegrationService.getEmailSettings(scope)));
    }

    @PutMapping("/customer-auth/email-settings")
    public ResponseEntity<EmailSettingsResponse> saveEmailSettings(
        @PathVariable UUID storeId,
        @RequestBody(required = false) EmailSettingsRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        CustomerEmailSettingsCommand command = request == null ? null : request.toCommand();
        return ResponseEntity.ok(EmailSettingsResponse.from(authIntegrationService.saveEmailSettings(scope, command)));
    }

    @GetMapping("/customer-auth/oauth-providers/{provider}")
    public ResponseEntity<OAuthProviderSettingsResponse> getOAuthProviderSettings(
        @PathVariable UUID storeId,
        @PathVariable String provider
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(OAuthProviderSettingsResponse.from(
            authIntegrationService.getOAuthProviderSettings(scope, provider)
        ));
    }

    @PutMapping("/customer-auth/oauth-providers/{provider}")
    public ResponseEntity<OAuthProviderSettingsResponse> saveOAuthProviderSettings(
        @PathVariable UUID storeId,
        @PathVariable String provider,
        @RequestBody(required = false) OAuthProviderSettingsRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        CustomerOAuthProviderSettingsCommand command = request == null
            ? new CustomerOAuthProviderSettingsCommand(provider, null, null, null)
            : request.toCommand(provider);
        return ResponseEntity.ok(OAuthProviderSettingsResponse.from(
            authIntegrationService.saveOAuthProviderSettings(scope, command)
        ));
    }

    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleApiException(TenantAdminApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<TenantAdminApiErrorResponse> handleBadRequest(RuntimeException exception) {
        return apiError(TenantAdminApiErrorCode.REQUEST_INVALID);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(TenantAdminApiErrorCode.PERSISTENCE_ERROR);
    }

    private static ResponseEntity<TenantAdminApiErrorResponse> apiError(TenantAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(TenantAdminApiErrorResponse.of(code));
    }

    public record PublicBookingSettingsRequest(
        Boolean enabled,
        Boolean requireCustomerLogin,
        String defaultQuotaMode,
        Integer defaultQuotaPercent,
        Integer defaultTableCount,
        Integer defaultGuestCount,
        Integer minLeadMinutes,
        Integer maxAdvanceDays
    ) {
        PublicBookingSettingsCommand toCommand() {
            return new PublicBookingSettingsCommand(
                enabled,
                requireCustomerLogin,
                defaultQuotaMode,
                defaultQuotaPercent,
                defaultTableCount,
                defaultGuestCount,
                minLeadMinutes,
                maxAdvanceDays
            );
        }
    }

    public record PublicBookingQuotaOverrideRequest(
        LocalDate businessDate,
        String periodKey,
        String quotaMode,
        Integer quotaPercent,
        Integer tableCount,
        Integer guestCount
    ) {
        PublicBookingQuotaOverrideCommand toCommand() {
            return new PublicBookingQuotaOverrideCommand(
                businessDate,
                periodKey,
                quotaMode,
                quotaPercent,
                tableCount,
                guestCount
            );
        }
    }

    public record EmailSettingsRequest(
        Boolean enabled,
        String fromEmail,
        String fromName,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        Boolean smtpStartTls
    ) {
        CustomerEmailSettingsCommand toCommand() {
            return new CustomerEmailSettingsCommand(
                enabled,
                fromEmail,
                fromName,
                smtpHost,
                smtpPort,
                smtpUsername,
                smtpPassword,
                smtpStartTls
            );
        }
    }

    public record OAuthProviderSettingsRequest(
        Boolean enabled,
        String clientId,
        String clientSecret
    ) {
        CustomerOAuthProviderSettingsCommand toCommand(String provider) {
            return new CustomerOAuthProviderSettingsCommand(provider, enabled, clientId, clientSecret);
        }
    }

    public record SettingsResponse(
        boolean success,
        boolean enabled,
        boolean requireCustomerLogin,
        String defaultQuotaMode,
        Integer defaultQuotaPercent,
        Integer defaultTableCount,
        Integer defaultGuestCount,
        int minLeadMinutes,
        int maxAdvanceDays
    ) {
        static SettingsResponse from(PublicBookingSettings settings) {
            return new SettingsResponse(
                true,
                settings.enabled(),
                settings.requireCustomerLogin(),
                settings.defaultQuotaMode(),
                settings.defaultQuotaPercent(),
                settings.defaultTableCount(),
                settings.defaultGuestCount(),
                settings.minLeadMinutes(),
                settings.maxAdvanceDays()
            );
        }
    }

    public record QuotaOverrideResponse(
        boolean success,
        String periodKey,
        String quotaMode,
        Integer quotaPercent,
        Integer tableCount,
        Integer guestCount
    ) {
        static QuotaOverrideResponse from(PublicBookingQuotaOverride override) {
            return new QuotaOverrideResponse(
                true,
                override.periodKey(),
                override.quotaMode(),
                override.quotaPercent(),
                override.tableCount(),
                override.guestCount()
            );
        }
    }

    public record EmailSettingsResponse(
        boolean success,
        boolean enabled,
        String provider,
        String fromEmail,
        String fromName,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        boolean smtpStartTls,
        boolean secretConfigured
    ) {
        static EmailSettingsResponse from(CustomerEmailSettings settings) {
            return new EmailSettingsResponse(
                true,
                settings.enabled(),
                settings.provider(),
                settings.fromEmail(),
                settings.fromName(),
                settings.smtpHost(),
                settings.smtpPort(),
                settings.smtpUsername(),
                settings.smtpStartTls(),
                settings.secretConfigured()
            );
        }
    }

    public record OAuthProviderSettingsResponse(
        boolean success,
        boolean enabled,
        String provider,
        String clientId,
        boolean secretConfigured
    ) {
        static OAuthProviderSettingsResponse from(CustomerOAuthProviderSettings settings) {
            return new OAuthProviderSettingsResponse(
                true,
                settings.enabled(),
                settings.provider(),
                settings.clientId(),
                settings.secretConfigured()
            );
        }
    }
}
