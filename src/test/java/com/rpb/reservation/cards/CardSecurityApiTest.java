package com.rpb.reservation.cards;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.command.CardCommandService;
import com.rpb.reservation.cards.entitlement.CardHoldingService;
import com.rpb.reservation.cards.plan.CardPlanService;
import com.rpb.reservation.cards.stamp.StampService;
import com.rpb.reservation.cards.purchase.PurchaseService;
import com.rpb.reservation.cards.redemption.RedemptionService;
import com.rpb.reservation.catalog.api.CatalogRequestSecurityFilter;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.cards.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.integration.WalletCardsAdapter;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
class CardSecurityApiTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);
 CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wallet.cards.redeem"),Set.of(store));
 WalletApiIdentity identity=mock(WalletApiIdentity.class);CardPlanService plans=mock(CardPlanService.class);MockMvc mvc;
 @BeforeEach void setup(){when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);mvc=standaloneSetup(new WalletCardsStaffController(identity,plans,mock(CardHoldingService.class),mock(StampService.class),mock(RedemptionService.class),mock(PurchaseService.class),mock(CardAccess.class),mock(CardCommandService.class),mock(WalletDirectory.class),mock(WalletCustomerIdentityService.class))).setControllerAdvice(new CardErrorAdvice()).addFilters(new WalletRequestSecurityFilter(),new CatalogRequestSecurityFilter()).build();}
 @Test void cookieMutationNeedsSameOriginMarkerAndNeverRunsBusinessOnDenial()throws Exception{mvc.perform(post("/api/v1/stores/"+store+"/wallet/cards/plans").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(plans);mvc.perform(post("/api/v1/stores/"+store+"/wallet/cards/plans").header("X-RPB-Wallet","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());}
 @Test void anonymousAndBusinessDenialsUseStableCodes()throws Exception{when(identity.staff()).thenThrow(new com.rpb.reservation.wallet.domain.WalletException("WALLET_UNAUTHENTICATED",401,"login"));mvc.perform(get("/api/v1/stores/"+store+"/wallet/cards/plans")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WALLET_UNAUTHENTICATED"));reset(identity);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);when(plans.staff(scope,actor)).thenThrow(CardException.forbidden());mvc.perform(get("/api/v1/stores/"+store+"/wallet/cards/plans")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CARD_FORBIDDEN"));}
 @Test void cardPortRetainsRealWalletPermissionStoreAndCommercialGate(){var gate=mock(AppGateService.class);var directory=mock(WalletDirectory.class);var access=new WalletAccess(gate,directory);var port=new WalletCardsAdapter(access,directory);when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));assertThatThrownBy(()->port.staff(scope,actor,"wallet.cards.redeem",true)).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);assertThatThrownBy(()->port.staff(new StoreScope(new TenantId(tenant),UUID.randomUUID()),actor,"wallet.cards.redeem",true)).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);assertThatThrownBy(()->port.staff(scope,actor,"wallet.cards.manage",true)).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);}
 @Test void presentationEndpointRequiresCustomerIdentityAndSameOriginAndReturnsBoundRequest()throws Exception{
  var redeem=mock(RedemptionService.class);var principal=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"customer@test.invalid","Customer");
  when(identity.customer(any())).thenReturn(principal);UUID card=UUID.randomUUID(),request=UUID.randomUUID();
  var body=new CardDtos.PresentationCreate(2,"2026-10-26","key");
  when(redeem.present(scope,principal,card,body)).thenReturn(new CardDtos.Presentation(request,"CA."+"A".repeat(43),java.time.Instant.parse("2026-10-26T01:02:00Z")));
  var customerMvc=standaloneSetup(new WalletCardsCustomerController(identity,plans,mock(CardHoldingService.class),redeem,mock(PurchaseService.class),mock(CardAccess.class),mock(CardCommandService.class))).setControllerAdvice(new CardErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();
  String url="/api/v1/public/stores/"+store+"/wallet/cards/holdings/"+card+"/presentation",json="{\"quantity\":2,\"businessDay\":\"2026-10-26\",\"idempotencyKey\":\"key\"}";
  customerMvc.perform(post(url).contentType("application/json").content(json)).andExpect(status().isForbidden());verifyNoInteractions(redeem);
  customerMvc.perform(post(url).header("X-RPB-Wallet","1").contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(request.toString())).andExpect(jsonPath("$.code").value("CA."+"A".repeat(43)));
  when(identity.customer(any())).thenThrow(new com.rpb.reservation.wallet.domain.WalletException("WALLET_UNAUTHENTICATED",401,"login"));
  customerMvc.perform(post(url).header("X-RPB-Wallet","1").contentType("application/json").content(json)).andExpect(status().isUnauthorized());
 }
}
