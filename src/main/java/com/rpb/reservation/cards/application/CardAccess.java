package com.rpb.reservation.cards.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class CardAccess {
 private final CardContextPort context;private final CardSettingsRepository settings;
 public CardAccess(CardContextPort context,CardSettingsRepository settings){this.context=context;this.settings=settings;}
 public void staff(StoreScope s,CurrentActor a,String permission,boolean mutate){context.staff(s,a,permission,mutate);if(mutate)enabled(s);}
 public void customer(StoreScope s,CustomerAuthPrincipal p,boolean mutate){context.customer(s,p,mutate);if(mutate)enabled(s);}
 public void requireCustomer(StoreScope s,UUID customer){context.requireCustomer(s,customer);}
 public CardDtos.StoreInfo store(StoreScope s){return context.store(s);}
 public CardDtos.Settings settings(StoreScope s,CurrentActor a){context.staff(s,a,"wallet.cards.view",false);return new CardDtos.Settings(settings.enabled(s));}
 @Transactional public CardDtos.Settings settings(StoreScope s,CurrentActor a,boolean enabled){context.staff(s,a,"wallet.cards.manage",false);settings.set(s,enabled);return new CardDtos.Settings(enabled);}
 private void enabled(StoreScope s){if(!settings.enabled(s))throw new CardException("CARD_DISABLED",409,"卡功能未启用");}
}
