package com.rpb.reservation.wallet.marketing;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.wallet.funding.application.WalletFundingOperations;
import com.rpb.reservation.wallet.integration.WalletPointsAdapter;
import com.rpb.reservation.walkin.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Real local SecurityFilterChain plus controller identity boundary; no business services run. */
@WebMvcTest({WalletMarketingController.class,WalletCustomerTopupController.class,WalletFundingController.class,WalletPointsStaffController.class,WalletPointsCustomerController.class,WalletCustomerIdentityController.class})
@Import({LocalRuntimeSecurityConfiguration.class,LocalRuntimeCurrentActorProvider.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WalletMarketingLocalSecurityTest {
 @Autowired MockMvc mvc;
 @MockBean WalletApiIdentity identity;
 @MockBean WalletMarketingService marketing;
 @MockBean WalletMarketingConfigurationService config;
 @MockBean WalletCustomerTopupService self;
 @MockBean MarketingRequestReader reader;
 @MockBean WalletPointsAdapter points;
 @MockBean WalletFundingOperations funding;
 @MockBean com.rpb.reservation.wallet.application.WalletCustomerIdentityService customerIdentity;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
 final String id="20000000-0000-0000-0000-000000000983";
 String staff(){return "/api/v1/stores/"+id+"/wallet";}
 String customer(){return "/api/v1/public/stores/"+id+"/wallet";}
 @BeforeEach void unauthenticated(){when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));}
 @Test void exactNewStaffRoutesReachIdentityAndNeverBusinessServices()throws Exception{
  for(String path:new String[]{"/points/context","/marketing/settings","/marketing/campaigns","/marketing/campaigns/"+id,"/topup-offers","/accounts/"+id+"/funding-lots","/customers/"+id+"/points"})mvc.perform(get(staff()+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  for(String path:new String[]{"/marketing/settings","/marketing/campaigns/"+id+"/draft"})mvc.perform(put(staff()+path).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  for(String path:new String[]{"/marketing/campaigns","/marketing/campaigns/"+id+"/publish","/marketing/campaigns/"+id+"/disable","/marketing/campaigns/"+id+"/archive","/topup-quotes","/customer-codes/resolve","/charge-previews","/refund-previews","/funding-lots/"+id+"/exit-bonus","/customers/"+id+"/points/grants","/customers/"+id+"/points/deductions","/customers/"+id+"/points/redemptions","/points/entries/"+id+"/reverse"})mvc.perform(post(staff()+path).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  verifyNoInteractions(marketing,config,self,points,funding,customerIdentity);
 }
 @Test void exactNewCustomerRoutesUseCustomerIdentityAndNoStore()throws Exception{
  for(String path:new String[]{"/topup-offers","/topups/"+id,"/topups/"+id+"/paynow","/points"})mvc.perform(get(customer()+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  for(String path:new String[]{"/customer-codes","/topup-quotes","/topups","/topups/"+id+"/declare-paid","/topups/"+id+"/cancel"})mvc.perform(post(customer()+path).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  verifyNoInteractions(marketing,config,self,points,funding,customerIdentity);
 }
 @Test void wrongMethodsAndUnlistedDeepRoutesRemainForbidden()throws Exception{
  for(String path:new String[]{"/marketing/settings","/topup-quotes","/customer-codes/resolve","/charge-previews","/refund-previews","/funding-lots/"+id+"/exit-bonus","/customers/"+id+"/points/grants"})mvc.perform(delete(staff()+path).header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
  mvc.perform(get(staff()+"/marketing/campaigns/"+id+"/internal-debug")).andExpect(status().isForbidden());mvc.perform(post(customer()+"/points/grants").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(customer()+"/topups/"+id+"/declare-paid").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void newWritesRetainWalletHeaderAndSameOriginCsrfBoundary()throws Exception{
  for(String path:new String[]{staff()+"/marketing/campaigns",staff()+"/customers/"+id+"/points/grants",staff()+"/charge-previews",customer()+"/customer-codes",staff()+"/customer-codes/resolve",customer()+"/topups",customer()+"/topups/"+id+"/declare-paid"}){
   mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   for(String site:new String[]{"cross-site","same-site"})mvc.perform(post(path).header("X-RPB-Wallet","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(post(path).header("X-RPB-Wallet","1").header("Sec-Fetch-Site","same-origin").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  }
  verifyNoInteractions(marketing,config,self,points,funding,customerIdentity);
 }
}
