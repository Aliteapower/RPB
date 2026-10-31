package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.api.StorageRequestSecurityFilter;
import org.springframework.mock.web.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class StorageRequestSecurityTest {
 @Test void cookieBackedWritesRequireHeaderAndSameOrigin()throws Exception{
  for(String path:new String[]{"/api/v1/stores/x/wine-storage/locations","/api/v1/stores/x/wine-storage/locations/x","/api/v1/stores/x/wine-storage/scan-deliver","/api/v1/stores/x/wine-storage/custodians","/api/v1/stores/x/wine-storage/custodians/x","/api/v1/stores/x/wine-storage/registrations","/api/v1/stores/x/wine-storage/intents","/api/v1/public/stores/x/wine-storage/photos"}){
   for(String site:new String[]{"cross-site","same-site","missing-header"}){
    var req=new MockHttpServletRequest("POST",path);if(!site.equals("missing-header"))req.addHeader("X-RPB-Storage","1");req.addHeader("Sec-Fetch-Site",site);var res=new MockHttpServletResponse();var chain=new AtomicBoolean();
    new StorageRequestSecurityFilter().doFilter(req,res,(a,b)->chain.set(true));assertThat(res.getStatus()).isEqualTo(403);assertThat(chain.get()).isFalse();
   }
   var req=new MockHttpServletRequest("POST",path);req.addHeader("X-RPB-Storage","1");req.addHeader("Sec-Fetch-Site","same-origin");var res=new MockHttpServletResponse();var chain=new AtomicBoolean();new StorageRequestSecurityFilter().doFilter(req,res,(a,b)->chain.set(true));assertThat(chain.get()).isTrue();assertThat(res.getHeader("Cache-Control")).contains("no-store");
  }
 }
}
