package com.rpb.reservation.wallet.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet")
public class WalletStaffController {
    private final WalletApiIdentity identity;private final WalletAccountService accounts;private final WalletTopupService topups;
    private final WalletCheckoutService checkout;private final WalletAuthorizationService authorization;private final WalletRefundService refunds;
    private final WalletReconciliationService reports;private final WalletCollectionPort collection;
    public WalletStaffController(WalletApiIdentity identity,WalletAccountService accounts,WalletTopupService topups,WalletCheckoutService checkout,WalletAuthorizationService authorization,WalletRefundService refunds,WalletReconciliationService reports,WalletCollectionPort collection) {
        this.identity=identity;this.accounts=accounts;this.topups=topups;this.checkout=checkout;this.authorization=authorization;this.refunds=refunds;this.reports=reports;this.collection=collection;
    }
    private StoreScope scope(UUID store,CurrentActor a) {return identity.scope(a.tenantId(),store);}
    @GetMapping("/context") public Context context(@PathVariable UUID storeId) {var a=identity.staff();return accounts.context(scope(storeId,a),a);}
    @PutMapping("/program") public Program configure(@PathVariable UUID storeId,@RequestBody ProgramSettings body) {var a=identity.staff();return accounts.configure(scope(storeId,a),a,body);}
    @GetMapping("/customers") public List<Customer> customers(@PathVariable UUID storeId,@RequestParam(defaultValue="") String query) {var a=identity.staff();return accounts.search(scope(storeId,a),a,query);}
    @GetMapping("/customers/{id}") public Customer customer(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return accounts.customer(scope(storeId,a),a,id);}
    @GetMapping("/customers/{id}/accounts") public List<Account> customerAccounts(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return accounts.forCustomer(scope(storeId,a),a,id);}
    @PostMapping("/payment-codes/resolve") public Map<String,Object> resolveCode(@PathVariable UUID storeId,@RequestBody ResolveCode body) {var a=identity.staff();var s=scope(storeId,a);var account=authorization.resolve(s,a,body.code(),body.purpose());return Map.of("account",account,"customer",accounts.customer(s,a,account.customerId()));}
    @PostMapping("/accounts") public Account open(@PathVariable UUID storeId,@RequestBody AccountRequest body) {var a=identity.staff();return accounts.open(scope(storeId,a),a,body.customerId(),body.idempotencyKey());}
    @GetMapping("/accounts/{id}") public Account account(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return accounts.get(scope(storeId,a),a,id);}
    @GetMapping("/topups/{id}") public Topup topup(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return topups.get(scope(storeId,a),a,id);}
    @PostMapping("/topups") public Topup createTopup(@PathVariable UUID storeId,@RequestBody JsonNode body) {var a=identity.staff();return topups.create(scope(storeId,a),a,topupCreate(body));}
    @PostMapping("/topups/{id}/confirm") public TopupResult confirm(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CollectionConfirm body,HttpServletRequest request) {var a=identity.staff();WalletModelGuard.require(request.getHeader("X-RPB-Wallet-Model"));return topups.confirm(scope(storeId,a),a,id,body);}
    @PostMapping("/topups/{id}/cancel") public Topup cancelTopup(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body) {var a=identity.staff();return topups.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/topups/{id}/paynow") public Map<String,String> topupPaynow(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return Map.of("payload",topups.paynowQr(scope(storeId,a),a,id,collection));}
    @GetMapping("/bills/{id}") public Bill bill(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return checkout.get(scope(storeId,a),a,id);}
    @PostMapping("/bills") public Bill createBill(@PathVariable UUID storeId,@RequestBody BillCreate body) {var a=identity.staff();return checkout.create(scope(storeId,a),a,body);}
    @PostMapping("/bills/{id}/code") public PaymentCode billCode(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return authorization.issueBillCode(scope(storeId,a),a,id);}
    @PostMapping("/bills/{id}/bind-code") public Bill bind(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Code body) {var a=identity.staff();return authorization.bind(scope(storeId,a),a,id,body.code());}
    @PostMapping("/bills/{id}/collections") public Bill supplement(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CollectionConfirm body) {var a=identity.staff();return checkout.collect(scope(storeId,a),a,id,body);}
    @GetMapping("/bills/{id}/paynow") public Map<String,String> billPaynow(@PathVariable UUID storeId,@PathVariable UUID id) {
        var a=identity.staff();return Map.of("payload",checkout.paynowQr(scope(storeId,a),a,id,collection));
    }
    @PostMapping("/bills/{id}/cancel") public Bill cancelBill(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body) {var a=identity.staff();return checkout.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/refunds/{id}") public RefundDetail refund(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return refunds.get(scope(storeId,a),a,id);}
    @PostMapping("/refunds") public RefundDetail createRefund(@PathVariable UUID storeId,@RequestBody RefundCreate body,HttpServletRequest request) {var a=identity.staff();WalletModelGuard.require(request.getHeader("X-RPB-Wallet-Model"));if(body==null||body.expectedAccountVersion()==null)throw WalletException.invalid();return refunds.create(scope(storeId,a),a,body);}
    @PostMapping("/refunds/{id}/confirm") public RefundDetail confirmRefund(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody RefundConfirmation body,HttpServletRequest request) {var a=identity.staff();WalletModelGuard.require(request.getHeader("X-RPB-Wallet-Model"));return refunds.confirmPart(scope(storeId,a),a,id,body.partId(),body.receiptReference(),body.idempotencyKey());}
    @PostMapping("/refunds/{id}/cancel") public RefundDetail cancelRefund(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body,HttpServletRequest request) {var a=identity.staff();WalletModelGuard.require(request.getHeader("X-RPB-Wallet-Model"));return refunds.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/reconciliation") public Reconciliation report(@PathVariable UUID storeId,@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam(defaultValue="store") String group) {var a=identity.staff();return reports.query(scope(storeId,a),a,start,end,group);}
    private TopupCreate topupCreate(JsonNode body) {
        if(body==null||!body.isObject())throw singleOffer();
        var names=body.fieldNames();while(names.hasNext())if(!Set.of("accountId","amount","channel","idempotencyKey","quoteId").contains(names.next()))throw singleOffer();
        try{var command=new TopupCreate(uuid(body,"accountId"),text(body,"amount"),text(body,"channel"),text(body,"idempotencyKey"),uuid(body,"quoteId"));if(command.accountId()==null || (command.quoteId()!=null&&(body.has("amount")||body.has("channel"))) || (command.quoteId()==null&&(command.amount()==null||command.channel()==null)))throw singleOffer();return command;}catch(IllegalArgumentException e){throw singleOffer();}
    }
    private UUID uuid(JsonNode body,String key){String value=text(body,key);return value==null?null:UUID.fromString(value);}
    private String text(JsonNode body,String key){var value=body.get(key);if(value==null||value.isNull())return null;if(!value.isTextual())throw singleOffer();return value.textValue();}
    private WalletException singleOffer(){return new WalletException("WALLET_TOPUP_SINGLE_OFFER_REQUIRED",400,"充值仅允许一个报价或普通金额，不允许数量、赠送或身份覆盖字段");}
    public record AccountRequest(UUID customerId,String idempotencyKey) {}
    public record Key(String idempotencyKey) {}
    public record Code(String code) {}
    public record ResolveCode(String code,String purpose) {}
    public record RefundConfirmation(UUID partId,String receiptReference,String idempotencyKey) {}
}
