package com.rpb.reservation.wallet;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.WalletCollectionRepository;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

class WalletCashTopupReceiptTest {
    final StoreScope scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());
    final WalletCollectionRepository repository=mock(WalletCollectionRepository.class);
    final WalletCollectionPort port=mock(WalletCollectionPort.class);
    final WalletCollectionService service=new WalletCollectionService(repository,port);
    final UUID topup=UUID.randomUUID(),actor=UUID.randomUUID();
    CollectionConfirm command(String channel,String receipt) {return new CollectionConfirm(channel,"10","account",receipt,"T1","",true,"retry");}

    @Test void blankCashTopupReceiptGetsStableInternalReference() {
        when(port.canonicalAccount(scope,"cash","account","T1")).thenReturn("cash-account");
        for(String receipt:Arrays.asList(null,"","  "))
            service.confirm(scope,topup,null,1000,command("cash",receipt),actor,List.of("cash"));
        verify(repository,times(3)).confirm(scope,topup,null,"cash",1000,"cash-account","CASH-TOPUP-"+topup,"T1","",actor,false);
    }
    @Test void enteredCashReferenceIsPreservedAndNormalized() {
        when(port.canonicalAccount(scope,"cash","account","T1")).thenReturn("cash-account");
        service.confirm(scope,topup,null,1000,command("cash"," paper-123 "),actor,List.of("cash"));
        verify(repository).confirm(scope,topup,null,"cash",1000,"cash-account","PAPER-123","T1","",actor,false);
    }
    @Test void otherChannelsStillRequireExternalReceipt() {
        for(String channel:List.of("paynow","credit_card"))
            assertThatThrownBy(()->service.confirm(scope,topup,null,1000,command(channel,null),actor,List.of(channel)))
                .isInstanceOf(WalletException.class);
        verifyNoInteractions(repository);
    }
    @Test void cashBillAndExceptionStillRequireReceipt() {
        assertThatThrownBy(()->service.confirm(scope,null,UUID.randomUUID(),1000,command("cash",null),actor,List.of("cash")))
            .isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.confirm(scope,topup,null,1000,command("cash",null),actor,List.of("cash"),true))
            .isInstanceOf(WalletException.class);
        verifyNoInteractions(repository);
    }
}
