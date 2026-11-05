package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.appgate.domain.StoreAppSubscription;
import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductHistoryAccessTest {
 @ParameterizedTest @ValueSource(strings={"reservation_queue","payment","wallet","wine_storage","daily_ledger"})
 void lifecycleNeverExposesUnopenedProductsAndSeparatesHistoricalReadsFromOperations(String key){
  var tenant=UUID.randomUUID();var store=UUID.randomUUID();var user=UUID.randomUUID();var now=OffsetDateTime.now();
  var apps=mock(PlatformAppJpaRepository.class);var grants=mock(TenantAppEntitlementJpaRepository.class);var settings=mock(StoreAppSettingJpaRepository.class);var billing=mock(StoreAppSubscriptionRepository.class);
  var gate=new AppGateService(apps,grants,settings,billing,Clock.systemUTC());
  var entry=ProductLineEntryRegistry.entries().stream().filter(e->e.appKey().equals(key)).findFirst().orElseThrow();
  String read=entry.historyPermissions().iterator().next();
  var actor=CurrentActor.storeStaff(tenant,user,"staff",Set.of("store_staff"),Set.of(read,"example.write"),Set.of(store));
  var request=new AppGateAccessRequest(key,tenant,store,read,actor);
  var platform=PlatformAppEntity.of(UUID.randomUUID(),key,key,"active",entry.value(),null,10,"{}");
  when(apps.findByAppKey(key)).thenReturn(Optional.of(platform));when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of(platform));
  assertThat(gate.historyApps(actor,store)).isEmpty();assertThat(gate.evaluateHistory(request).allowed()).isFalse();
  var grant=TenantAppEntitlementEntity.of(UUID.randomUUID(),tenant,key,"enabled",now.minusDays(1),null,"{}",user,now);
  var setting=StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,key,false,false,"{}",null,null,null);
  when(grants.findByTenantIdAndAppKey(tenant,key)).thenReturn(Optional.of(grant));when(grants.findAllByTenantId(tenant)).thenReturn(List.of(grant));
  when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,key)).thenReturn(Optional.of(setting));when(settings.findAllByTenantIdAndStoreId(tenant,store)).thenReturn(List.of(setting));
  when(billing.findByTenantAndStore(tenant,store)).thenReturn(Map.of(key,new StoreAppSubscription("active",null,null)));
  assertThat(gate.historyApps(actor,store)).isEmpty();assertThat(gate.evaluateHistory(request).allowed()).isFalse();
  when(billing.findByTenantAndStore(tenant,store)).thenReturn(Map.of(key,new StoreAppSubscription("active","active",now.plusMonths(1))));
  assertThat(gate.evaluateHistory(request).allowed()).isFalse(); // Purchased but never activated at this store.
  setting.enable(user,now);
  assertThat(gate.evaluate(request).allowed()).isTrue();assertThat(gate.visibleApps(actor,store)).hasSize(1);assertThat(gate.historyApps(actor,store)).isEmpty();
  setting.disable(now);
  assertThat(gate.visibleApps(actor,store)).isEmpty();assertThat(gate.evaluate(request).allowed()).isFalse();assertThat(gate.evaluateHistory(request).allowed()).isTrue();
  var history=gate.historyApps(actor,store).getFirst();
  if (key.equals("wallet") && read.equals("wallet.points.view")) assertThat(history.entryRoute()).contains("category=points");
  if (key.equals("reservation_queue") && read.equals("queue.view")) assertThat(history.entryRoute()).contains("queue-tickets");
  assertThat(gate.historyApps(actor,store)).singleElement().satisfies(e->{assertThat(e.status()).isEqualTo("history");assertThat(e.entryRoute()).contains("history=1");assertThat(e.permissions()).containsExactly(read);});
  assertThat(gate.evaluateHistory(new AppGateAccessRequest(key,tenant,store,"example.write",actor)).allowed()).isFalse();
  assertThat(gate.evaluateHistory(new AppGateAccessRequest(key,UUID.randomUUID(),store,read,actor)).allowed()).isFalse();
  assertThat(gate.evaluateHistory(new AppGateAccessRequest(key,tenant,UUID.randomUUID(),read,actor)).allowed()).isFalse();
  var noPermission=CurrentActor.storeStaff(tenant,user,"staff",Set.of("store_staff"),Set.of(),Set.of(store));
  assertThat(gate.historyApps(noPermission,store)).isEmpty();assertThat(gate.evaluateHistory(new AppGateAccessRequest(key,tenant,store,read,noPermission)).allowed()).isFalse();
  grant.updateStatus("expired",user,now);
  when(billing.findByTenantAndStore(tenant,store)).thenReturn(Map.of(key,new StoreAppSubscription("expired","expired",now.minusDays(1))));
  assertThat(gate.evaluate(request).allowed()).isFalse();assertThat(gate.evaluateHistory(request).allowed()).isTrue();assertThat(gate.historyApps(actor,store)).hasSize(1);
  when(apps.findByAppKey(key)).thenReturn(Optional.empty());when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of());
  assertThat(gate.evaluateHistory(request).allowed()).isFalse();assertThat(gate.historyApps(actor,store)).isEmpty();
 }
}
