package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.*;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerRestrictedSessionTest {
    @Test void restrictedSessionIsAvailableOnlyToPasswordManagementAndHidesProfileInResponse(){
        var tenant=UUID.randomUUID();var customer=UUID.randomUUID();var account=UUID.randomUUID();
        var records=mock(CustomerAuthRepositoryPort.class);var customers=mock(CustomerRepositoryPort.class);
        var scope=new TenantScope(new TenantId(tenant));
        when(customers.findById(eq(scope),any())).thenReturn(Optional.of(new Customer(new CustomerId(customer),scope,"test","regular",new E164Phone("+6591234567"),"active","Private Name",null)));
        when(records.findActiveSessionByHash(any())).thenReturn(Optional.of(new CustomerAuthRepositoryPort.CustomerAuthSessionRecord(UUID.randomUUID(),
            new CustomerAuthRepositoryPort.CustomerAuthAccountRecord(account,tenant,customer,"private@example.com","Private Name","active"),Instant.now().plusSeconds(600),true)));
        var service=new CustomerAuthApplicationService(records,customers,mock(CustomerEmailSettingsRepositoryPort.class),mock(CustomerEmailDeliveryPort.class),mock(CustomerOAuthSettingsRepositoryPort.class),mock(CustomerOAuthTokenVerifierPort.class));
        assertThat(service.authenticateSession("bootstrap")).isEmpty();
        assertThat(service.authenticatePasswordManagementSession("bootstrap").orElseThrow().passwordChangeRequired()).isTrue();
    }
}
