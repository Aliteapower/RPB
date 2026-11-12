package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.wallet.api.WalletApiIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.transactions.TransactionViews.*;

@RestController
public class TransactionController {
    private final WalletApiIdentity identity; private final TransactionService service;
    public TransactionController(WalletApiIdentity identity,TransactionService service){this.identity=identity;this.service=service;}
    @GetMapping("/api/v1/stores/{storeId}/wallet/transactions")
    public Page staff(@PathVariable UUID storeId,@RequestParam Map<String,String> query){var a=identity.staff();return service.list(identity.scope(a.tenantId(),storeId),a,null,query);}
    @GetMapping("/api/v1/public/stores/{storeId}/wallet/transactions")
    public Page customer(@PathVariable UUID storeId,@RequestParam Map<String,String> query,HttpServletRequest request){var c=identity.customer(request);return service.list(identity.scope(c.tenantId(),storeId),null,c,query);}
    @GetMapping("/api/v1/stores/{storeId}/wallet/transactions/{sourceType}/{id}")
    public Detail staffDetail(@PathVariable UUID storeId,@PathVariable String sourceType,@PathVariable UUID id){var a=identity.staff();return service.detail(identity.scope(a.tenantId(),storeId),a,null,sourceType,id);}
    @GetMapping("/api/v1/public/stores/{storeId}/wallet/transactions/{sourceType}/{id}")
    public Detail customerDetail(@PathVariable UUID storeId,@PathVariable String sourceType,@PathVariable UUID id,HttpServletRequest request){var c=identity.customer(request);return service.detail(identity.scope(c.tenantId(),storeId),null,c,sourceType,id);}
}
