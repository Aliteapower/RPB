package com.rpb.reservation.cards;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.wallet.integration.WalletCardPaymentAdapter;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.WalletAccountRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CardPaymentAdapterTest {
 @Test void onlyConfiguredChannelsCanReceiveNewCardPaymentsButOriginalRecoveryRemainsAvailable(){var port=mock(WalletCollectionPort.class);var accounts=mock(WalletAccountRepository.class);var program=mock(WalletViews.Program.class);var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());String alias="cash:"+UUID.randomUUID();when(accounts.requireProgram(scope,true)).thenReturn(program);when(program.channels()).thenReturn(List.of(alias));when(port.canonicalAccount(scope,alias,"","T1")).thenReturn("cash:store:T1");when(port.canonicalRecoveryAccount(scope,"cash","","T1")).thenReturn("cash:store:T1");var adapter=new WalletCardPaymentAdapter(port,accounts);
  assertThatThrownBy(()->adapter.confirm(scope,new PaymentConfirm("cash","","T1","R1",20000,true,"a"))).isInstanceOf(CardException.class);
  assertThat(adapter.confirm(scope,new PaymentConfirm(alias,"","T1","R2",20000,true,"b")).channel()).isEqualTo(alias);
  assertThat(adapter.recovery(scope,new PaymentConfirm("cash","","T1","R3",20000,true,"c")).amountCents()).isEqualTo(20000);
 }
}
