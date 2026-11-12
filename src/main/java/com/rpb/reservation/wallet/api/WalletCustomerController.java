package com.rpb.reservation.wallet.api;

import com.rpb.reservation.wallet.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wallet")
public class WalletCustomerController {
    private final WalletApiIdentity identity;private final WalletAccountService accounts;
    private final WalletCheckoutService checkout;private final WalletAuthorizationService authorization;
    public WalletCustomerController(WalletApiIdentity identity,WalletAccountService accounts,WalletCheckoutService checkout,WalletAuthorizationService authorization) {this.identity=identity;this.accounts=accounts;this.checkout=checkout;this.authorization=authorization;}
    @GetMapping("/me") public CustomerHome home(@PathVariable UUID storeId,HttpServletRequest request) {
        var p=identity.customer(request);var s=identity.scope(p.tenantId(),storeId);return new CustomerHome(accounts.customerContext(s,p),accounts.ownAccounts(s,p),checkout.pending(s,p));
    }
    @GetMapping("/pending-bills") public List<Bill> pending(@PathVariable UUID storeId,HttpServletRequest request) {var p=identity.customer(request);return checkout.pending(identity.scope(p.tenantId(),storeId),p);}
    @GetMapping("/bills/{id}") public Bill receipt(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request) {var p=identity.customer(request);return checkout.ownBill(identity.scope(p.tenantId(),storeId),p,id);}
    @PostMapping("/payment-codes") public PaymentCode code(@PathVariable UUID storeId,@RequestBody AccountCode body,HttpServletRequest request) {var p=identity.customer(request);return authorization.issuePaymentCode(identity.scope(p.tenantId(),storeId),p,body.accountId(),body.purpose());}
    @PostMapping("/bill-code") public Bill scan(@PathVariable UUID storeId,@RequestBody BillCode body,HttpServletRequest request) {var p=identity.customer(request);return authorization.scanBill(identity.scope(p.tenantId(),storeId),p,body.code());}
    @PostMapping("/bills/{id}/quote") public Quote quote(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request) {var p=identity.customer(request);return authorization.quote(identity.scope(p.tenantId(),storeId),p,id);}
    @PostMapping("/bills/{id}/pay") public Paid pay(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Pay body,HttpServletRequest request) {var p=identity.customer(request);WalletModelGuard.require(request);return authorization.pay(identity.scope(p.tenantId(),storeId),p,id,body);}
    public record AccountCode(UUID accountId,String purpose) {}
    public record BillCode(String code) {}
}
