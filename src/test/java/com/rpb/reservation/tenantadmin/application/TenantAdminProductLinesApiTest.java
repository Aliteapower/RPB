package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.tenantadmin.api.*;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantAdminProductLinesApiTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
    final CurrentActor admin=new CurrentActor(tenant,UUID.randomUUID(),"admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
    final AtomicReference<CurrentActor> actor=new AtomicReference<>(admin);
    final StoreProductSettingsService service=mock(StoreProductSettingsService.class);
    MockMvc mvc;
    String base(){return "/api/v1/stores/"+store+"/tenant-admin/product-lines";}
    @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new TenantAdminProductLinesController(new TenantAdminScopeResolver(()->Optional.ofNullable(actor.get())),service)).build();}
    @Test void closedProductsRemainManageableWithExplicitStateAndPrivateCache()throws Exception{
        when(service.list(any())).thenReturn(List.of(new StoreProductSetting("wallet",false,true,null)));
        when(service.setEnabled(any(),eq(admin),eq("wallet"),eq(true))).thenReturn(new StoreProductSetting("wallet",true,true,null));
        mvc.perform(get(base())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.products[0].enabled").value(false));
        mvc.perform(put(base()+"/wallet").header("X-RPB-Product-Settings","1").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.products[0].enabled").value(true));
    }
    @Test void anonymousStaffAndUnassignedStoresCannotReadOrWrite()throws Exception{
        actor.set(null);mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(put(base()+"/wallet").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isUnauthorized());
        actor.set(new CurrentActor(tenant,admin.actorId(),"staff",Set.of("store_staff"),Set.of("tenant.admin.manage"),Set.of(store)));
        mvc.perform(get(base())).andExpect(status().isForbidden());
        actor.set(admin);mvc.perform(get("/api/v1/stores/"+UUID.randomUUID()+"/tenant-admin/product-lines")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void missingNullAndCrossSiteCommandsCannotWrite()throws Exception{
        for(String body:List.of("{}","{\"enabled\":null}","invalid"))mvc.perform(put(base()+"/wallet").header("X-RPB-Product-Settings","1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
        for(String site:List.of("cross-site","same-site"))mvc.perform(put(base()+"/wallet").header("X-RPB-Product-Settings","1").header("Sec-Fetch-Site",site).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isForbidden());
        mvc.perform(put(base()+"/wallet").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void unavailableAndUnknownProductsHaveStableErrors()throws Exception{
        when(service.setEnabled(any(),any(),eq("wallet"),eq(true))).thenThrow(new StoreProductSettingsException(StoreProductSettingsException.Reason.UNAVAILABLE));
        mvc.perform(put(base()+"/wallet").header("X-RPB-Product-Settings","1").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRODUCT_LINE_UNAVAILABLE"));
        when(service.setEnabled(any(),any(),eq("payment"),eq(true))).thenThrow(new StoreProductSettingsException(StoreProductSettingsException.Reason.INVALID_PRODUCT));
        mvc.perform(put(base()+"/payment").header("X-RPB-Product-Settings","1").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }
}
