package com.rpb.reservation.auth.security;

import com.rpb.reservation.auth.application.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthStoreScopeFilterTest {
    final UUID tenant=UUID.randomUUID(), account=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
    final AuthApplicationService service=mock(AuthApplicationService.class);
    final AuthSessionFilter filter=new AuthSessionFilter(new AuthCookieService(),service);

    AuthPrincipal principal(Set<String> selected) {
        return new AuthPrincipal(account,tenant,"staff","Staff","staff",a,Set.of(a,b),Set.of("store_staff"),
            Set.of("wallet.view","wallet.charge.create"),Map.of(a,selected));
    }
    MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET",path);
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME,"same-session"));
        return request;
    }
    CurrentActor authenticate(MockHttpServletRequest request) throws Exception {
        SecurityContextHolder.clearContext();
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{});
        return (CurrentActor)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void pathStoreIsAuthoritativeEvenWhenQueryNamesMorePrivilegedStore() throws Exception {
        when(service.authenticateSession("same-session")).thenReturn(Optional.of(new AuthSessionAuthentication(principal(Set.of()),Instant.now().plusSeconds(60))));
        var request=request("/api/v1/stores/"+a+"/wallet/context"); request.setParameter("storeId",b.toString());
        assertThat(authenticate(request).permissions()).isEmpty();
    }
    @Test void siblingStoreKeepsLegacyPermissionWhenNoConfigurationExists() throws Exception {
        when(service.authenticateSession(anyString())).thenReturn(Optional.of(new AuthSessionAuthentication(principal(Set.of()),Instant.now().plusSeconds(60))));
        assertThat(authenticate(request("/api/v1/stores/"+b+"/wallet/context")).permissions()).contains("wallet.charge.create");
    }
    @Test void meAppsUsesTheRequestedStorePermissions() throws Exception {
        when(service.authenticateSession(anyString())).thenReturn(Optional.of(new AuthSessionAuthentication(principal(Set.of("wine_storage.view")),Instant.now().plusSeconds(60))));
        var request=request("/api/me/apps"); request.setParameter("storeId",a.toString());
        assertThat(authenticate(request).permissions()).containsExactly("wine_storage.view");
    }
    @Test void sameSessionReloadsRevocationOnNextRequest() throws Exception {
        when(service.authenticateSession(anyString())).thenReturn(
            Optional.of(new AuthSessionAuthentication(principal(Set.of("wallet.view")),Instant.now().plusSeconds(60))),
            Optional.of(new AuthSessionAuthentication(principal(Set.of()),Instant.now().plusSeconds(60))));
        assertThat(authenticate(request("/api/v1/stores/"+a+"/wallet/context")).hasPermission("wallet.view")).isTrue();
        assertThat(authenticate(request("/api/v1/stores/"+a+"/wallet/context")).hasPermission("wallet.view")).isFalse();
    }
    @Test void contextPathIsRemovedAndUnscopedRequestsIgnoreStoreQuery() {
        var request=request("/rpb/api/v1/stores/"+a+"/wallet/context"); request.setContextPath("/rpb");
        assertThat(RequestStoreScope.resolve(request)).isEqualTo(a);
        var me=request("/api/v1/auth/me"); me.setParameter("storeId",a.toString());
        assertThat(RequestStoreScope.resolve(me)).isNull();
        var invalid=request("/api/v1/stores/bad-id/wallet"); invalid.setParameter("storeId",b.toString());
        assertThat(RequestStoreScope.resolve(invalid)).isNull();
    }
}
