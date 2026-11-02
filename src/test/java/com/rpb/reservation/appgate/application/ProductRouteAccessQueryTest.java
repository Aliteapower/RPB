package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRouteAccessQueryTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),account=UUID.randomUUID();
    final AppGateService gate=mock(AppGateService.class);
    final ProductRouteAccessQuery query=new ProductRouteAccessQuery(gate);
    CurrentActor actor(Set<String> roles,Set<String> permissions){return new CurrentActor(tenant,account,"staff",roles,permissions,Set.of(store));}
    void commercial(boolean allowed){when(gate.evaluate(any())).thenAnswer(i->{var r=(AppGateAccessRequest)i.getArgument(0);assertThat(r.tenantId()).isEqualTo(tenant);assertThat(r.storeId()).isEqualTo(store);assertThat(r.actor().tenantId()).isEqualTo(tenant);assertThat(r.requiredPermission()).isNull();return allowed?AppGateDecision.allow(r.appKey(),tenant,store,null):AppGateDecision.deny(r.appKey(),tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED);});}
    ProductRouteAccessQuery.ProductAccess product(CurrentActor actor,String key){return query.find(actor,store).stream().filter(p->p.appKey().equals(key)).findFirst().orElseThrow();}
    @Test void businessAccessIsIndependentOfMenuVisibilityAndPreservesStorePermissionScope(){
        commercial(true);var actor=actor(Set.of("store_staff"),Set.of("payment.intent.create"));
        var result=product(actor,"payment");assertThat(result.businessAllowed()).isTrue();assertThat(result.denialReason()).isNull();
        verify(gate,never()).visibleApps(any(),any());
        assertThat(product(actor,"daily_ledger").businessAllowed()).isFalse();
    }
    @Test void existingTenantAdministratorConfigurationAccessDoesNotNeedCashierPermissions(){
        commercial(true);assertThat(product(actor(Set.of("tenant_admin"),Set.of("tenant.admin.manage")),"payment").businessAllowed()).isTrue();
        assertThat(product(actor(Set.of("store_manager"),Set.of("tenant.admin.manage")),"payment").businessAllowed()).isFalse();
        assertThat(product(actor(Set.of("tenant_admin"),Set.of()),"payment").businessAllowed()).isFalse();
    }
    @Test void expiredBusinessKeepsOnlyRegisteredHistoryAndRoleSpecificQueueRoute(){
        commercial(false);when(gate.evaluateHistory(any())).thenAnswer(i->{var r=(AppGateAccessRequest)i.getArgument(0);return AppGateDecision.allow(r.appKey(),tenant,store,r.requiredPermission());});
        var result=product(actor(Set.of("store_staff"),Set.of("queue.view")),"reservation_queue");
        assertThat(result.businessAllowed()).isFalse();assertThat(result.denialReason()).isEqualTo("STORE_APP_EXPIRED");
        assertThat(result.historyAllowed()).isTrue();assertThat(result.historyRoute()).isEqualTo("/stores/"+store+"/queue-tickets?history=1");
    }
    @Test void activeBusinessStillAllowsExplicitReadOnlyHistoryWithPointsOnlyRoute(){
        commercial(true);when(gate.evaluateHistory(any())).thenAnswer(i->{var r=(AppGateAccessRequest)i.getArgument(0);return AppGateDecision.allow(r.appKey(),tenant,store,r.requiredPermission());});
        var result=product(actor(Set.of("store_staff"),Set.of("wallet.points.view")),"wallet");
        assertThat(result.businessAllowed()).isTrue();assertThat(result.historyAllowed()).isTrue();assertThat(result.historyRoute()).endsWith("history=1&category=points");
    }
    @Test void historyMustBeAllowedByGateAndCannotBeInventedByAdminRole(){
        commercial(true);when(gate.evaluateHistory(any())).thenAnswer(i->{var r=(AppGateAccessRequest)i.getArgument(0);return AppGateDecision.deny(r.appKey(),tenant,store,r.requiredPermission(),AppGateDenyReason.TENANT_APP_NOT_ENABLED);});
        var result=product(actor(Set.of("store_staff"),Set.of("payment.intent.view")),"payment");assertThat(result.historyAllowed()).isFalse();assertThat(result.historyRoute()).isEmpty();
        var admin=product(actor(Set.of("tenant_admin"),Set.of("tenant.admin.manage")),"payment");assertThat(admin.historyAllowed()).isFalse();
    }
    @Test void missingActorCustomerPlatformAndForeignStoreReturnNoQualifications(){
        assertThat(query.find(null,store)).isEmpty();
        assertThat(query.find(actor(Set.of(),Set.of()),UUID.randomUUID())).isEmpty();
        assertThat(query.find(new CurrentActor(null,account,"staff",Set.of(),Set.of(),Set.of(store)),store)).isEmpty();
        assertThat(query.find(new CurrentActor(tenant,account,"customer",Set.of(),Set.of("payment.intent.view"),Set.of(store)),store)).isEmpty();
        assertThat(query.find(actor(Set.of("platform_admin"),Set.of("payment.intent.view")),store)).isEmpty();
        verifyNoInteractions(gate);
    }
}
