package com.rpb.reservation.loyalty;
import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.wallet.api.WalletPointsRequests.*;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PointsApiTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 @Test void rejectsFractionalStringBooleanZeroNegativeAndOverflowQuantities(){
  for(String q:new String[]{"1.5","1.0","\"1\"","true","0","-1","2147483648"})
   assertThatThrownBy(()->json.readValue("{\"quantity\":"+q+",\"reason\":\"理由\",\"idempotencyKey\":\"key\"}",Grant.class)).isInstanceOf(Exception.class);
 }
 @Test void rejectsUnexpectedMoneyIdentityAndShippingFieldsEvenWithGlobalUnknownFieldsIgnored(){
  for(String field:new String[]{"amount","currency","cashValue","customerId","programId","issuerId","shippingAddress"})
   assertThatThrownBy(()->json.readValue("{\"quantity\":1,\""+field+"\":\"x\",\"redemptionDescription\":\"礼品\"}",Redeem.class)).isInstanceOf(Exception.class);
 }
 @Test void validGrantAndRedemptionAndNoAccountRoundTrip()throws Exception{
  assertThat(json.readValue("{\"quantity\":100,\"reason\":\"手动积分\",\"idempotencyKey\":\"key\"}",Grant.class).command().quantity()).isEqualTo(100);
  assertThat(json.readValue("{\"quantity\":30,\"redemptionDescription\":\"咖啡\",\"expectedVersion\":1,\"idempotencyKey\":\"key\"}",Redeem.class).command().reason()).isEqualTo("兑换");
  var a=new Account(null,null,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),0,0);
  String encoded=json.writeValueAsString(a);assertThat(encoded).contains("\"accountId\":null");assertThat(json.readValue(encoded,Account.class)).isEqualTo(a);
 }
 @Test void actualControllerRejectsUnknownFieldsAndNonIntegralQuantityWithStableHttpErrors()throws Exception{
  var identity=org.mockito.Mockito.mock(com.rpb.reservation.wallet.api.WalletApiIdentity.class);
  var adapter=org.mockito.Mockito.mock(com.rpb.reservation.wallet.integration.WalletPointsAdapter.class);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.rpb.reservation.wallet.api.WalletPointsStaffController(identity,adapter)).setControllerAdvice(new com.rpb.reservation.wallet.api.WalletPointsErrorAdvice()).addFilters(new com.rpb.reservation.wallet.api.WalletRequestSecurityFilter()).build();
  String path="/api/v1/stores/"+java.util.UUID.randomUUID()+"/wallet/customers/"+java.util.UUID.randomUUID()+"/points/grants";
  for(String body:new String[]{"{\"quantity\":1.5,\"reason\":\"x\"}","{\"quantity\":1,\"amount\":100}"})
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("X-RPB-Wallet","1").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("POINTS_REQUEST_INVALID"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("X-RPB-Wallet","1").contentType("application/json").content("{\"quantity\":2147483648}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("POINTS_LIMIT_EXCEEDED"));
  org.mockito.Mockito.verifyNoInteractions(adapter);
 }
 @Test void pointsMutationsRequireSameOriginCustomHeaderAndReadsAreNeverCached()throws Exception{
  var filter=new com.rpb.reservation.wallet.api.WalletRequestSecurityFilter();
  String base="/api/v1/stores/"+java.util.UUID.randomUUID()+"/wallet/";
  for(String suffix:new String[]{"customers/1/points/grants","customers/1/points/deductions","customers/1/points/redemptions","points/entries/1/reverse"}){
   var request=new org.springframework.mock.web.MockHttpServletRequest("POST",base+suffix);var response=new org.springframework.mock.web.MockHttpServletResponse();
   filter.doFilter(request,response,(a,b)->fail("missing header"));assertThat(response.getStatus()).isEqualTo(403);
   request=new org.springframework.mock.web.MockHttpServletRequest("POST",base+suffix);request.addHeader("X-RPB-Wallet","1");request.addHeader("Sec-Fetch-Site","cross-site");response=new org.springframework.mock.web.MockHttpServletResponse();
   filter.doFilter(request,response,(a,b)->fail("cross site"));assertThat(response.getStatus()).isEqualTo(403);
  }
  var request=new org.springframework.mock.web.MockHttpServletRequest("GET",base+"customers/1/points");var response=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,response,(a,b)->{});assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
 }

}
