package com.rpb.reservation.cards.command;
import com.rpb.reservation.cards.application.CardAccess;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CardCommandService {
 private final CardAccess access;private final CardCommands commands;
 public CardCommandService(CardAccess access,CardCommands commands){this.access=access;this.commands=commands;}
 public Object staffResult(StoreScope s,CurrentActor a,String op,String key){access.staff(s,a,"wallet.cards.view",false);return commands.result(s,a.actorId(),op,key);}
 @Transactional public Object staffReconcile(StoreScope s,CurrentActor a,String op,String key){access.staff(s,a,"wallet.cards.view",false);return commands.reconcile(s,a.actorId(),op,key);}
 public Object customerResult(StoreScope s,CustomerAuthPrincipal p,String op,String key){access.customer(s,p,false);return commands.result(s,p.customerId(),op,key);}
 @Transactional public Object customerReconcile(StoreScope s,CustomerAuthPrincipal p,String op,String key){access.customer(s,p,false);return commands.reconcile(s,p.customerId(),op,key);}
}
