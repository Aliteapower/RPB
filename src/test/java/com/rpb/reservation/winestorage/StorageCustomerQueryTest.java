package com.rpb.reservation.winestorage;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCustomerQueryTest {
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
 final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 final StorageAccess access=mock(StorageAccess.class);
 final CustomerDirectoryQuery directory=mock(CustomerDirectoryQuery.class);
 final StorageCustomerQuery service=new StorageCustomerQuery(access,directory);
 CurrentActor actor(String... permissions){return new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of(permissions),Set.of(store));}
 @Test void trimmedBoundedTenantDirectorySearchReturnsOnlyIdentityFields(){
  var actor=actor("wine_storage.view","wine_storage.receive");var value=mock(CustomerManagementItem.class);when(value.id()).thenReturn(customer);when(value.displayName()).thenReturn("A");when(value.phoneE164()).thenReturn("+6580000000");when(directory.search(scope.tenantScope(),"8000")).thenReturn(List.of(value));
  assertThat(service.search(scope,actor," 8000 ")).containsExactly(new StorageCustomerQuery.Customer(customer,"A","+6580000000"));verify(access).staff(scope,actor,"wine_storage.view",false);verify(directory).search(scope.tenantScope(),"8000");
 }
 @Test void blankQueryDoesNotEnumerateCustomersAndLongQueryIsRejected(){var a=actor("wine_storage.view","wine_storage.deliver");assertThat(service.search(scope,a," ")).isEmpty();assertThatThrownBy(()->service.search(scope,a,"x".repeat(121))).isInstanceOf(StorageException.class);verifyNoInteractions(directory);}
 @Test void unknownCustomerAndReadOnlyStaffCannotReadIdentity(){var a=actor("wine_storage.view","wine_storage.deliver");when(directory.active(scope.tenantScope(),customer)).thenReturn(Optional.empty());assertThatThrownBy(()->service.get(scope,a,customer)).isInstanceOf(StorageException.class);assertThatThrownBy(()->service.search(scope,actor("wine_storage.view"),"A")).isInstanceOf(StorageException.class);}
 @Test void scopeRejectionPrecedesAnyDirectoryRead(){var a=actor("wine_storage.view","wine_storage.receive");doThrow(StorageException.forbidden()).when(access).staff(scope,a,"wine_storage.view",false);assertThatThrownBy(()->service.search(scope,a,"A")).isInstanceOf(StorageException.class);verifyNoInteractions(directory);}
}
