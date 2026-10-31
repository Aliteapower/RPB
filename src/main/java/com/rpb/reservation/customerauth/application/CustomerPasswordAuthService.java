package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.CustomerAuthAccountRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerPasswordRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerSecurityEmailPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerPasswordAuthService {
    private final CustomerRepositoryPort customers;
    private final CustomerAuthRepositoryPort accounts;
    private final CustomerPasswordRepositoryPort passwords;
    private final CustomerAuthApplicationService sessions;
    private final PasswordEncoder encoder;
    private final CustomerSecurityEmailPort securityCodes;
    private final String dummyHash;

    public CustomerPasswordAuthService(CustomerRepositoryPort customers, CustomerAuthRepositoryPort accounts,
        CustomerPasswordRepositoryPort passwords, CustomerAuthApplicationService sessions, PasswordEncoder encoder, CustomerSecurityEmailPort securityCodes) {
        this.customers=customers;this.accounts=accounts;this.passwords=passwords;this.sessions=sessions;this.encoder=encoder;
        this.securityCodes=securityCodes;
        this.dummyHash=encoder.encode(UUID.randomUUID().toString());
    }

    // Authentication failures must commit rate-limit counters, while persistence failures roll back everything.
    @Transactional(noRollbackFor = CustomerAuthServiceException.class)
    public CustomerAuthLoginResult login(UUID tenantId, String phoneText, String password, String remoteAddr, String userAgent) {
        E164Phone phone = phone(phoneText);
        if (!validPasswordLength(password,1)) throw new CustomerAuthServiceException(CustomerAuthError.INVALID_CREDENTIALS);
        var scope = new TenantScope(new TenantId(tenantId));
        passwords.recordAttempt(scope,phone.value(),remoteAddr);
        Customer customer = customers.findByPhone(scope,phone).filter(c -> "active".equals(c.status()))
            .orElseGet(() -> { encoder.matches(password,dummyHash); throw invalidCredentials(); });
        passwords.lockCustomer(scope,customer.id().value());
        customer = customers.findById(scope,customer.id()).filter(c -> "active".equals(c.status()) && phone.equals(c.phone()))
            .orElseThrow(CustomerPasswordAuthService::invalidCredentials);
        String hash = passwords.passwordHash(scope,customer.id().value()).orElse(null);
        if (!matches(password,hash) || !validPasswordLength(password,6) || passwords.passwordChangeRequired(scope,customer.id().value())) throw invalidCredentials();
        var account = account(customer);
        return sessions.loginAccount(account,remoteAddr,userAgent);
    }

    @Transactional(noRollbackFor = CustomerAuthServiceException.class)
    public CustomerAuthLoginResult change(CustomerAuthPrincipal principal, String currentPassword, String newPassword,
        String remoteAddr, String userAgent) {
        if (principal == null || principal.passwordChangeRequired()) throw new CustomerAuthServiceException(CustomerAuthError.UNAUTHENTICATED);
        if (!validPasswordLength(newPassword,6) || newPassword.equals(currentPassword))
            throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        var scope = new TenantScope(new TenantId(principal.tenantId()));
        passwords.recordAttempt(scope,"change:"+principal.customerId(),remoteAddr);
        passwords.lockCustomer(scope,principal.customerId());
        var customer = customers.findById(scope,new CustomerId(principal.customerId()))
            .filter(c -> "active".equals(c.status())).orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.ACCOUNT_DISABLED));
        String hash = passwords.passwordHash(scope,principal.customerId()).orElse(null);
        if (!validPasswordLength(currentPassword,6) || !matches(currentPassword,hash) || passwords.passwordChangeRequired(scope,principal.customerId())) throw invalidCredentials();
        var account = account(customer);
        passwords.savePassword(scope,principal.customerId(),encoder.encode(newPassword));
        passwords.completePasswordChange(scope,principal.customerId());
        passwords.revokeSessions(scope,principal.customerId());
        // Codes issued before a password change must not be usable to undo it.
        securityCodes.invalidateAll(scope,principal.customerId());
        return sessions.loginAccount(account,remoteAddr,userAgent);
    }

    private CustomerAuthAccountRecord account(Customer customer) {
        var existing = accounts.findAccountByCustomerId(customer.scope().tenantId().value(),customer.id().value());
        if (existing.isPresent() && !"active".equals(existing.get().status()))
            throw new CustomerAuthServiceException(CustomerAuthError.ACCOUNT_DISABLED);
        return existing.orElseGet(() -> accounts.createAccount(UUID.randomUUID(),customer.scope().tenantId().value(),
            customer.id().value(),null,customer.displayName()));
    }
    private boolean matches(String password,String hash) {
        boolean matches = encoder.matches(password,hash == null ? dummyHash : hash);
        return hash != null && matches;
    }
    private static E164Phone phone(String text) {
        try { if(text == null || text.isBlank()) throw new IllegalArgumentException();return new E164Phone(text.trim()); }
        catch (IllegalArgumentException e) { throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID); }
    }
    private static boolean validPasswordLength(String text,int minimum) {
        return text != null && !text.isBlank() && text.length() >= minimum && text.length() <= 72 && text.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    private static CustomerAuthServiceException invalidCredentials() { return new CustomerAuthServiceException(CustomerAuthError.INVALID_CREDENTIALS); }
}
