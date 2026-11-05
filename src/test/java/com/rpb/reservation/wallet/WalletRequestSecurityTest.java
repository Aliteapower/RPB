package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.api.WalletRequestSecurityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class WalletRequestSecurityTest {
    @Test void walletMutationRequiresCustomHeaderAndRejectsCrossSiteRequests() throws Exception {
        var filter=new WalletRequestSecurityFilter();
        for(String site:new String[]{"cross-site","same-site"}) {
            var request=new MockHttpServletRequest("POST","/api/v1/public/stores/123/wallet/bills/456/pay");
            request.addHeader("X-RPB-Wallet","1");request.addHeader("Sec-Fetch-Site",site);
            var response=new MockHttpServletResponse();var called=new AtomicBoolean();filter.doFilter(request,response,(a,b)->called.set(true));
            assertThat(response.getStatus()).isEqualTo(403);assertThat(called).isFalse();
        }
        var request=new MockHttpServletRequest("POST","/api/v1/stores/123/wallet/topups");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(a,b)->fail("missing CSRF header"));assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void readsAndUnrelatedProductsRemainUnaffected() throws Exception {
        for(var path:new String[]{"/api/v1/stores/123/wallet/context","/api/v1/stores/123/payments/intents"}) {
            var called=new AtomicBoolean();new WalletRequestSecurityFilter().doFilter(new MockHttpServletRequest("GET",path),new MockHttpServletResponse(),(a,b)->called.set(true));assertThat(called).isTrue();
        }
    }
    @Test void sameOriginCustomHeaderPermitsMutationToReachAuthentication() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/public/stores/123/wallet/payment-codes");request.addHeader("X-RPB-Wallet","1");request.addHeader("Sec-Fetch-Site","same-origin");
        var called=new AtomicBoolean();new WalletRequestSecurityFilter().doFilter(request,new MockHttpServletResponse(),(a,b)->called.set(true));assertThat(called).isTrue();
    }
}
