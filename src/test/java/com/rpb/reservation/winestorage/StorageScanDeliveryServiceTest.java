package com.rpb.reservation.winestorage;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageScanDeliveryServiceTest {
 @Test void deniedActorCannotResolveOrRedeemACode(){
  var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var actor=new CurrentActor(scope.tenantId().value(),UUID.randomUUID(),"staff",Set.of("staff"),Set.of(),Set.of(scope.storeId().value()));
  var access=mock(StorageAccess.class);var r=mock(StorageRepository.class);var commands=mock(StorageCommands.class);var withdrawals=mock(StorageWithdrawalService.class);var service=new StorageScanDeliveryService(access,r,commands,withdrawals);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.deliver",false);
  assertThatThrownBy(()->service.deliver(scope,actor,new StorageDtos.ScanDelivery("WS."+"A".repeat(43),"key"))).isInstanceOf(StorageException.class);verifyNoInteractions(r,commands,withdrawals);
 }
}
