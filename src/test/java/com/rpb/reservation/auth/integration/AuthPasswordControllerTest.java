package com.rpb.reservation.auth.integration;
import com.rpb.reservation.auth.api.*;
import com.rpb.reservation.auth.application.*;
import com.rpb.reservation.auth.security.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AuthPasswordControllerTest{
 final AuthPasswordService service=mock(AuthPasswordService.class);
 final MockMvc mvc=MockMvcBuilders.standaloneSetup(new AuthPasswordController(service,new AuthCookieService())).build();
 static final String PATH="/api/v1/auth/password-change",BODY="{\"currentPassword\":\"old123\",\"newPassword\":\"new456\"}";
 @Test void currentSessionIsOnlyIdentityAndResponseHasNoSecrets()throws Exception{
  mvc.perform(post(PATH).cookie(new Cookie(AuthCookieService.COOKIE_NAME,"staff-session")).header("X-RPB-Account","1").contentType("application/json").content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(header().string("Cache-Control","no-store, private")).andExpect(jsonPath("$.sessionToken").doesNotExist());
  verify(service).change("staff-session","old123","new456");
 }
 @Test void anonymousAndCustomerOnlyCookiesCannotChangeStaffPassword()throws Exception{
  mvc.perform(post(PATH).header("X-RPB-Account","1").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
  mvc.perform(post(PATH).cookie(new Cookie("RPB_CUSTOMER_SESSION","customer")).header("X-RPB-Account","1").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
 @Test void missingMarkerAndCrossOriginRequestsAreRejected()throws Exception{
  mvc.perform(post(PATH).cookie(new Cookie(AuthCookieService.COOKIE_NAME,"staff")).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
  for(String site:new String[]{"cross-site","same-site"})mvc.perform(post(PATH).cookie(new Cookie(AuthCookieService.COOKIE_NAME,"staff")).header("X-RPB-Account","1").header("Sec-Fetch-Site",site).contentType("application/json").content(BODY)).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void accountTenantAndStoreOverridesAreRejected()throws Exception{
  for(String field:new String[]{"accountId","tenantId","storeId","username"})mvc.perform(post(PATH).cookie(new Cookie(AuthCookieService.COOKIE_NAME,"staff")).header("X-RPB-Account","1").contentType("application/json").content(BODY.substring(0,BODY.length()-1)+",\""+field+"\":\"other\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
}
