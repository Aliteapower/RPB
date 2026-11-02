package com.rpb.reservation.winestorage;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.catalog.application.StorageProductCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import java.time.Clock;import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageHomeEntryGateTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);
 AppGateService gate=mock(AppGateService.class);SharedStoreIdentityQuery stores=mock(SharedStoreIdentityQuery.class);CustomerDirectoryQuery customers=mock(CustomerDirectoryQuery.class);StorageRepository repo=mock(StorageRepository.class);StorageAccess access=new StorageAccess(gate,stores,customers);
 StorageQueryService service=new StorageQueryService(access,repo,mock(StorageProductCatalog.class),Clock.systemUTC(),stores);
 CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"qa@example.invalid","QA");CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wine_storage.view","wine_storage.deliver"),Set.of(store));
 @BeforeEach void closed(){var identity=mock(PlatformStore.class);when(identity.timezone()).thenReturn("Asia/Singapore");when(stores.active(tenant,store)).thenReturn(Optional.of(identity));when(customers.active(scope.tenantScope(),customer)).thenReturn(Optional.of(mock(CustomerManagementItem.class)));when(repo.settings(scope)).thenReturn(new StorageDtos.Settings("customer_verification",0));when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.deny(i.getArgument(0),tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,"wine_storage.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,"wine_storage.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));}
 @Test void unopenedCustomerWithNoOwnHistoryCannotReadAnEmptyWorkspace(){assertThatThrownBy(()->service.customer(scope,principal)).extracting("status").isEqualTo(403);verify(repo).hasCustomerHistory(scope,customer);verify(repo,never()).settings(any());}
 @Test void ownHistorySurvivesMissingCommercialMetadataWithoutGrantingNewBusiness(){when(repo.hasCustomerHistory(scope,customer)).thenReturn(true);assertThat(service.customer(scope,principal).newBusinessEnabled()).isFalse();assertThatThrownBy(()->access.customer(scope,principal,true)).extracting("status").isEqualTo(403);}
 @Test void provisionedClosedCustomerAndEnabledCustomerCanReadContext(){when(gate.isStoreProductProvisioned("wine_storage",tenant,store)).thenReturn(true);assertThat(service.customer(scope,principal).newBusinessEnabled()).isFalse();when(gate.evaluateCommercial("wine_storage",tenant,store)).thenReturn(AppGateDecision.allow("wine_storage",tenant,store,null));assertThat(service.customer(scope,principal).newBusinessEnabled()).isTrue();}
 @Test void staffWorkspaceRequiresOperationalOrHistoricalGateButControlledRecordAccessRemains(){assertThatThrownBy(()->service.staff(scope,actor)).extracting("status").isEqualTo(403);verify(repo,never()).settings(any());when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("wine_storage",tenant,store,"wine_storage.view"));assertThat(service.staff(scope,actor).newBusinessEnabled()).isFalse();access.staff(scope,actor,"wine_storage.deliver",false);assertThatThrownBy(()->access.staff(scope,actor,"wine_storage.deliver",true)).extracting("status").isEqualTo(403);}
 @Test void foreignCustomerIsRejectedBeforeAnyHistoryProbe(){var foreign=new CustomerAuthPrincipal(UUID.randomUUID(),principal.authAccountId(),customer,"qa@example.invalid","QA");assertThatThrownBy(()->service.customer(scope,foreign)).extracting("status").isEqualTo(403);verifyNoInteractions(repo,gate);}
 @Test void metadataOnlyStaffHomeNeverFetchesEntireStoreHistory(){when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("wine_storage",tenant,store,"wine_storage.view"));var home=service.staff(scope,actor,false);assertThat(home.items()).isEmpty();assertThat(home.applications()).isEmpty();assertThat(home.intents()).isEmpty();assertThat(home.events()).isEmpty();verify(repo,never()).items(any(),any(),any());verify(repo,never()).applications(any(),any());verify(repo,never()).intents(any(),any());verify(repo,never()).events(any(),any());}
}
