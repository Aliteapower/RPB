package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.port.out.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerSecurityEmailServiceTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID(),account=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final CustomerSecurityEmailPort records=mock(CustomerSecurityEmailPort.class);
    final CustomerPasswordRepositoryPort passwords=mock(CustomerPasswordRepositoryPort.class);
    final CustomerEmailSettingsRepositoryPort settings=mock(CustomerEmailSettingsRepositoryPort.class);
    final CustomerEmailDeliveryPort mail=mock(CustomerEmailDeliveryPort.class);
    final CustomerSecurityThrottleService throttle=mock(CustomerSecurityThrottleService.class);
    final CustomerSecurityEmailService service=new CustomerSecurityEmailService(records,passwords,settings,mail,throttle,new BCryptPasswordEncoder(4));
    final CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,account,customer,null,"Guest");
    @BeforeEach void setup(){
        when(settings.findEmailSettings(tenant,store)).thenReturn(Optional.of(new CustomerEmailSettings(true,"smtp","no-reply@example.com","Test","localhost",1025,null,false)));
        when(records.customerByPhone(scope,"+6591234567")).thenReturn(Optional.of(customer));
        when(records.account(scope,customer)).thenReturn(Optional.of(new CustomerSecurityEmailPort.Account(account,customer,"verified@example.com",true)));
        when(records.emailAvailable(any(),any(),any())).thenReturn(true);
    }
    @Test void unmatchedOrUnverifiedRecoveryEmailNeverSendsAndDoesNotIssueChallenge(){
        service.requestReset(scope,"+6591234567","other@example.com","ip");
        when(records.account(scope,customer)).thenReturn(Optional.of(new CustomerSecurityEmailPort.Account(account,customer,"verified@example.com",false)));
        service.requestReset(scope,"+6591234567","verified@example.com","ip");
        verifyNoInteractions(mail);verify(records,never()).issue(any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void matchingVerifiedEmailSendsCodeButOnlyPersistsSaltedHash(){
        service.requestReset(scope,"+6591234567","Verified@Example.com","ip");
        var message=org.mockito.ArgumentCaptor.forClass(CustomerEmailDeliveryMessage.class);
        verify(mail).sendLoginCode(message.capture(),any());
        String code=message.getValue().code();assertThat(code).matches("[0-9]{6}");
        verify(records).issue(eq(scope),eq(customer),eq(account),eq("reset_password"),any(),eq("verified@example.com"),argThat(h -> h.length()==64 && !h.contains(code)),any());
    }
    @Test void invalidExpiredOrLockedCodeCannotChangePassword(){
        for(var challenge:List.of(new CustomerSecurityEmailPort.Challenge(UUID.randomUUID(),account,"verified@example.com","0".repeat(64),0,Instant.now().plusSeconds(600)),
            new CustomerSecurityEmailPort.Challenge(UUID.randomUUID(),account,"verified@example.com","0".repeat(64),5,Instant.now().plusSeconds(600)),
            new CustomerSecurityEmailPort.Challenge(UUID.randomUUID(),account,"verified@example.com","0".repeat(64),0,Instant.now().minusSeconds(1)))){
            when(records.latest(scope,customer,"reset_password")).thenReturn(Optional.of(challenge));
            assertThatThrownBy(()->service.reset(scope,"+6591234567","verified@example.com","123456","a-new-password","ip"))
                .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.CODE_MISMATCH);
        }
        verify(passwords,never()).savePassword(any(),any(),any());
    }
    @Test void bootstrapAndForeignTenantPrincipalsCannotBindEmail(){
        for(var p:List.of(new CustomerAuthPrincipal(tenant,account,customer,null,"Guest",null,true),
            new CustomerAuthPrincipal(UUID.randomUUID(),account,customer,null,"Guest"))){
            assertThatThrownBy(()->service.requestBinding(scope,p,"new@example.com","ip")).isInstanceOf(CustomerAuthServiceException.class);
        }
        verifyNoInteractions(mail);
    }
}
