package com.rpb.reservation.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cards.application.CardDtos.Entry;
import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.cards.entitlement.CardHoldingService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletCardEntryQuery;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletCardEntryQueryTest {
 final StoreScope scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());
 final CurrentActor actor=mock(CurrentActor.class);
 final CardHoldingService cards=mock(CardHoldingService.class);
 final CustomerDirectoryQuery customers=mock(CustomerDirectoryQuery.class);
 final WalletCardEntryQuery query=new WalletCardEntryQuery(cards,customers);
 Entry entry(UUID customer,String kind){return new Entry(UUID.randomUUID(),UUID.randomUUID(),customer,kind,1,30,29,java.time.LocalDate.of(2026,10,28),0,1,"receipt",null,"",java.time.Instant.now(),null);}
 @Test void preservesScopedFiltersAndFlatHistoryWhileBatchingOnlyPageCustomers()throws Exception{
  UUID customer=UUID.randomUUID(),missing=UUID.randomUUID(),filter=UUID.randomUUID(),before=UUID.randomUUID();var first=entry(customer,"redeem");var expired=entry(customer,"daily_expire");var third=entry(missing,"reverse");
  when(cards.entries(scope,actor,filter,before)).thenReturn(List.of(first,expired,third));
  when(customers.activeByIds(scope.tenantScope(),Set.of(customer,missing))).thenReturn(List.of(new CustomerManagementItem(customer,"C1","陈小明",null,"+6591234567","private@example.invalid","active",null,null)));
  var rows=query.staff(scope,actor,filter,before);var json=new ObjectMapper().findAndRegisterModules().valueToTree(rows);
  assertThat(json.size()).isEqualTo(3);assertThat(json.get(0).path("id").asText()).isEqualTo(first.id().toString());assertThat(json.get(0).path("customerName").asText()).isEqualTo("陈小明");assertThat(json.get(0).path("customerPhone").asText()).isEqualTo("+6591234567");assertThat(json.get(1).path("kind").asText()).isEqualTo("daily_expire");assertThat(json.get(1).path("afterTotal").asInt()).isEqualTo(29);assertThat(json.get(2).path("customerName").isNull()).isTrue();assertThat(json.toString()).doesNotContain("private@example.invalid");assertThat(json.get(0).has("entry")).isFalse();
  var order=inOrder(cards,customers);order.verify(cards).entries(scope,actor,filter,before);order.verify(customers).activeByIds(scope.tenantScope(),Set.of(customer,missing));order.verifyNoMoreInteractions();
 }
 @Test void permissionFailureNeverReadsCustomerProfiles(){when(cards.entries(scope,actor,null,null)).thenThrow(CardException.forbidden());assertThatThrownBy(()->query.staff(scope,actor,null,null)).isInstanceOf(CardException.class);verifyNoInteractions(customers);}
 @Test void emptyPageNeverReadsCustomerProfiles(){when(cards.entries(scope,actor,null,null)).thenReturn(List.of());assertThat(query.staff(scope,actor,null,null)).isEmpty();verifyNoInteractions(customers);}
}
