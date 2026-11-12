package com.rpb.reservation.wallet;
import com.rpb.reservation.wallet.collectionmethods.CollectionMethods;
import com.rpb.reservation.wallet.domain.WalletMoney;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollectionMethodsTest {
 @Test void customCodeKeepsSettlementType(){String code="cash:"+UUID.randomUUID();assertEquals(code,WalletMoney.channel(code));assertEquals("cash",WalletMoney.channelType(code));assertThrows(WalletException.class,()->WalletMoney.channel("cash:forged"));}
 @Test void aliasesRetainRealCollectionVerification(){var paynow=org.mockito.Mockito.mock(com.rpb.reservation.payment.application.OfflinePayNowCollectionService.class);var adapter=new com.rpb.reservation.wallet.integration.WalletOfflineCollectionAdapter(paynow,org.mockito.Mockito.mock(com.rpb.reservation.payment.application.OfflineReceiptRegistry.class));var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(UUID.randomUUID()),UUID.randomUUID());assertThrows(WalletException.class,()->adapter.canonicalAccount(scope,"paynow:"+UUID.randomUUID(),"ANY","T1"));assertTrue(adapter.canonicalAccount(scope,"cash:"+UUID.randomUUID(),"","T1").startsWith("cash:"));assertThrows(WalletException.class,()->adapter.canonicalAccount(scope,"other:"+UUID.randomUUID(),"","T1"));assertEquals("offline:BANK:T1",adapter.canonicalAccount(scope,"other:"+UUID.randomUUID(),"BANK","T1"));}
 @Test void namesAndOrderAreAdminData(){var result=CollectionMethods.normalize(List.of(new CollectionMethods.Method("cash","柜台现金","cash"),new CollectionMethods.Method(null,"银行转账","other")),List.of("cash"),Map.of());assertEquals("柜台现金",result.getFirst().name());assertTrue(result.getLast().code().startsWith("other:"));}
 @Test void cannotForgeOrChangeExistingType(){assertThrows(WalletException.class,()->CollectionMethods.normalize(List.of(new CollectionMethods.Method("cash","转账","other")),List.of("cash"),Map.of()));assertThrows(WalletException.class,()->CollectionMethods.normalize(List.of(new CollectionMethods.Method("cash:"+UUID.randomUUID(),"现金","cash")),List.of("cash"),Map.of()));}
 @Test void rejectEmptyDuplicateAndUnboundedNames(){for(var methods:List.of(List.<CollectionMethods.Method>of(),List.of(new CollectionMethods.Method(null,"A","other"),new CollectionMethods.Method(null," a ","cash")),List.of(new CollectionMethods.Method(null,"x".repeat(61),"other"))))assertThrows(WalletException.class,()->CollectionMethods.normalize(methods,List.of("cash"),Map.of()));}
}
