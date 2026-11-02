package com.rpb.reservation.wallet;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class WalletStaffEntryGateTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wallet.view","wallet.refund.create"),Set.of(store));
 AppGateService gate=mock(AppGateService.class);WalletDirectory directory=mock(WalletDirectory.class);WalletAccountRepository accounts=mock(WalletAccountRepository.class);WalletAccess access=new WalletAccess(gate,directory);WalletAccountService service=new WalletAccountService(accounts,mock(WalletLedgerRepository.class),mock(WalletCommandRepository.class),access,directory,mock(WalletCollectionPort.class));
 @BeforeEach void closed(){when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,"wallet.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,"wallet.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));when(gate.evaluateCommercial("wallet",tenant,store)).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));}
 @Test void unopenedProductCannotExposeStaffWorkspace(){assertThatThrownBy(()->service.context(scope,actor)).extracting("status").isEqualTo(403);verifyNoInteractions(accounts);}
 @Test void historicalStaffScopeKeepsClosedReadOnlyContext(){when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,"wallet.view"));assertThat(service.context(scope,actor).transactionsEnabled()).isFalse();verify(gate).evaluateHistory(argThat(r->r.requiredPermission().equals("wallet.view")));}
 @Test void activeProductEntryAutomaticallyRecovers(){when(gate.evaluate(any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,"wallet.view"));when(gate.evaluateCommercial("wallet",tenant,store)).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));assertThat(service.context(scope,actor).denialReason()).isNull();verify(gate,never()).evaluateHistory(any());}
 @Test void entryCheckDoesNotChangeGenericRecordOrControlledOriginalOperationAccess(){access.staff(scope,actor,"wallet.refund.create",false);verifyNoInteractions(gate);assertThatThrownBy(()->access.staff(scope,actor,"wallet.refund.create",true)).extracting("status").isEqualTo(403);}
 @Test void missingStoreMembershipStillPrecedesEntryGate(){var other=new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of());assertThatThrownBy(()->service.context(scope,other)).extracting("status").isEqualTo(403);verifyNoInteractions(gate,accounts);}
}
