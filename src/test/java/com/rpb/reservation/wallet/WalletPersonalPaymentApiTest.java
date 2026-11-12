package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.personalpayment.*;
import com.rpb.reservation.wallet.domain.WalletException;
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

class WalletPersonalPaymentApiTest {
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
 final WalletApiIdentity identity=mock(WalletApiIdentity.class);final WalletPersonalPaymentService service=mock(WalletPersonalPaymentService.class);
 final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.charge.create","wallet.collection.confirm"),Set.of(store));
 final CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.invalid","test");
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new WalletPersonalPaymentController(identity,service)).setControllerAdvice(new WalletErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();when(identity.staff()).thenReturn(actor);when(identity.customer(any())).thenReturn(principal);when(identity.scope(tenant,store)).thenReturn(scope);}
 String path(boolean publicPath){return "/api/v1/"+(publicPath?"public/":"")+"stores/"+store+"/wallet/";}
 @Test void policyDefaultsRemainNullAndConsentUsesSessionIdentity()throws Exception {
  mvc.perform(get(path(false)+"personal-payment-policy")).andExpect(status().isNotFound());
  mvc.perform(post(path(true)+"personal-payment-consent").header("X-RPB-Wallet","1").header("X-RPB-Wallet-Model","components-v2").contentType("application/json").content("{\"policyVersion\":1,\"limitCents\":500,\"accepted\":true,\"idempotencyKey\":\"consent\"}")).andExpect(status().isOk());
  verify(service).consent(scope,principal,new PersonalPaymentDtos.ConsentUpdate(1,500L,true,"consent"));
 }
 @Test void rejectsIdentityOverridesNumericStringsAndMissingModelBeforeService()throws Exception {
  mvc.perform(post(path(true)+"personal-codes").header("X-RPB-Wallet","1").header("X-RPB-Wallet-Model","components-v2").contentType("application/json").content("{\"customerId\":\"x\"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path(true)+"personal-payment-consent").header("X-RPB-Wallet","1").header("X-RPB-Wallet-Model","components-v2").contentType("application/json").content("{\"limitCents\":\"500\",\"policyVersion\":0,\"accepted\":true,\"idempotencyKey\":\"x\"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path(true)+"personal-codes").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WALLET_CLIENT_UPGRADE_REQUIRED"));verifyNoInteractions(service);
 }
 @Test void reconcileReturnsSafeClosureAndMapsMissingResults()throws Exception {
  when(service.reconcile(scope,actor,"missing")).thenReturn(new PersonalPaymentDtos.ChargeResult("not_submitted",null,null,store));
  mvc.perform(post(path(false)+"personal-scan-charges/reconcile").header("X-RPB-Wallet","1").header("X-RPB-Wallet-Model","components-v2").contentType("application/json").content("{\"idempotencyKey\":\"missing\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("not_submitted")).andExpect(jsonPath("$.storeId").value(store.toString()));
  when(service.result(scope,actor,"missing")).thenThrow(WalletException.missing());
  mvc.perform(get(path(false)+"personal-scan-charges/result?idempotencyKey=missing")).andExpect(status().isNotFound());
 }
 @Test void originAndAuthenticationBoundariesApply()throws Exception {
  mvc.perform(post(path(false)+"personal-scan-charges/reconcile").header("X-RPB-Wallet","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));mvc.perform(get(path(true)+"personal-payment-consent")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
}
