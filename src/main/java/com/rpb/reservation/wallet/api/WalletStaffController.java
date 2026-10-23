package com.rpb.reservation.wallet.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.*;
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
    @GetMapping("/accounts/{id}/entries") public List<Entry> entries(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam(defaultValue="0") int offset) {var a=identity.staff();return accounts.entries(scope(storeId,a),a,id,offset);}
    @GetMapping("/topups") public List<Topup> topups(@PathVariable UUID storeId,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="all") String status) {var a=identity.staff();return topups.list(scope(storeId,a),a,new WalletHistoryQuery(offset,status));}
    @GetMapping("/topups/{id}") public Topup topup(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return topups.get(scope(storeId,a),a,id);}
    @PostMapping("/topups") public Topup createTopup(@PathVariable UUID storeId,@RequestBody TopupCreate body) {var a=identity.staff();return topups.create(scope(storeId,a),a,body);}
    @PostMapping("/topups/{id}/confirm") public TopupResult confirm(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CollectionConfirm body) {var a=identity.staff();return topups.confirm(scope(storeId,a),a,id,body);}
    @PostMapping("/topups/{id}/cancel") public Topup cancelTopup(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body) {var a=identity.staff();return topups.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/topups/{id}/paynow") public Map<String,String> topupPaynow(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return Map.of("payload",topups.paynowQr(scope(storeId,a),a,id,collection));}
    @GetMapping("/bills") public List<Bill> bills(@PathVariable UUID storeId,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="all") String status) {var a=identity.staff();return checkout.list(scope(storeId,a),a,new WalletHistoryQuery(offset,status));}
    @GetMapping("/bills/{id}") public Bill bill(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return checkout.get(scope(storeId,a),a,id);}
    @PostMapping("/bills") public Bill createBill(@PathVariable UUID storeId,@RequestBody BillCreate body) {var a=identity.staff();return checkout.create(scope(storeId,a),a,body);}
    @PostMapping("/bills/{id}/code") public PaymentCode billCode(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return authorization.issueBillCode(scope(storeId,a),a,id);}
    @PostMapping("/bills/{id}/bind-code") public Bill bind(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Code body) {var a=identity.staff();return authorization.bind(scope(storeId,a),a,id,body.code());}
    @PostMapping("/bills/{id}/collections") public Bill supplement(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CollectionConfirm body) {var a=identity.staff();return checkout.collect(scope(storeId,a),a,id,body);}
    @GetMapping("/bills/{id}/paynow") public Map<String,String> billPaynow(@PathVariable UUID storeId,@PathVariable UUID id) {
        var a=identity.staff();return Map.of("payload",checkout.paynowQr(scope(storeId,a),a,id,collection));
    }
    @PostMapping("/bills/{id}/cancel") public Bill cancelBill(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body) {var a=identity.staff();return checkout.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/refunds") public List<Refund> refunds(@PathVariable UUID storeId) {var a=identity.staff();return refunds.list(scope(storeId,a),a);}
    @GetMapping("/refunds/{id}") public RefundDetail refund(@PathVariable UUID storeId,@PathVariable UUID id) {var a=identity.staff();return refunds.get(scope(storeId,a),a,id);}
    @PostMapping("/refunds") public RefundDetail createRefund(@PathVariable UUID storeId,@RequestBody RefundCreate body) {var a=identity.staff();return refunds.create(scope(storeId,a),a,body);}
    @PostMapping("/refunds/{id}/confirm") public RefundDetail confirmRefund(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody RefundConfirmation body) {var a=identity.staff();return refunds.confirmPart(scope(storeId,a),a,id,body.partId(),body.receiptReference(),body.idempotencyKey());}
    @PostMapping("/refunds/{id}/cancel") public RefundDetail cancelRefund(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key body) {var a=identity.staff();return refunds.cancel(scope(storeId,a),a,id,body.idempotencyKey());}
    @GetMapping("/reconciliation") public Reconciliation report(@PathVariable UUID storeId,@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam(defaultValue="store") String group) {var a=identity.staff();return reports.query(scope(storeId,a),a,start,end,group);}
    public record AccountRequest(UUID customerId,String idempotencyKey) {}
    public record Key(String idempotencyKey) {}
    public record Code(String code) {}
    public record ResolveCode(String code,String purpose) {}
    public record RefundConfirmation(UUID partId,String receiptReference,String idempotencyKey) {}
}
