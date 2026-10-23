package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletCustomerIdentityApiTest {
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
 final WalletApiIdentity identity=mock(WalletApiIdentity.class);final WalletCustomerIdentityService service=mock(WalletCustomerIdentityService.class);
 final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store));
 final CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.invalid","test");
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new WalletCustomerIdentityController(identity,service)).setControllerAdvice(new WalletErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();when(identity.staff()).thenReturn(actor);when(identity.customer(any())).thenReturn(principal);when(identity.scope(tenant,store)).thenReturn(scope);}
 String path(boolean publicPath){return "/api/v1/"+(publicPath?"public/":"")+"stores/"+store+"/wallet/customer-codes";}
 @Test void identifiesOnlyCustomerWithoutExposingAccountOrFinancialAuthority()throws Exception{
  String code="WI."+"A".repeat(43);when(service.resolve(scope,actor,code)).thenReturn(new WalletViews.Customer(customer,"C","test",null,"+6581234567"));
  mvc.perform(post(path(false)+"/resolve").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\""+code+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(customer.toString())).andExpect(jsonPath("$.account").doesNotExist()).andExpect(jsonPath("$.balanceCents").doesNotExist());
 }
 @Test void ignoresNoIdentityOrAmountOverridesAndKeepsCsrfBoundary()throws Exception{
  mvc.perform(post(path(true)).header("X-RPB-Wallet","1").contentType("application/json").content("{\"customerId\":\""+customer+"\"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path(false)+"/resolve").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"x\",\"amount\":100}")).andExpect(status().isBadRequest());
  mvc.perform(post(path(true)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post(path(false)+"/resolve").header("X-RPB-Wallet","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void issuingUsesSessionIdentityAndNeedsNoAccountParameter()throws Exception{
  when(service.issue(scope,principal)).thenReturn(new WalletViews.PaymentCode("customer_identity","WI."+"A".repeat(43),java.time.Instant.now().plusSeconds(60)));
  mvc.perform(post(path(true)).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("customer_identity"));verify(service).issue(scope,principal);
 }
}
