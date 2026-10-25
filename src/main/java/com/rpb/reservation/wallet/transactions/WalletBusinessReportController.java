package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.wallet.api.WalletApiIdentity;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class WalletBusinessReportController {
    private final WalletApiIdentity identity;
    private final WalletBusinessReportService service;
    public WalletBusinessReportController(WalletApiIdentity identity,WalletBusinessReportService service){this.identity=identity;this.service=service;}
    @GetMapping("/api/v1/stores/{storeId}/wallet/business-report")
    public WalletBusinessReport query(@PathVariable UUID storeId,@RequestParam Map<String,String> query){var actor=identity.staff();return service.query(identity.scope(actor.tenantId(),storeId),actor,query);}
}
