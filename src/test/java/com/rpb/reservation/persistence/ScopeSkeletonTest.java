package com.rpb.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScopeSkeletonTest {

    @Test
    void persistenceAccessScopesAreAvailable() throws Exception {
        assertThat(Class.forName("com.rpb.reservation.common.scope.TenantScope").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.scope.StoreScope").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.scope.PlatformScope").isRecord()).isTrue();
    }
}
