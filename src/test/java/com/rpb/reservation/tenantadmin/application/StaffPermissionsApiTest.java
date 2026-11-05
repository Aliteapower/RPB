package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.tenantadmin.api.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffPermissionsApiTest {
    final UUID tenant=UUID.randomUUID(), store=UUID.randomUUID(), staff=UUID.randomUUID();
    final CurrentActor admin=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
    final AtomicReference<CurrentActor> actor=new AtomicReference<>(admin);
    final StaffPermissionsService service=mock(StaffPermissionsService.class);
    MockMvc mvc;
    String path() { return "/api/v1/stores/"+store+"/tenant-admin/staff/"+staff+"/permissions"; }
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new StaffPermissionsController(new TenantAdminScopeResolver(()->Optional.ofNullable(actor.get())),service)).build();
    }
    @Test void readAndWriteUsePrivateCacheAndExplicitContract() throws Exception {
        var view=new StaffPermissionsService.View(true,1L,false,List.of("wallet.view"),List.of());
        when(service.get(any(),eq(admin),eq(staff))).thenReturn(view);
        when(service.put(any(),eq(admin),eq(staff),eq(0L),eq(List.of("wallet.view")))).thenReturn(view);
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
            .andExpect(jsonPath("$.permissions[0]").value("wallet.view"));
        mvc.perform(put(path()).header("X-RPB-Staff-Permissions","1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":0,\"permissions\":[\"wallet.view\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }
    @Test void anonymousStaffAndUnassignedStoreCannotReachService() throws Exception {
        actor.set(null);mvc.perform(get(path())).andExpect(status().isUnauthorized());
        actor.set(new CurrentActor(tenant,admin.actorId(),"staff",Set.of("store_staff"),Set.of("tenant.admin.manage"),Set.of(store)));
        mvc.perform(get(path())).andExpect(status().isForbidden());
        actor.set(admin);mvc.perform(get(path().replace(store.toString(),UUID.randomUUID().toString()))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void crossSiteMissingHeaderAndMalformedPayloadCannotWrite() throws Exception {
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"permissions\":[]}"))
            .andExpect(status().isForbidden());
        for(String site:List.of("same-site","cross-site")) mvc.perform(put(path()).header("X-RPB-Staff-Permissions","1")
            .header("Sec-Fetch-Site",site).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"permissions\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(path()).header("X-RPB-Staff-Permissions","1").contentType(MediaType.APPLICATION_JSON).content("invalid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }
    @Test void staleSnapshotReturnsStable409() throws Exception {
        when(service.put(any(),any(),any(),any(),any())).thenThrow(new TenantAdminApiException(TenantAdminApiErrorCode.PERMISSION_VERSION_CONFLICT));
        mvc.perform(put(path()).header("X-RPB-Staff-Permissions","1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":0,\"permissions\":[]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PERMISSION_VERSION_CONFLICT"));
    }
}
