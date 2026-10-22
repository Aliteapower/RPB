package com.rpb.reservation.dailyledger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.dailyledger.application.LedgerCommandService;
import com.rpb.reservation.dailyledger.application.LedgerQueryService;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/daily-ledger")
@RequireAppGate(appKey="daily_ledger",permission="daily_ledger.view")
public class LedgerController {
    private final LedgerQueryService queries;private final LedgerCommandService commands;
    private final CurrentActorProvider actors;private final ObjectMapper json;
    public LedgerController(LedgerQueryService queries,LedgerCommandService commands,CurrentActorProvider actors,ObjectMapper json){this.queries=queries;this.commands=commands;this.actors=actors;this.json=json;}
    private CurrentActor actor(){return actors.currentActor().orElseThrow(()->new LedgerException("UNAUTHENTICATED",401,"请先登录"));}
    private StoreScope scope(UUID storeId,CurrentActor a){if(a.tenantId()==null)throw new LedgerException("FORBIDDEN",403,"需要租户账号");return new StoreScope(new TenantId(a.tenantId()),storeId);}
    public record SettingsResponse(JsonNode config,JsonNode branding,int version,com.rpb.reservation.dailyledger.application.LedgerStoreDisplay display) {}
    @GetMapping("/settings") public SettingsResponse settings(@PathVariable UUID storeId){var a=actor();var scope=scope(storeId,a);var s=queries.settings(scope,a);return new SettingsResponse(s.config(),s.branding(),s.version(),queries.display(scope,a));}
    @PutMapping("/settings") @RequireAppGate(appKey="daily_ledger",permission="daily_ledger.settings.manage")
    public SettingsResponse settings(@PathVariable UUID storeId,@Valid @RequestBody LedgerRequests.Settings body){var a=actor();var scope=scope(storeId,a);var s=commands.settings(scope,a,body.version(),body.config(),body.branding());return new SettingsResponse(s.config(),s.branding(),s.version(),queries.display(scope,a));}
    @GetMapping("/days/{date}") public ObjectNode day(@PathVariable UUID storeId,@PathVariable LocalDate date){var a=actor();return queries.day(scope(storeId,a),date,a);}
    @PutMapping("/days/{date}/records/{period}") @RequireAppGate(appKey="daily_ledger",permission="daily_ledger.write")
    public ObjectNode save(@PathVariable UUID storeId,@PathVariable LocalDate date,@PathVariable String period,@Valid @RequestBody LedgerRequests.Save body){var a=actor();return commands.save(scope(storeId,a),date,period,a,body.version(),body.client_save_id(),body.action(),body.input(json));}
    @GetMapping("/history") public List<ObjectNode> history(@PathVariable UUID storeId,@RequestParam LocalDate start,@RequestParam LocalDate end){var a=actor();return queries.history(scope(storeId,a),start,end,a);}
    @GetMapping("/years") public List<Integer> years(@PathVariable UUID storeId){var a=actor();return queries.years(scope(storeId,a),a);}
    @GetMapping("/audit") public List<JsonNode> audit(@PathVariable UUID storeId,@RequestParam LocalDate date,@RequestParam String period){var a=actor();return queries.audit(scope(storeId,a),date,period,a);}
}
