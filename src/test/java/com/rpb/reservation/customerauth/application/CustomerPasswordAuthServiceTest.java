package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerPasswordRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerPasswordAuthServiceTest {
    final UUID tenant = UUID.randomUUID(), id = UUID.randomUUID();
    final TenantScope scope = new TenantScope(new TenantId(tenant));
    final CustomerRepositoryPort customers = mock(CustomerRepositoryPort.class);
    final CustomerAuthRepositoryPort accounts = mock(CustomerAuthRepositoryPort.class);
    final CustomerPasswordRepositoryPort passwords = mock(CustomerPasswordRepositoryPort.class);
    final com.rpb.reservation.customerauth.application.port.out.CustomerSecurityEmailPort securityCodes = mock(com.rpb.reservation.customerauth.application.port.out.CustomerSecurityEmailPort.class);
    final CustomerAuthApplicationService sessions = mock(CustomerAuthApplicationService.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final CustomerPasswordAuthService service = new CustomerPasswordAuthService(customers,accounts,passwords,sessions,encoder,securityCodes);
    final Customer customer = new Customer(new CustomerId(id),scope,"C-test","regular",new E164Phone("+6591234567"),"active","Guest",null);

    @BeforeEach void setup() {
        when(customers.findByPhone(scope,customer.phone())).thenReturn(Optional.of(customer));
        when(customers.findById(scope,customer.id())).thenReturn(Optional.of(customer));
        when(accounts.findAccountByCustomerId(tenant,id)).thenReturn(Optional.empty());
        when(passwords.passwordHash(scope,id)).thenReturn(Optional.empty());
        when(accounts.createAccount(any(),eq(tenant),eq(id),isNull(),any())).thenAnswer(i ->
            new CustomerAuthRepositoryPort.CustomerAuthAccountRecord(i.getArgument(0),tenant,id,null,"Guest","active"));
    }
    @Test void initialPasswordCanNoLongerBootstrapAnAccount() {
        assertThatThrownBy(()->service.login(tenant,"+6591234567","234567","127.0.0.1","test"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.INVALID_CREDENTIALS);
        verify(customers,never()).save(any(),any());
        verify(passwords,never()).savePassword(any(),any(),any());verifyNoInteractions(sessions);
    }
    @Test void previouslyPersistedInitialPasswordMustNotCreateAnOrdinarySession() {
        when(passwords.passwordHash(scope,id)).thenReturn(Optional.of(encoder.encode("234567")));
        when(passwords.passwordChangeRequired(scope,id)).thenReturn(true);
        assertThatThrownBy(()->service.login(tenant,"+6591234567","234567","127.0.0.1","test"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.INVALID_CREDENTIALS);
        verifyNoInteractions(sessions);
    }
    @Test void changedPasswordNeverFallsBackToPhoneSuffix() {
        when(passwords.passwordHash(scope,id)).thenReturn(Optional.of(encoder.encode("new-password")));
        assertThatThrownBy(() -> service.login(tenant,"+6591234567","234567","ip","test"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.INVALID_CREDENTIALS);
        verify(accounts,never()).createAccount(any(),any(),any(),any(),any());
        verify(sessions,never()).loginAccount(any(),any(),any());
    }
    @Test void unknownPhoneNeverCreatesCustomerOrAccount() {
        assertThatThrownBy(() -> service.login(tenant,"+6599999999","999999","ip","test"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.INVALID_CREDENTIALS);
        verify(customers,never()).save(any(),any());verify(accounts,never()).createAccount(any(),any(),any(),any(),any());
    }
    @Test void anotherTenantCannotUseCustomerOrPassword() {
        assertThatThrownBy(() -> service.login(UUID.randomUUID(),"+6591234567","234567","ip","test"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.INVALID_CREDENTIALS);
    }
    @Test void changeRequiresOldPasswordAndRevokesAllOldSessions() {
        var account = new CustomerAuthRepositoryPort.CustomerAuthAccountRecord(UUID.randomUUID(),tenant,id,null,"Guest","active");
        when(accounts.findAccountByCustomerId(tenant,id)).thenReturn(Optional.of(account));
        when(passwords.passwordHash(scope,id)).thenReturn(Optional.of(encoder.encode("old-password")));
        var principal = new CustomerAuthPrincipal(tenant,account.id(),id,null,"Guest");
        assertThatThrownBy(() -> service.change(principal,"wrong","new-password","ip","test"))
            .isInstanceOf(CustomerAuthServiceException.class);
        verify(passwords,never()).savePassword(any(),any(),any());
        service.change(principal,"old-password","new-password","ip","test");
        verify(passwords).revokeSessions(scope,id);verify(sessions).loginAccount(account,"ip","test");
        verify(securityCodes).invalidateAll(scope,id);
    }
    @Test void newPasswordRejectsPhoneSuffixAndBcryptTruncation() {
        var principal = new CustomerAuthPrincipal(tenant,UUID.randomUUID(),id,null,"Guest");
        for (String value : List.of("234567", "        ", "x".repeat(73), "密".repeat(25))) {
            assertThatThrownBy(() -> service.change(principal,"234567",value,"ip","test"))
                .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.REQUEST_INVALID);
        }
    }
}
