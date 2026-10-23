package com.rpb.reservation.wallet.api;
import com.rpb.reservation.wallet.integration.WalletPointsAdapter;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wallet/points")
public class WalletPointsCustomerController {
 private final WalletApiIdentity identity;private final WalletPointsAdapter points;
 public WalletPointsCustomerController(WalletApiIdentity identity,WalletPointsAdapter points){this.identity=identity;this.points=points;}
 @GetMapping public Account get(@PathVariable UUID storeId,HttpServletRequest request){var p=identity.customer(request);return points.own(identity.scope(p.tenantId(),storeId),p);}
 @GetMapping("/entries")public List<Entry> entries(@PathVariable UUID storeId,@RequestParam(defaultValue="0")int offset,HttpServletRequest request){var p=identity.customer(request);return points.ownEntries(identity.scope(p.tenantId(),storeId),p,offset);}
}
