package com.rpb.reservation.wallet.cards;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.plan.CardPlanService;
import com.rpb.reservation.cards.entitlement.CardHoldingService;
import com.rpb.reservation.cards.redemption.RedemptionService;
import com.rpb.reservation.cards.purchase.PurchaseService;
import com.rpb.reservation.cards.command.CardCommandService;
import com.rpb.reservation.common.scope.StoreScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wallet/cards")
public class WalletCardsCustomerController {
 private final WalletApiIdentity identity;private final CardPlanService plans;private final CardHoldingService cards;private final RedemptionService redeem;private final PurchaseService purchases;private final CardAccess access;private final CardCommandService commands;
 public WalletCardsCustomerController(WalletApiIdentity identity,CardPlanService plans,CardHoldingService cards,RedemptionService redeem,PurchaseService purchases,CardAccess access,CardCommandService commands){this.identity=identity;this.plans=plans;this.cards=cards;this.redeem=redeem;this.purchases=purchases;this.access=access;this.commands=commands;}
 private StoreScope scope(UUID store,HttpServletRequest r){return identity.scope(identity.customer(r).tenantId(),store);}
 @GetMapping("/plans")public List<Plan> plans(@PathVariable UUID storeId,HttpServletRequest r){return plans.own(scope(storeId,r),identity.customer(r));}
 @GetMapping("/holdings")public List<Holding> holdings(@PathVariable UUID storeId,HttpServletRequest r,@RequestParam(required=false)UUID before){return cards.own(scope(storeId,r),identity.customer(r),before);}
 @GetMapping("/entries")public List<Entry> entries(@PathVariable UUID storeId,HttpServletRequest r,@RequestParam(required=false)UUID before){return cards.ownEntries(scope(storeId,r),identity.customer(r),before);}
 @PostMapping("/enroll")public Holding enroll(@PathVariable UUID storeId,HttpServletRequest r,@RequestBody Enroll c){return cards.enroll(scope(storeId,r),identity.customer(r),c);}
 @GetMapping("/purchases")public List<Purchase> purchases(@PathVariable UUID storeId,HttpServletRequest r,@RequestParam(required=false)UUID before){return purchases.own(scope(storeId,r),identity.customer(r),before);}
 @PostMapping("/purchases")public Purchase buy(@PathVariable UUID storeId,HttpServletRequest r,@RequestBody PurchaseCreate c){return purchases.create(scope(storeId,r),identity.customer(r),c);}
 @PostMapping("/scan")public Request scan(@PathVariable UUID storeId,HttpServletRequest r,@RequestBody Scan c){return redeem.scan(scope(storeId,r),identity.customer(r),c.code());}
 @GetMapping("/requests/{id}")public Request request(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest r){return redeem.own(scope(storeId,r),identity.customer(r),id);}
 @PostMapping("/requests/{id}/confirm")public Request confirm(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest r,@RequestBody Confirm c){return redeem.confirm(scope(storeId,r),identity.customer(r),id,c);}
 @PostMapping("/requests/{id}/authorize")public Code authorize(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest r,@RequestBody Confirm c){return redeem.authorize(scope(storeId,r),identity.customer(r),id,c);}
 @GetMapping("/commands/{operation}/{key}")public Object result(@PathVariable UUID storeId,@PathVariable String operation,@PathVariable String key,HttpServletRequest r){var s=scope(storeId,r);var p=identity.customer(r);access.customer(s,p,false);return commands.customerResult(s,p,operation,key);}
 @PostMapping("/commands/{operation}/{key}/reconcile") public Object reconcile(@PathVariable UUID storeId,@PathVariable String operation,@PathVariable String key,HttpServletRequest r){var s=scope(storeId,r);var p=identity.customer(r);access.customer(s,p,false);return commands.customerReconcile(s,p,operation,key);}
}
