package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.domain.WalletMoney;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WalletMoneyTest {
    @Test void usesExactCentsAndRejectsFractionalOrNonPositiveMoney() {
        assertThat(WalletMoney.cents("0.01")).isEqualTo(1);
        assertThat(WalletMoney.cents("80.00")).isEqualTo(8000);
        for (String invalid : new String[]{"0", "-1", "0.001", "NaN", "1000000000000", ""}) {
            assertThatThrownBy(() -> WalletMoney.cents(invalid)).isInstanceOf(WalletException.class);
        }
    }
    @Test void partialWalletPaymentDoesNotInventMoneyOrNegativeBalances() {
        assertThat(WalletMoney.debit(5000,8000)).isEqualTo(5000);
        assertThat(WalletMoney.debit(0,8000)).isZero();
        assertThat(WalletMoney.debit(5000,5000)).isEqualTo(5000);
        assertThat(WalletMoney.status(5000,5000,0)).isEqualTo("paid");
        assertThat(WalletMoney.status(8000,5000,0)).isEqualTo("partially_paid");
        assertThat(WalletMoney.status(8000,5000,3000)).isEqualTo("paid");
        assertThatThrownBy(() -> WalletMoney.status(8000,5000,4000)).isInstanceOf(WalletException.class);
    }
    @Test void refundAllocationPreservesEveryCentAndHasStableTieBreaking() {
        UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3);
        assertThat(WalletMoney.allocate(2,Map.of(a,1L,b,1L,c,1L)))
            .containsEntry(a,1L).containsEntry(b,1L).containsEntry(c,0L);
        assertThat(WalletMoney.allocate(3000,Map.of(a,5000L,b,3000L)))
            .containsEntry(a,1875L).containsEntry(b,1125L);
    }
}
