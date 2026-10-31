package com.rpb.reservation.tenantadmin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.auth.authorization.StaffPermissionCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.persistence.StaffPermissionsRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffPermissionsServiceTest {
    final UUID tenant=UUID.randomUUID(), store=UUID.randomUUID(), staff=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final StaffPermissionsRepository repository=mock(StaffPermissionsRepository.class);
    final AppGateService gate=mock(AppGateService.class);
    final AuditLogRepositoryPort audit=mock(AuditLogRepositoryPort.class);
    final StaffPermissionsService service=new StaffPermissionsService(repository,gate,new StaffPermissionsAuditService(audit,new ObjectMapper()));
    CurrentActor admin;
    @BeforeEach void setup() {
        var permissions=new HashSet<>(StaffPermissionCatalog.permissions());permissions.add("tenant.admin.manage");
        admin=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),permissions,Set.of(store));
        when(repository.target(eq(scope),eq(staff),anyBoolean())).thenReturn(Optional.of(new StaffPermissionsRepository.Target("staff",Set.of("store_staff"),Set.of("wallet.view","wallet.charge.create"))));
        when(repository.find(scope,staff)).thenReturn(Optional.empty());
        when(gate.isStoreProductProvisioned(anyString(),eq(tenant),eq(store))).thenReturn(true);
        when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.allow(i.getArgument(0),tenant,store,null));
    }
    @Test void catalogueExcludesIdentityAndHardRoleOnlySettingsAndExpandsDependencies() {
        assertThat(StaffPermissionCatalog.permissions()).doesNotContain("tenant.admin.manage","platform.admin.manage","catalog.manage","wine_storage.custodians.manage","wallet.settings.manage");
        assertThat(StaffPermissionCatalog.expand(Set.of("wallet.cards.stamp","wine_storage.deliver","wallet.collection.confirm")))
            .contains("wallet.cards.stamp","wallet.cards.view","wallet.view","wine_storage.view","wine_storage.deliver","wallet.collection.confirm");
    }
    @Test void cardPlanManagementIncludesReadOnlyProductSelectionWithoutCatalogWrites() {
        assertThat(StaffPermissionCatalog.expand(Set.of("wallet.cards.manage")))
            .contains("wallet.cards.manage","wallet.cards.view","wallet.view","catalog.view")
            .doesNotContain("catalog.manage","catalog.publish");
    }
    @Test void paymentOptionsReflectThePermissionsActuallyCheckedByBusinessEndpoints() {
        assertThat(StaffPermissionCatalog.permissions()).doesNotContain("payment.verification.review","payment.proof_template.manage");
        var payment=StaffPermissionCatalog.groups().stream().filter(g->g.key().equals("payment")).findFirst().orElseThrow();
        var create=payment.permissions().stream().filter(p->p.code().equals("payment.intent.create")).findFirst().orElseThrow();
        assertThat(create.labelZh()).isEqualTo("建立及手动确认收款");
        assertThat(create.labelEn()).isEqualTo("Create and manually confirm payments");
    }
    @Test void legacyFallbackIsReadOnlyAndExplicitEmptyRevokesWithoutReturningToLegacy() {
        assertThat(service.get(scope,admin,staff).permissions()).containsExactly("wallet.charge.create","wallet.view");
        var result=service.put(scope,admin,staff,0L,List.of());
        assertThat(result.inherited()).isFalse();assertThat(result.version()).isEqualTo(1L);assertThat(result.permissions()).isEmpty();
        verify(repository).save(scope,staff,Set.of(),1L);verify(audit).append(eq(scope),any());
    }
    @Test void grantNormalizesDependenciesAndAuditsBeforeAndAfter() {
        var result=service.put(scope,admin,staff,0L,List.of("wine_storage.deliver"));
        assertThat(result.permissions()).containsExactly("wine_storage.deliver","wine_storage.view");
        verify(audit).append(eq(scope),argThat(log->log.actorId().equals(admin.actorId())&&log.metadata().contains("before")&&log.metadata().contains("wine_storage.deliver")));
    }
    @Test void unknownPermissionAndMissingCommandAreInvalid() {
        assertCode(()->service.put(scope,admin,staff,0L,List.of("tenant.admin.manage")),TenantAdminApiErrorCode.REQUEST_INVALID);
        assertCode(()->service.put(scope,admin,staff,null,List.of()),TenantAdminApiErrorCode.REQUEST_INVALID);
        assertCode(()->service.put(scope,admin,staff,0L,null),TenantAdminApiErrorCode.REQUEST_INVALID);
        verify(repository,never()).save(any(),any(),any(),anyLong());
    }
    @Test void noEscalationBeyondAdministratorsPermissionAndProductProvisioning() {
        var restricted=new CurrentActor(tenant,admin.actorId(),"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
        assertCode(()->service.put(scope,restricted,staff,0L,List.of("wine_storage.view")),TenantAdminApiErrorCode.FORBIDDEN);
        when(gate.isStoreProductProvisioned("wine_storage",tenant,store)).thenReturn(false);
        assertCode(()->service.put(scope,admin,staff,0L,List.of("wine_storage.view")),TenantAdminApiErrorCode.FORBIDDEN);
        assertThat(service.put(scope,restricted,staff,0L,List.of()).permissions()).isEmpty();
    }
    @Test void administrativeAndSelfAccountsAreNeverEditable() {
        for(var role:List.of("tenant_admin","store_manager","platform_admin")) {
            when(repository.target(eq(scope),eq(staff),anyBoolean())).thenReturn(Optional.of(new StaffPermissionsRepository.Target("staff",Set.of(role),Set.of())));
            assertCode(()->service.get(scope,admin,staff),TenantAdminApiErrorCode.FORBIDDEN);
        }
        assertCode(()->service.get(scope,admin,admin.actorId()),TenantAdminApiErrorCode.FORBIDDEN);
    }
    @Test void wrongTenantStoreAndNonAdminCannotReachTarget() {
        for(var actor:List.of(new CurrentActor(UUID.randomUUID(),admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(store)),
            new CurrentActor(tenant,admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of()),
            new CurrentActor(tenant,admin.actorId(),"staff",Set.of("store_staff"),admin.permissions(),Set.of(store)))) {
            assertCode(()->service.get(scope,actor,staff),TenantAdminApiErrorCode.FORBIDDEN);
        }
        verifyNoInteractions(repository);
    }
    @Test void missingStaffIsNotFoundAndStaleDifferentSnapshotConflicts() {
        when(repository.target(eq(scope),eq(staff),anyBoolean())).thenReturn(Optional.empty());
        assertCode(()->service.get(scope,admin,staff),TenantAdminApiErrorCode.STAFF_NOT_FOUND);
        setup();when(repository.find(scope,staff)).thenReturn(Optional.of(new StaffPermissionsRepository.Configuration(2L,Set.of("wallet.view"))));
        assertCode(()->service.put(scope,admin,staff,1L,List.of()),TenantAdminApiErrorCode.PERMISSION_VERSION_CONFLICT);
        verify(repository,never()).save(any(),any(),any(),anyLong());verifyNoInteractions(audit);
    }
    @Test void exactReplayDoesNotIncrementVersionOrAuditAgain() {
        when(repository.find(scope,staff)).thenReturn(Optional.of(new StaffPermissionsRepository.Configuration(1L,Set.of("wallet.view"))));
        assertThat(service.put(scope,admin,staff,0L,List.of("wallet.view")).version()).isEqualTo(1L);
        verify(repository,never()).save(any(),any(),any(),anyLong());verifyNoInteractions(audit);
    }
    @Test void disabledCommercialAccessDoesNotPreventRevokingOrBypassProductGate() {
        when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.deny(i.getArgument(0),tenant,store,null,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED));
        assertThat(service.get(scope,admin,staff).groups()).allMatch(g->!g.enabled());
        assertThat(service.put(scope,admin,staff,0L,List.of()).permissions()).isEmpty();
    }
    private void assertCode(Runnable task,TenantAdminApiErrorCode code) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(TenantAdminApiException.class,e->assertThat(e.code()).isEqualTo(code));
    }
}
