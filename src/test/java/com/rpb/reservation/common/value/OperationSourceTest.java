package com.rpb.reservation.common.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationSourceTest {

    @Test
    void mapsStoreAdminActorTypesToStaffSource() {
        assertThat(OperationSource.fromActorType("platform_admin")).isEqualTo("staff");
        assertThat(OperationSource.fromActorType("tenant_admin")).isEqualTo("staff");
        assertThat(OperationSource.fromActorType("store_manager")).isEqualTo("staff");
    }

    @Test
    void preservesSupportedSourceCategories() {
        assertThat(OperationSource.fromActorType(" customer ")).isEqualTo("customer");
        assertThat(OperationSource.fromActorType("INTEGRATION")).isEqualTo("integration");
        assertThat(OperationSource.fromSourceOrActor("system", "platform_admin")).isEqualTo("system");
    }

    @Test
    void fallsBackToActorCategoryWhenRawSourceIsNotSupported() {
        assertThat(OperationSource.fromSourceOrActor("wechat", "customer")).isEqualTo("customer");
        assertThat(OperationSource.fromSourceOrActor("front_desk", "platform_admin")).isEqualTo("staff");
    }
}
