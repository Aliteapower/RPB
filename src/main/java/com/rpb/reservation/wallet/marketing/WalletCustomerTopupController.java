package com.rpb.reservation.wallet.marketing;
import com.fasterxml.jackson.databind.JsonNode;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.wallet.application.WalletViews;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;
@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wallet")
public class WalletCustomerTopupController {
 private final WalletApiIdentity identity;private final WalletMarketingService marketing;private final WalletCustomerTopupService topups;private final MarketingRequestReader reader;
 public WalletCustomerTopupController(WalletApiIdentity identity,WalletMarketingService marketing,WalletCustomerTopupService topups,MarketingRequestReader reader){this.identity=identity;this.marketing=marketing;this.topups=topups;this.reader=reader;}
 @GetMapping("/topup-offers") public Offers offers(@PathVariable UUID storeId,HttpServletRequest request){var p=identity.customer(request);return marketing.customerOffers(identity.scope(p.tenantId(),storeId),p);}
 @PostMapping("/topup-quotes") public Quote quote(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){var p=identity.customer(request);return marketing.customerQuote(identity.scope(p.tenantId(),storeId),p,reader.read(body,CustomerQuote.class));}
 @PostMapping("/topups") public WalletViews.Topup apply(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){var p=identity.customer(request);return topups.apply(identity.scope(p.tenantId(),storeId),p,reader.read(body,Apply.class),request.getRemoteAddr());}
 @GetMapping("/topups/{id}") public WalletViews.Topup get(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);return topups.get(identity.scope(p.tenantId(),storeId),p,id);}
 @GetMapping("/topups/{id}/paynow") public Map<String,String> paynow(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);return topups.paynow(identity.scope(p.tenantId(),storeId),p,id);}
 @PostMapping("/topups/{id}/declare-paid") public WalletViews.Topup declare(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body,HttpServletRequest request){var p=identity.customer(request);return topups.declare(identity.scope(p.tenantId(),storeId),p,id,reader.read(body,CustomerAction.class));}
 @PostMapping("/topups/{id}/cancel") public WalletViews.Topup cancel(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body,HttpServletRequest request){var p=identity.customer(request);return topups.cancel(identity.scope(p.tenantId(),storeId),p,id,reader.read(body,CustomerAction.class));}
}
