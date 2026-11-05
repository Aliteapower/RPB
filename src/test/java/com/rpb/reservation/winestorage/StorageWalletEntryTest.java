package com.rpb.reservation.winestorage;

import com.rpb.reservation.catalog.application.StorageProductCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageWalletEntryTest {
    @Test void customerHomePublishesCurrentWalletAvailabilityWithoutGrantingItFromStorageAccess() {
        var tenant=UUID.randomUUID();var store=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
        var access=mock(StorageAccess.class);var repo=mock(StorageRepository.class);var catalog=mock(StorageProductCatalog.class);var stores=mock(SharedStoreIdentityQuery.class);
        var identity=mock(PlatformStore.class);when(identity.storeName()).thenReturn("QA");when(stores.active(tenant,store)).thenReturn(Optional.of(identity));
        when(repo.settings(scope)).thenReturn(new StorageDtos.Settings("customer_verification",0));when(access.enabled(scope)).thenReturn(true);
        var service=new StorageQueryService(access,repo,catalog,Clock.systemUTC(),stores);
        var principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"qa@example.invalid","QA");
        assertThat(service.customer(scope,principal).walletEnabled()).isFalse();
        when(access.walletEnabled(scope)).thenReturn(true);assertThat(service.customer(scope,principal).walletEnabled()).isTrue();
        when(access.walletEnabled(scope)).thenReturn(false);assertThat(service.customer(scope,principal).walletEnabled()).isFalse();
        verify(access,times(3)).customer(scope,principal,false);
    }
}
