package com.rpb.reservation.customerauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailDeliveryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthTokenVerifierPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerAuthApplicationServiceTest {

    private final UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000901");
    private final UUID storeId = UUID.fromString("20000000-0000-0000-0000-000000000901");

    @Test
    void emailLoginCreatesCustomerAccountIdentityAndSession() {
        FakeCustomerAuthRepository authRepository = new FakeCustomerAuthRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        FakeEmailDelivery delivery = new FakeEmailDelivery();
        CustomerAuthApplicationService service = service(authRepository, customerRepository, delivery, new FakeOAuthVerifier());

        CustomerAuthEmailCodeResult code = service.requestEmailCode(
            tenantId,
            storeId,
            "Guest@Example.COM",
            "127.0.0.1",
            "test"
        );
        CustomerAuthLoginResult login = service.loginWithEmail(
            tenantId,
            storeId,
            "guest@example.com",
            code.devCode(),
            "Guest",
            "127.0.0.1",
            "test"
        );

        assertThat(login.sessionToken()).isNotBlank();
        assertThat(login.principal().tenantId()).isEqualTo(tenantId);
        assertThat(login.principal().email()).isEqualTo("guest@example.com");
        assertThat(authRepository.account).isNotNull();
        assertThat(authRepository.linkedProvider).isEqualTo("email");
        assertThat(authRepository.sessionCount).isEqualTo(1);
        assertThat(authRepository.codeStatus).isEqualTo("consumed");
        assertThat(customerRepository.savedCustomer).isNotNull();
        assertThat(customerRepository.savedCustomer.displayName()).isEqualTo("Guest");
        assertThat(delivery.lastMessage).isNotNull();
        assertThat(delivery.lastMessage.tenantId()).isEqualTo(tenantId);
        assertThat(delivery.lastMessage.storeId()).isEqualTo(storeId);
        assertThat(delivery.lastMessage.toEmail()).isEqualTo("guest@example.com");
        assertThat(delivery.lastMessage.code()).isEqualTo(code.devCode());
    }

    @Test
    void emailLoginRejectsDisabledAccount() {
        FakeCustomerAuthRepository authRepository = new FakeCustomerAuthRepository();
        authRepository.account = new CustomerAuthRepositoryPort.CustomerAuthAccountRecord(
            UUID.fromString("30000000-0000-0000-0000-000000000901"),
            tenantId,
            UUID.fromString("40000000-0000-0000-0000-000000000901"),
            "guest@example.com",
            "Guest",
            "disabled"
        );
        CustomerAuthApplicationService service = service(authRepository, new FakeCustomerRepository(), new FakeEmailDelivery(), new FakeOAuthVerifier());

        CustomerAuthEmailCodeResult code = service.requestEmailCode(
            tenantId,
            storeId,
            "guest@example.com",
            "127.0.0.1",
            "test"
        );

        assertThatThrownBy(() -> service.loginWithEmail(
            tenantId,
            storeId,
            "guest@example.com",
            code.devCode(),
            "Guest",
            "127.0.0.1",
            "test"
        ))
            .isInstanceOf(CustomerAuthServiceException.class)
            .satisfies(exception -> assertThat(((CustomerAuthServiceException) exception).error())
                .isEqualTo(CustomerAuthError.ACCOUNT_DISABLED));
        assertThat(authRepository.sessionCount).isZero();
    }

    @Test
    void emailCodeRequiresEnabledTenantEmailSettings() {
        FakeCustomerAuthRepository authRepository = new FakeCustomerAuthRepository();
        FakeEmailSettingsRepository emailSettings = new FakeEmailSettingsRepository();
        emailSettings.settings = Optional.of(CustomerEmailSettings.disabled());
        CustomerAuthApplicationService service = new CustomerAuthApplicationService(
            authRepository,
            new FakeCustomerRepository(),
            emailSettings,
            new FakeEmailDelivery(),
            new FakeOAuthSettingsRepository(),
            new FakeOAuthVerifier()
        );

        assertThatThrownBy(() -> service.requestEmailCode(
            tenantId,
            storeId,
            "guest@example.com",
            "127.0.0.1",
            "test"
        ))
            .isInstanceOf(CustomerAuthServiceException.class)
            .satisfies(exception -> assertThat(((CustomerAuthServiceException) exception).error())
                .isEqualTo(CustomerAuthError.EMAIL_CHANNEL_NOT_CONFIGURED));
        assertThat(authRepository.code).isNull();
    }

    @Test
    void oauthLoginVerifiesProviderTokenFromTenantSettings() {
        FakeCustomerAuthRepository authRepository = new FakeCustomerAuthRepository();
        FakeOAuthVerifier verifier = new FakeOAuthVerifier();
        verifier.identity = new CustomerOAuthVerifiedIdentity(
            "google-subject-1",
            "Guest@Example.COM",
            "Google Guest"
        );
        CustomerAuthApplicationService service = service(authRepository, new FakeCustomerRepository(), new FakeEmailDelivery(), verifier);

        CustomerAuthLoginResult result = service.loginWithProvider(
            tenantId,
            storeId,
            "google",
            "official-token",
            "127.0.0.1",
            "test"
        );

        assertThat(result.principal().email()).isEqualTo("guest@example.com");
        assertThat(verifier.lastRequest).isNotNull();
        assertThat(verifier.lastRequest.provider()).isEqualTo("google");
        assertThat(verifier.lastRequest.token()).isEqualTo("official-token");
        assertThat(authRepository.linkedProvider).isEqualTo("google");
        assertThat(authRepository.linkedSubject).isEqualTo("google-subject-1");
    }

    @Test
    void oauthLoginRejectsDisabledProviderSettings() {
        FakeOAuthSettingsRepository oauthSettings = new FakeOAuthSettingsRepository();
        oauthSettings.settings = Optional.of(CustomerOAuthProviderSettings.disabled("facebook"));
        CustomerAuthApplicationService service = new CustomerAuthApplicationService(
            new FakeCustomerAuthRepository(),
            new FakeCustomerRepository(),
            new FakeEmailSettingsRepository(),
            new FakeEmailDelivery(),
            oauthSettings,
            new FakeOAuthVerifier()
        );

        assertThatThrownBy(() -> service.loginWithProvider(
            tenantId,
            storeId,
            "facebook",
            "official-token",
            "127.0.0.1",
            "test"
        ))
            .isInstanceOf(CustomerAuthServiceException.class)
            .satisfies(exception -> assertThat(((CustomerAuthServiceException) exception).error())
                .isEqualTo(CustomerAuthError.PROVIDER_NOT_CONFIGURED));
    }

    private CustomerAuthApplicationService service(
        FakeCustomerAuthRepository authRepository,
        FakeCustomerRepository customerRepository,
        FakeEmailDelivery delivery,
        FakeOAuthVerifier verifier
    ) {
        return new CustomerAuthApplicationService(
            authRepository,
            customerRepository,
            new FakeEmailSettingsRepository(),
            delivery,
            new FakeOAuthSettingsRepository(),
            verifier
        );
    }

    private static final class FakeCustomerAuthRepository implements CustomerAuthRepositoryPort {
        private EmailCodeRecord code;
        private String codeStatus = "created";
        private CustomerAuthAccountRecord account;
        private String linkedProvider;
        private String linkedSubject;
        private int sessionCount;

        @Override
        public void createEmailCode(
            UUID id,
            UUID tenantId,
            UUID storeId,
            String email,
            String codeHash,
            Instant expiresAt,
            String remoteAddr,
            String userAgent
        ) {
            codeStatus = "created";
            code = new EmailCodeRecord(id, tenantId, storeId, email, codeHash, "created", 0, 5, expiresAt);
        }

        @Override
        public Optional<EmailCodeRecord> findLatestUsableEmailCode(UUID tenantId, UUID storeId, String email) {
            if (code == null || !"created".equals(codeStatus) || !code.email().equals(email)) {
                return Optional.empty();
            }
            return Optional.of(code);
        }

        @Override
        public void markEmailCodeConsumed(UUID codeId) {
            codeStatus = "consumed";
        }

        @Override
        public void markEmailCodeFailed(UUID codeId) {
            codeStatus = "failed";
        }

        @Override
        public void markEmailCodeExpired(UUID codeId) {
            codeStatus = "expired";
        }

        @Override
        public Optional<CustomerAuthAccountRecord> findAccountByEmail(UUID tenantId, String email) {
            return account == null || !account.email().equals(email) ? Optional.empty() : Optional.of(account);
        }

        @Override
        public Optional<CustomerAuthAccountRecord> findAccountByIdentity(UUID tenantId, String provider, String providerSubject) {
            return Optional.empty();
        }

        @Override
        public CustomerAuthAccountRecord createAccount(
            UUID accountId,
            UUID tenantId,
            UUID customerId,
            String email,
            String displayName
        ) {
            account = new CustomerAuthAccountRecord(accountId, tenantId, customerId, email, displayName, "active");
            return account;
        }

        @Override
        public void markEmailVerified(UUID accountId, String displayName) {
        }

        @Override
        public void linkIdentity(
            UUID tenantId,
            UUID authAccountId,
            String provider,
            String providerSubject,
            String email,
            String displayName
        ) {
            linkedProvider = provider;
            linkedSubject = providerSubject;
        }

        @Override
        public void createSession(
            UUID sessionId,
            CustomerAuthAccountRecord account,
            String sessionHash,
            Instant expiresAt,
            String remoteAddr,
            String userAgent
        ) {
            sessionCount++;
        }

        @Override
        public Optional<CustomerAuthSessionRecord> findActiveSessionByHash(String sessionHash) {
            return Optional.empty();
        }

        @Override
        public void touchSession(UUID sessionId) {
        }

        @Override
        public void revokeSession(String sessionHash) {
        }
    }

    private static final class FakeEmailSettingsRepository implements CustomerEmailSettingsRepositoryPort {
        private Optional<CustomerEmailSettings> settings = Optional.of(new CustomerEmailSettings(
            true,
            "smtp",
            "booking@example.com",
            "Booking",
            "smtp.example.com",
            587,
            "smtp-user",
            true
        ));

        @Override
        public Optional<CustomerEmailSettings> findEmailSettings(UUID tenantId, UUID storeId) {
            return settings;
        }
    }

    private static final class FakeEmailDelivery implements CustomerEmailDeliveryPort {
        private CustomerEmailDeliveryMessage lastMessage;

        @Override
        public void sendLoginCode(CustomerEmailDeliveryMessage message, CustomerEmailSettings settings) {
            lastMessage = message;
        }
    }

    private static final class FakeOAuthSettingsRepository implements CustomerOAuthSettingsRepositoryPort {
        private Optional<CustomerOAuthProviderSettings> settings = Optional.of(new CustomerOAuthProviderSettings(
            true,
            "google",
            "client-id",
            true
        ));

        @Override
        public Optional<CustomerOAuthProviderSettings> findProviderSettings(UUID tenantId, UUID storeId, String provider) {
            return settings;
        }
    }

    private static final class FakeOAuthVerifier implements CustomerOAuthTokenVerifierPort {
        private CustomerOAuthVerificationRequest lastRequest;
        private CustomerOAuthVerifiedIdentity identity = new CustomerOAuthVerifiedIdentity(
            "subject-1",
            "guest@example.com",
            "Guest"
        );

        @Override
        public CustomerOAuthVerifiedIdentity verify(CustomerOAuthVerificationRequest request, CustomerOAuthProviderSettings settings) {
            lastRequest = request;
            return identity;
        }
    }

    private static final class FakeCustomerRepository implements CustomerRepositoryPort {
        private Customer savedCustomer;

        @Override
        public Optional<Customer> findById(TenantScope scope, CustomerId customerId) {
            return Optional.empty();
        }

        @Override
        public Optional<Customer> findByCode(TenantScope scope, String customerCode) {
            return Optional.empty();
        }

        @Override
        public Optional<Customer> findByPhone(TenantScope scope, E164Phone phone) {
            return Optional.empty();
        }

        @Override
        public List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText) {
            return List.of();
        }

        @Override
        public Customer save(TenantScope scope, Customer customer) {
            savedCustomer = customer;
            return customer;
        }
    }
}
