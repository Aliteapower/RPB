package com.rpb.reservation.wallet.marketing;
import com.fasterxml.jackson.databind.JsonNode;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet")
public class WalletMarketingController {
 private final WalletApiIdentity identity;private final WalletMarketingConfigurationService config;private final WalletMarketingService marketing;private final MarketingRequestReader reader;
 public WalletMarketingController(WalletApiIdentity identity,WalletMarketingConfigurationService config,WalletMarketingService marketing,MarketingRequestReader reader){this.identity=identity;this.config=config;this.marketing=marketing;this.reader=reader;}
 @GetMapping("/marketing/settings") public Settings settings(@PathVariable UUID storeId){var a=identity.staff();return config.settings(identity.scope(a.tenantId(),storeId),a);}
 @PutMapping("/marketing/settings") public Settings settings(@PathVariable UUID storeId,@RequestBody JsonNode body){var a=identity.staff();return config.save(identity.scope(a.tenantId(),storeId),a,reader.read(body,SettingsUpdate.class));}
 @GetMapping("/marketing/campaigns") public Map<String,Object> campaigns(@PathVariable UUID storeId,@RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String cursor){var a=identity.staff();var items=config.campaigns(identity.scope(a.tenantId(),storeId),a,status.isBlank()?"all":status,limit,cursor);String next=items.size()<limit?"":Base64.getUrlEncoder().withoutPadding().encodeToString((items.getLast().createdAt()+"|"+items.getLast().id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));return Map.of("items",items,"nextCursor",next);}
 @PostMapping("/marketing/campaigns") public Campaign create(@PathVariable UUID storeId,@RequestBody JsonNode body){var a=identity.staff();return config.create(identity.scope(a.tenantId(),storeId),a,reader.read(body,CampaignDraft.class));}
 @GetMapping("/marketing/campaigns/{id}") public Campaign campaign(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();return config.campaign(identity.scope(a.tenantId(),storeId),a,id);}
 @PutMapping("/marketing/campaigns/{id}/draft") public Campaign draft(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body){var a=identity.staff();return config.draft(identity.scope(a.tenantId(),storeId),a,id,reader.read(body,CampaignDraft.class));}
 @PostMapping("/marketing/campaigns/{id}/publish") public Campaign publish(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body){var a=identity.staff();return config.publish(identity.scope(a.tenantId(),storeId),a,id,reader.read(body,Publish.class));}
 @PostMapping("/marketing/campaigns/{id}/disable") public Campaign disable(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body){var a=identity.staff();return config.lifecycle(identity.scope(a.tenantId(),storeId),a,id,reader.read(body,Lifecycle.class),false);}
 @PostMapping("/marketing/campaigns/{id}/archive") public Campaign archive(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody JsonNode body){var a=identity.staff();return config.lifecycle(identity.scope(a.tenantId(),storeId),a,id,reader.read(body,Lifecycle.class),true);}
 @GetMapping("/topup-offers") public Offers offers(@PathVariable UUID storeId){var a=identity.staff();return marketing.offers(identity.scope(a.tenantId(),storeId),a);}
 @PostMapping("/topup-quotes") public Quote quote(@PathVariable UUID storeId,@RequestBody JsonNode body){var a=identity.staff();return marketing.quote(identity.scope(a.tenantId(),storeId),a,reader.read(body,QuoteRequest.class));}
}
