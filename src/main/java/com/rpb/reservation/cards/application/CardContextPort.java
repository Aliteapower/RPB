package com.rpb.reservation.cards.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
public interface CardContextPort {
 void staff(StoreScope scope,CurrentActor actor,String permission,boolean newTransaction);
 void customer(StoreScope scope,CustomerAuthPrincipal principal,boolean newTransaction);
 void requireCustomer(StoreScope scope,UUID customerId);
 CardDtos.StoreInfo store(StoreScope scope);
}
