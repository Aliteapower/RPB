package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.walkin.auth.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TenantEntryLinksController.class)
@Import({TenantAdminScopeResolver.class,LocalRuntimeSecurityConfiguration.class,LocalRuntimeCurrentActorProvider.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class TenantEntryLinksSecurityTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockBean TenantEntryLinksService service;
    @MockBean CurrentActorProvider actors;
    @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
    @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
    String url(){return "/api/v1/stores/"+store+"/tenant-admin/entry-links";}
    @Test void anonymousCannotUseForgedTenantOrActorHeaders() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(get(url()).header("X-Test-Tenant-Id",tenant).header("X-Test-Actor-Id",UUID.randomUUID())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }
    @Test void onlyTenantAdminsWithPermissionAndAuthorizedStoreCanReadTheirOwnScope() throws Exception {
        for(var actor:List.of(new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("tenant.admin.manage"),Set.of(store)),new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of(),Set.of(store)),new CurrentActor(null,UUID.randomUUID(),"platform_admin",Set.of("platform_admin"),Set.of("tenant.admin.manage"),Set.of(store)))) {
            when(actors.currentActor()).thenReturn(Optional.of(actor));mvc.perform(get(url())).andExpect(status().isForbidden());
        }
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(UUID.randomUUID()))));
        mvc.perform(get(url())).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));verifyNoInteractions(service);
    }
    @Test void sessionDeterminesTenantAndMethodRemainsReadOnly() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store))));
        mvc.perform(get(url()).header("X-Test-Tenant-Id",UUID.randomUUID())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        verify(service).get(argThat(s->s.tenantId().value().equals(tenant)&&s.storeId().value().equals(store)));
        mvc.perform(post(url())).andExpect(status().isMethodNotAllowed());
    }
    @Test void missingAndPersistenceFailuresKeepStableErrorsWithoutDatabaseDetails() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store))));
        when(service.get(any())).thenThrow(new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
        mvc.perform(get(url())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TENANT_PROFILE_NOT_FOUND"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL connection details")).when(service).get(any());
        mvc.perform(get(url())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("PERSISTENCE_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
}
