package com.rpb.reservation.wallet.cards;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.plan.CardPlanService;
import com.rpb.reservation.cards.entitlement.CardHoldingService;
import com.rpb.reservation.cards.stamp.StampService;
import com.rpb.reservation.cards.redemption.RedemptionService;
import com.rpb.reservation.cards.purchase.PurchaseService;
import com.rpb.reservation.cards.command.CardCommandService;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/cards")
public class WalletCardsStaffController {
 private final WalletApiIdentity identity;private final CardPlanService plans;private final CardHoldingService cards;private final StampService stamps;private final RedemptionService redeem;private final PurchaseService purchases;private final CardAccess access;private final CardCommandService commands;private final WalletDirectory directory;private final WalletCustomerIdentityService codes;
 public WalletCardsStaffController(WalletApiIdentity identity,CardPlanService plans,CardHoldingService cards,StampService stamps,RedemptionService redeem,PurchaseService purchases,CardAccess access,CardCommandService commands,WalletDirectory directory,WalletCustomerIdentityService codes){this.identity=identity;this.plans=plans;this.cards=cards;this.stamps=stamps;this.redeem=redeem;this.purchases=purchases;this.access=access;this.commands=commands;this.directory=directory;this.codes=codes;}
 private StoreScope scope(UUID store){return identity.scope(identity.staff().tenantId(),store);}
 @GetMapping("/settings")public Settings settings(@PathVariable UUID storeId){return access.settings(scope(storeId),identity.staff());}
 @PutMapping("/settings")public Settings settings(@PathVariable UUID storeId,@RequestBody Settings c){return access.settings(scope(storeId),identity.staff(),c.enabled());}
 @GetMapping("/plans")public List<Plan> plans(@PathVariable UUID storeId){return plans.staff(scope(storeId),identity.staff());}
 @PostMapping("/plans")public Plan create(@PathVariable UUID storeId,@RequestBody PlanCreate c){return plans.create(scope(storeId),identity.staff(),c);}
 @PostMapping("/plans/{id}/status")public Plan status(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody PlanStatus c){return plans.status(scope(storeId),identity.staff(),id,c);}
 @GetMapping("/holdings")public List<Holding> holdings(@PathVariable UUID storeId,@RequestParam(required=false)UUID customerId,@RequestParam(required=false)UUID before){return cards.staff(scope(storeId),identity.staff(),customerId,before);}
 @GetMapping("/entries")public List<Entry> entries(@PathVariable UUID storeId,@RequestParam(required=false)UUID customerId,@RequestParam(required=false)UUID before){return cards.entries(scope(storeId),identity.staff(),customerId,before);}
 @GetMapping("/customers")public List<WalletViews.Customer> customers(@PathVariable UUID storeId,@RequestParam String query){var s=scope(storeId);access.staff(s,identity.staff(),"wallet.cards.view",false);return directory.search(s.tenantScope(),query);}
 @PostMapping("/identity")public WalletViews.Customer identity(@PathVariable UUID storeId,@RequestBody Scan c){return codes.resolveForCards(scope(storeId),identity.staff(),c.code());}
 @PostMapping("/enroll")public Holding enroll(@PathVariable UUID storeId,@RequestBody StaffEnroll c){return cards.staffEnroll(scope(storeId),identity.staff(),c.customerId(),new Enroll(c.planId(),c.idempotencyKey()));}
 public record StaffEnroll(UUID customerId,UUID planId,String idempotencyKey){}
 @PostMapping("/stamps")public Holding stamp(@PathVariable UUID storeId,@RequestBody StampCommand c){return stamps.grant(scope(storeId),identity.staff(),c.cardId(),c.source(),c.idempotencyKey());}
 @PostMapping("/entries/{id}/reverse")public Holding reverse(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Reverse c){return redeem.reverse(scope(storeId),identity.staff(),id,c);}
 @PostMapping("/entries/{id}/reverse-stamp")public Holding reverseStamp(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Reverse c){return stamps.reverse(scope(storeId),identity.staff(),id,c);}
 @PostMapping("/requests")public Request request(@PathVariable UUID storeId,@RequestBody RequestCreate c){return redeem.create(scope(storeId),identity.staff(),c);}
 @GetMapping("/requests/{id}")public Request request(@PathVariable UUID storeId,@PathVariable UUID id){return redeem.get(scope(storeId),identity.staff(),id);}
 @PostMapping("/requests/{id}/code")public Code issue(@PathVariable UUID storeId,@PathVariable UUID id){return redeem.issue(scope(storeId),identity.staff(),id);}
 @PostMapping("/requests/{id}/cancel")public Request cancel(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c){return redeem.cancel(scope(storeId),identity.staff(),id,c);}
 @PostMapping("/authorized-redeem")public Request authorized(@PathVariable UUID storeId,@RequestBody Authorized c){return redeem.authorized(scope(storeId),identity.staff(),c);}
 @GetMapping("/purchases")public List<Purchase> purchases(@PathVariable UUID storeId,@RequestParam(required=false)UUID before){return purchases.staff(scope(storeId),identity.staff(),before);}
 @PostMapping("/purchases/{id}/confirm")public Purchase pay(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody PaymentConfirm c){return purchases.confirm(scope(storeId),identity.staff(),id,c);}
 @PostMapping("/purchases/{id}/exception")public Purchase exception(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ExceptionPayment c){return purchases.exception(scope(storeId),identity.staff(),id,c);}
 @PostMapping("/purchases/{id}/refund")public Purchase refund(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Refund c){return purchases.refund(scope(storeId),identity.staff(),id,c);}
 @GetMapping("/commands/{operation}/{key}")public Object result(@PathVariable UUID storeId,@PathVariable String operation,@PathVariable String key){var s=scope(storeId);access.staff(s,identity.staff(),"wallet.cards.view",false);return commands.staffResult(s,identity.staff(),operation,key);}
 @PostMapping("/commands/{operation}/{key}/reconcile") public Object reconcile(@PathVariable UUID storeId,@PathVariable String operation,@PathVariable String key){var s=scope(storeId);access.staff(s,identity.staff(),"wallet.cards.view",false);return commands.staffReconcile(s,identity.staff(),operation,key);}
}
