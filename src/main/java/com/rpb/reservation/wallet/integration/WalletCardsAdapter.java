package com.rpb.reservation.wallet.integration;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class WalletCardsAdapter implements CardContextPort {
 private final WalletAccess access;private final WalletDirectory directory;
 public WalletCardsAdapter(WalletAccess access,WalletDirectory directory){this.access=access;this.directory=directory;}
 public void staff(StoreScope s,CurrentActor a,String permission,boolean mutate){access.staff(s,a,permission,mutate);}
 public void customer(StoreScope s,CustomerAuthPrincipal p,boolean mutate){access.customer(s,p,mutate);}
 public void requireCustomer(StoreScope s,UUID customer){directory.customer(s.tenantScope(),customer);}
 public CardDtos.StoreInfo store(StoreScope s){var store=directory.store(s);return new CardDtos.StoreInfo(store.operatingEntityId(),store.timezone());}
}
