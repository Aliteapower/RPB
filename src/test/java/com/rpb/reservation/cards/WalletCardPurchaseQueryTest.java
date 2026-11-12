package com.rpb.reservation.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cards.application.CardDtos.Purchase;
import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.cards.purchase.PurchaseService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletCardPurchaseQuery;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletCardPurchaseQueryTest {
 final StoreScope scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());
 final CurrentActor actor=mock(CurrentActor.class);
 final PurchaseService purchases=mock(PurchaseService.class);
 final CustomerDirectoryQuery customers=mock(CustomerDirectoryQuery.class);
 final WalletCardPurchaseQuery query=new WalletCardPurchaseQuery(purchases,customers);
 Purchase order(UUID id,UUID customer){return new Purchase(id,customer,UUID.randomUUID(),null,20000,"issued",java.time.Instant.now(),null);}

 @Test void enrichesOnlyRequestedPageInOneTenantScopedBatchAndKeepsFlatContract()throws Exception {
  UUID customer=UUID.randomUUID(),missing=UUID.randomUUID(),before=UUID.randomUUID();
  var first=order(UUID.randomUUID(),customer);var second=order(UUID.randomUUID(),customer);var third=order(UUID.randomUUID(),missing);
  when(purchases.staff(scope,actor,before)).thenReturn(List.of(first,second,third));
  when(purchases.refundable(scope,actor,Set.of(first.id(),second.id(),third.id()))).thenReturn(Set.of(first.id()));
  when(customers.activeByIds(scope.tenantScope(),Set.of(customer,missing))).thenReturn(List.of(new CustomerManagementItem(customer,"C1","陈小明",null,"+6591234567","private@example.invalid","active",null,null)));
  var rows=query.staff(scope,actor,before);assertThat(rows).hasSize(3);
  var json=new ObjectMapper().findAndRegisterModules().valueToTree(rows);
  assertThat(json.get(0).path("id").asText()).isEqualTo(first.id().toString());
  assertThat(json.get(0).path("customerName").asText()).isEqualTo("陈小明");
  assertThat(json.get(0).path("customerPhone").asText()).isEqualTo("+6591234567");
  assertThat(json.get(0).path("amountCents").asLong()).isEqualTo(20000);
  assertThat(json.get(0).path("refundable").asBoolean()).isTrue();assertThat(json.get(1).path("refundable").asBoolean()).isFalse();
  assertThat(json.get(0).has("order")).isFalse();assertThat(json.toString()).doesNotContain("private@example.invalid");
  assertThat(json.get(2).path("customerName").isNull()).isTrue();
  verify(customers,times(1)).activeByIds(scope.tenantScope(),Set.of(customer,missing));verifyNoMoreInteractions(customers);
 }
 @Test void deniedOrderReadNeverQueriesCustomerProfiles(){when(purchases.staff(scope,actor,null)).thenThrow(CardException.forbidden());assertThatThrownBy(()->query.staff(scope,actor,null)).isInstanceOf(CardException.class);verifyNoInteractions(customers);}
 @Test void emptyPageDoesNotQueryDirectory(){when(purchases.staff(scope,actor,null)).thenReturn(List.of());assertThat(query.staff(scope,actor,null)).isEmpty();verifyNoInteractions(customers);}
}
