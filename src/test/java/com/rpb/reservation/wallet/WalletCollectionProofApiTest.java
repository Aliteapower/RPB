package com.rpb.reservation.wallet;
import com.rpb.reservation.wallet.proof.*;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class WalletCollectionProofApiTest {
 final WalletApiIdentity identity=mock(WalletApiIdentity.class);final WalletCollectionProofService service=mock(WalletCollectionProofService.class);
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),id=UUID.randomUUID();
 final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.view","wallet.collection.confirm"),Set.of(store));MockMvc mvc;
 @BeforeEach void setup(){when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);mvc=MockMvcBuilders.standaloneSetup(new WalletCollectionProofController(identity,service)).setControllerAdvice(new WalletErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();}
 @Test void privateImageRetainsOriginalBytesAndCannotBeContentSniffed()throws Exception {
  when(service.image(scope,actor,id)).thenReturn(new WalletProofViews.Image(new byte[]{1,2,3},"image/png"));
  mvc.perform(get(base()+"/images/"+id)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1,2,3}));
 }
 @Test void multipartScanPassesOnlySessionScopeAndNeverRequiresPaymentProductPermission()throws Exception {
  var file=new MockMultipartFile("image","receipt.png","image/png",new byte[]{1,2,3});
  mvc.perform(multipart(base()+"/topup/"+id+"/scan").file(file).param("channel","paynow").param("amount","10").param("idempotencyKey","upload-one").header("X-RPB-Wallet","1")).andExpect(status().isOk());
  verify(service).scan(scope,actor,"topup",id,"paynow","10","upload-one","receipt.png","image/png",new byte[]{1,2,3});
 }
 @Test void oversizedImageNeverReachesOcr()throws Exception {
  mvc.perform(multipart(base()+"/topup/"+id+"/scan").file(new MockMultipartFile("image","big.png","image/png",new byte[5242881])).param("channel","paynow").param("amount","10").param("idempotencyKey","big").header("X-RPB-Wallet","1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WALLET_REQUEST_INVALID"));verifyNoInteractions(service);
 }
 @Test void legacyConfirmationRetainsItsOriginalNullFieldsAndOmitsOnlyOptionalProof()throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var command=new com.rpb.reservation.wallet.application.WalletCommands.CollectionConfirm("paynow","10","202012345A","BANK","T1",null,true,"key");
  var serialized=mapper.readTree(mapper.writeValueAsString(command));org.assertj.core.api.Assertions.assertThat(serialized.has("note")).isTrue();org.assertj.core.api.Assertions.assertThat(serialized.get("note").isNull()).isTrue();org.assertj.core.api.Assertions.assertThat(serialized.has("proofId")).isFalse();
 }
 String base(){return "/api/v1/stores/"+store+"/wallet/collection-proofs";}
}
