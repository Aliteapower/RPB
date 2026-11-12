package com.rpb.reservation.wallet;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.wallet.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletCustomerEntryGateTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"qa@example.invalid","QA");
    final AppGateService gate=mock(AppGateService.class);
    final WalletDirectory directory=mock(WalletDirectory.class);
    final WalletAccountRepository accounts=mock(WalletAccountRepository.class);
    final WalletCollectionPort collections=mock(WalletCollectionPort.class);
    final WalletAccess access=new WalletAccess(gate,directory);
    final WalletAccountService service=new WalletAccountService(accounts,mock(WalletLedgerRepository.class),mock(WalletCommandRepository.class),access,directory,collections);
    @BeforeEach void closed(){when(gate.evaluateCommercial("wallet",tenant,store)).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));}

    @Test void storageOrCustomerRegistrationDoesNotGrantAnUnopenedWalletHome() {
        assertThatThrownBy(()->service.customerContext(scope,principal)).isInstanceOfSatisfying(WalletException.class,e->{assertThat(e.status()).isEqualTo(403);assertThat(e.code()).isEqualTo("WALLET_PRODUCT_UNAVAILABLE");});
        verify(accounts,never()).program(scope);
    }
    @Test void currentlyEnabledWalletRemainsAvailableEvenBeforeItsProgrammeIsConfigured() {
        when(gate.evaluateCommercial("wallet",tenant,store)).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));
        assertThat(service.customerContext(scope,principal).denialReason()).isNull();
    }
    @Test void previouslyProvisionedWalletKeepsReadOnlyContextWhenClosedOrExpired() {
        when(gate.isStoreProductProvisioned("wallet",tenant,store)).thenReturn(true);
        assertThat(service.customerContext(scope,principal).transactionsEnabled()).isFalse();
        assertThat(service.customerContext(scope,principal).denialReason()).isEqualTo("TENANT_APP_NOT_ENABLED");
    }
    @Test void existingOwnAccountSurvivesMissingHistoricalEntitlementMetadata() {
        when(accounts.customerAccounts(scope,customer)).thenReturn(List.of(mock(WalletViews.Account.class)));
        assertThat(service.customerContext(scope,principal).transactionsEnabled()).isFalse();
        verify(accounts).customerAccounts(scope,customer);
    }
    @Test void foreignTenantIsRejectedBeforeReadingAccountsOrCommercialState() {
        var foreign=new CustomerAuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),customer,"qa@example.invalid","QA");
        assertThatThrownBy(()->service.customerContext(scope,foreign)).isInstanceOf(WalletException.class);
        verifyNoInteractions(accounts);verify(gate,never()).isStoreProductProvisioned(anyString(),any(),any());
    }
}
