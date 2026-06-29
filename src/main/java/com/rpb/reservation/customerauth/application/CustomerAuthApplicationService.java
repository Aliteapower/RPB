package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.CustomerAuthAccountRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.EmailCodeRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.CustomerAuthSessionRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailDeliveryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthTokenVerifierPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuthApplicationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String PROVIDER_EMAIL = "email";
    private static final String PROVIDER_SMTP = "smtp";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_FACEBOOK = "facebook";

    private final CustomerAuthRepositoryPort repository;
    private final CustomerRepositoryPort customerRepository;
    private final CustomerEmailSettingsRepositoryPort emailSettingsRepository;
    private final CustomerEmailDeliveryPort emailDeliveryPort;
    private final CustomerOAuthSettingsRepositoryPort oauthSettingsRepository;
    private final CustomerOAuthTokenVerifierPort oauthTokenVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomerAuthApplicationService(
        CustomerAuthRepositoryPort repository,
        CustomerRepositoryPort customerRepository,
        CustomerEmailSettingsRepositoryPort emailSettingsRepository,
        CustomerEmailDeliveryPort emailDeliveryPort,
        CustomerOAuthSettingsRepositoryPort oauthSettingsRepository,
        CustomerOAuthTokenVerifierPort oauthTokenVerifier
    ) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.emailSettingsRepository = emailSettingsRepository;
        this.emailDeliveryPort = emailDeliveryPort;
        this.oauthSettingsRepository = oauthSettingsRepository;
        this.oauthTokenVerifier = oauthTokenVerifier;
    }

    @Transactional
    public CustomerAuthEmailCodeResult requestEmailCode(
        UUID tenantId,
        UUID storeId,
        String email,
        String remoteAddr,
        String userAgent
    ) {
        String normalizedEmail = normalizeEmail(email);
        CustomerEmailSettings settings = emailSettingsRepository.findEmailSettings(tenantId, storeId)
            .filter(CustomerAuthApplicationService::isUsableEmailSettings)
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.EMAIL_CHANNEL_NOT_CONFIGURED));
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(EMAIL_CODE_TTL);
        repository.createEmailCode(
            UUID.randomUUID(),
            tenantId,
            storeId,
            normalizedEmail,
            hash(code),
            expiresAt,
            remoteAddr,
            userAgent
        );
        try {
            emailDeliveryPort.sendLoginCode(
                new CustomerEmailDeliveryMessage(tenantId, storeId, normalizedEmail, code, expiresAt),
                settings
            );
        } catch (CustomerAuthServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomerAuthServiceException(CustomerAuthError.EMAIL_DELIVERY_FAILED);
        }
        return new CustomerAuthEmailCodeResult(true, normalizedEmail, expiresAt, code);
    }

    @Transactional
    public CustomerAuthLoginResult loginWithEmail(
        UUID tenantId,
        UUID storeId,
        String email,
        String code,
        String displayName,
        String remoteAddr,
        String userAgent
    ) {
        String normalizedEmail = normalizeEmail(email);
        if (code == null || code.isBlank()) {
            throw new CustomerAuthServiceException(CustomerAuthError.CODE_MISMATCH);
        }
        EmailCodeRecord codeRecord = repository.findLatestUsableEmailCode(tenantId, storeId, normalizedEmail)
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.CODE_EXPIRED));
        if (codeRecord.expiresAt().isBefore(Instant.now())) {
            repository.markEmailCodeExpired(codeRecord.id());
            throw new CustomerAuthServiceException(CustomerAuthError.CODE_EXPIRED);
        }
        if (!hash(code.trim()).equals(codeRecord.codeHash())) {
            repository.markEmailCodeFailed(codeRecord.id());
            throw new CustomerAuthServiceException(CustomerAuthError.CODE_MISMATCH);
        }

        CustomerAuthAccountRecord account = accountForEmail(tenantId, normalizedEmail, displayName);
        repository.markEmailVerified(account.id(), trimToNull(displayName));
        repository.linkIdentity(tenantId, account.id(), PROVIDER_EMAIL, normalizedEmail, normalizedEmail, trimToNull(displayName));
        repository.markEmailCodeConsumed(codeRecord.id());
        return createSession(account, remoteAddr, userAgent);
    }

    @Transactional
    public CustomerAuthLoginResult loginWithProvider(
        UUID tenantId,
        UUID storeId,
        String provider,
        String token,
        String remoteAddr,
        String userAgent
    ) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedToken = trimToNull(token);
        if (storeId == null || normalizedToken == null) {
            throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        }
        CustomerOAuthProviderSettings settings = oauthSettingsRepository
            .findProviderSettings(tenantId, storeId, normalizedProvider)
            .filter(value -> isUsableOAuthSettings(normalizedProvider, value))
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.PROVIDER_NOT_CONFIGURED));
        CustomerOAuthVerifiedIdentity identity;
        try {
            identity = oauthTokenVerifier.verify(
                new CustomerOAuthVerificationRequest(tenantId, storeId, normalizedProvider, normalizedToken),
                settings
            );
        } catch (CustomerAuthServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
        String subject = trimToNull(identity == null ? null : identity.providerSubject());
        if (subject == null) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
        String normalizedEmail = normalizeEmail(identity.email());
        String displayName = identity.displayName();
        CustomerAuthAccountRecord account = repository
            .findAccountByIdentity(tenantId, normalizedProvider, subject)
            .orElseGet(() -> {
                CustomerAuthAccountRecord resolved = accountForEmail(tenantId, normalizedEmail, displayName);
                repository.linkIdentity(tenantId, resolved.id(), normalizedProvider, subject, normalizedEmail, trimToNull(displayName));
                return resolved;
            });
        requireActiveAccount(account);
        repository.markEmailVerified(account.id(), trimToNull(displayName));
        return createSession(account, remoteAddr, userAgent);
    }

    @Transactional
    public Optional<CustomerAuthPrincipal> authenticateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        Optional<CustomerAuthSessionRecord> session = repository.findActiveSessionByHash(hash(sessionToken));
        session.ifPresent(record -> repository.touchSession(record.sessionId()));
        return session.map(record -> principal(record.account()));
    }

    @Transactional
    public void logout(String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            repository.revokeSession(hash(sessionToken));
        }
    }

    private CustomerAuthAccountRecord accountForEmail(UUID tenantId, String email, String displayName) {
        return repository.findAccountByEmail(tenantId, email)
            .map(CustomerAuthApplicationService::requireActiveAccount)
            .orElseGet(() -> {
                TenantScope scope = new TenantScope(new TenantId(tenantId));
                Customer customer = customerRepository.save(
                    scope,
                    new Customer(
                        new CustomerId(UUID.randomUUID()),
                        scope,
                        "C-PUB-" + UUID.randomUUID().toString().substring(0, 8),
                        "temporary",
                        E164Phone.empty(),
                        "active",
                        firstText(displayName, email),
                        null
                    )
                );
                return repository.createAccount(UUID.randomUUID(), tenantId, customer.id().value(), email, trimToNull(displayName));
            });
    }

    private static CustomerAuthAccountRecord requireActiveAccount(CustomerAuthAccountRecord account) {
        if (!ACCOUNT_STATUS_ACTIVE.equals(account.status())) {
            throw new CustomerAuthServiceException(CustomerAuthError.ACCOUNT_DISABLED);
        }
        return account;
    }

    private CustomerAuthLoginResult createSession(CustomerAuthAccountRecord account, String remoteAddr, String userAgent) {
        String sessionToken = newSessionToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        repository.createSession(
            UUID.randomUUID(),
            account,
            hash(sessionToken),
            expiresAt,
            remoteAddr,
            userAgent
        );
        return new CustomerAuthLoginResult(principal(account), sessionToken, expiresAt);
    }

    private static CustomerAuthPrincipal principal(CustomerAuthAccountRecord account) {
        return new CustomerAuthPrincipal(
            account.tenantId(),
            account.id(),
            account.customerId(),
            account.email(),
            account.displayName()
        );
    }

    private String newSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized == null || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean isUsableEmailSettings(CustomerEmailSettings settings) {
        return settings != null
            && settings.enabled()
            && PROVIDER_SMTP.equals(settings.provider())
            && hasText(settings.fromEmail())
            && hasText(settings.smtpHost())
            && settings.smtpPort() > 0;
    }

    private static boolean isUsableOAuthSettings(String provider, CustomerOAuthProviderSettings settings) {
        if (settings == null || !settings.enabled() || !provider.equals(settings.provider()) || !hasText(settings.clientId())) {
            return false;
        }
        return !PROVIDER_FACEBOOK.equals(provider) || settings.secretConfigured();
    }

    private static String normalizeProvider(String provider) {
        String normalized = trimToNull(provider);
        normalized = normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
        if (PROVIDER_GOOGLE.equals(normalized) || PROVIDER_FACEBOOK.equals(normalized)) {
            return normalized;
        }
        throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_UNSUPPORTED);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("hash_failed", exception);
        }
    }

    private static String firstText(String candidate, String fallback) {
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
