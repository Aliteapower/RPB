package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StaffStoreAccessPolicyTest {
    final UUID tenant=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),a);
    final CurrentActor admin=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(a,c));
    @Test void createCannotAssignAnotherActiveStoreInTheSameTenant() {
        denied(()->StaffStoreAccessPolicy.requireCreate(scope,admin,List.of(a,b),a));
        assertThatCode(()->StaffStoreAccessPolicy.requireCreate(scope,admin,List.of(a,c),c)).doesNotThrowAnyException();
    }
    @Test void existingOutsideMembershipAndDefaultAreRetainedWhenEditingWithinManagedStores() {
        assertThatCode(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),b,List.of(a,b,c),b)).doesNotThrowAnyException();
        assertThatCode(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b,c),b,List.of(a,b),b)).doesNotThrowAnyException();
        assertThatCode(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),b,List.of(a,b),b)).doesNotThrowAnyException();
    }
    @Test void cannotAddOrRemoveMembershipOutsideAdministratorScope() {
        denied(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a),a,List.of(a,b),a));
        denied(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),a,List.of(a),a));
    }
    @Test void defaultMayRemainOutsideScopeButCannotBeChangedToOutsideScope() {
        assertThatCode(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),b,List.of(a,b),b)).doesNotThrowAnyException();
        assertThatCode(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),b,List.of(a,b),a)).doesNotThrowAnyException();
        denied(()->StaffStoreAccessPolicy.requireUpdate(scope,admin,List.of(a,b),a,List.of(a,b),b));
    }
    @Test void wrongTenantStoreRoleOrPermissionCannotManageMemberships() {
        for(var actor:List.of(new CurrentActor(UUID.randomUUID(),admin.actorId(),"staff",admin.roles(),admin.permissions(),admin.storeIds()),
            new CurrentActor(tenant,admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(b)),
            new CurrentActor(tenant,admin.actorId(),"staff",Set.of("store_staff"),admin.permissions(),admin.storeIds()),
            new CurrentActor(tenant,admin.actorId(),"staff",admin.roles(),Set.of(),admin.storeIds()),
            new CurrentActor(tenant,admin.actorId(),"platform_admin",admin.roles(),admin.permissions(),admin.storeIds()))) {
            denied(()->StaffStoreAccessPolicy.requireCreate(scope,actor,List.of(a),a));
        }
        denied(()->StaffStoreAccessPolicy.requireCreate(scope,null,List.of(a),a));
    }
    private void denied(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TenantAdminApiException.class,e->assertThat(e.code()).isEqualTo(TenantAdminApiErrorCode.FORBIDDEN));
    }
}
