package com.rpb.reservation.wallet;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.appgate.application.AppGateService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class WalletPointsCustomerSearchTest {
 @Test void pointsViewCanSelectSharedCustomerWithoutAccessToMoneyAccountsOrOpeningWallet(){
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
  var gate=mock(AppGateService.class);var directory=mock(WalletDirectory.class);var accounts=mock(WalletAccountRepository.class);
  var service=new WalletAccountService(accounts,mock(WalletLedgerRepository.class),mock(WalletCommandRepository.class),new WalletAccess(gate,directory),directory,mock(WalletCollectionPort.class));
  var actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wallet.points.view"),Set.of(store));
  var matches=List.of(new WalletViews.Customer(customer,"C","顾客",null,"+6591234567"));when(directory.search(scope.tenantScope(),"9123")).thenReturn(matches);
  assertThat(service.search(scope,actor,"9123")).isEqualTo(matches);
  assertThatThrownBy(()->service.get(scope,actor,UUID.randomUUID())).isInstanceOf(WalletException.class);
  assertThatThrownBy(()->service.forCustomer(scope,actor,customer)).isInstanceOf(WalletException.class);
  assertThatThrownBy(()->service.open(scope,actor,customer,"key")).isInstanceOf(WalletException.class);
  verifyNoInteractions(accounts,gate);
 }
 @Test void customerSelectionStillRequiresExplicitPermissionTenantAndStoreScope(){
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
  var directory=mock(WalletDirectory.class);var service=new WalletAccountService(mock(WalletAccountRepository.class),mock(WalletLedgerRepository.class),mock(WalletCommandRepository.class),new WalletAccess(mock(AppGateService.class),directory),directory,mock(WalletCollectionPort.class));
  for(var actor:List.of(new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store)),new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store)),new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.points.view"),Set.of())))
   assertThatThrownBy(()->service.search(scope,actor,"query")).isInstanceOf(WalletException.class);
  verifyNoInteractions(directory);
 }
}
