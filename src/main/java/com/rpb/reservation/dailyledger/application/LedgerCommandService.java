package com.rpb.reservation.dailyledger.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.dailyledger.domain.LedgerRules;
import com.rpb.reservation.dailyledger.persistence.LedgerRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerCommandService {
    private final LedgerRepository repository;private final LedgerRules rules;private final LedgerAccess access;
    private final LedgerQueryService queries;private final ObjectMapper json;private final Clock clock;
    public LedgerCommandService(LedgerRepository repository,LedgerRules rules,LedgerAccess access,LedgerQueryService queries,ObjectMapper json,Clock clock){this.repository=repository;this.rules=rules;this.access=access;this.queries=queries;this.json=json;this.clock=clock;}

    @Transactional
    public LedgerRepository.Settings settings(StoreScope s,CurrentActor actor,int version,JsonNode config,JsonNode branding) {
        access.require(s,actor,"daily_ledger.settings.manage");
        JsonNode normalized=rules.settings(config);validateBranding(branding);
        repository.lockSettings(s,rules.defaults());
        var before=queries.currentSettings(s);
        if(!repository.updateSettings(s,normalized,branding,version))throw LedgerException.conflict("VERSION_CONFLICT","设置已更新，请重新加载");
        ObjectNode audit=json.createObjectNode();audit.set("before",json.valueToTree(before));audit.set("after",json.valueToTree(queries.currentSettings(s)));
        repository.audit(s,null,null,"settings",actor.actorId(),repository.actorName(actor.actorId()),audit);
        return queries.currentSettings(s);
    }
    private void validateBranding(JsonNode branding) {
        rules.requireObject(branding);
        rules.text(branding.path("store_name"),60,true);rules.text(branding.path("store_name_en"),100,false);
        String logo=rules.text(branding.path("logo_data"),160000,false);
        if(!logo.isEmpty()) {
            try {
                if(!logo.startsWith("data:image/png;base64,"))throw new IllegalArgumentException();
                byte[] bytes=java.util.Base64.getDecoder().decode(logo.substring(22));
                byte[] signature={(byte)137,80,78,71,13,10,26,10};
                if(bytes.length<45||!java.util.Arrays.equals(signature,java.util.Arrays.copyOf(bytes,8)))throw new IllegalArgumentException();
                var buffer=java.nio.ByteBuffer.wrap(bytes);int width=buffer.getInt(16),height=buffer.getInt(20);
                if(width<1||height<1||width>256||height>256)throw new IllegalArgumentException();
            }catch(Exception e){throw LedgerException.invalid("Logo 须为256像素以内 PNG 图片");}
        }
    }
    @Transactional
    public ObjectNode save(StoreScope s,LocalDate date,String period,CurrentActor actor,int version,String key,String action,JsonNode input) {
        access.require(s,actor,"daily_ledger.write");
        if(!LedgerRules.PERIODS.contains(period)||version<0||key==null||key.isBlank()||key.length()>100||!List.of("draft","submit").contains(action))
            throw LedgerException.invalid("保存请求无效");
        if(input.toString().length()>262144)throw LedgerException.invalid("请求过大");
        String requestHash=hash(date+":"+period+":"+action+":"+canonical(input));
        repository.lockSettings(s,rules.defaults());
        var replay=repository.savedRequest(s,key);
        if(replay.isPresent()) {
            var previous=replay.get();
            if(!previous.actorId().equals(actor.actorId())||!previous.requestHash().equals(requestHash))
                throw LedgerException.conflict("IDEMPOTENCY_CONFLICT","保存编号已用于其他输入");
            return queries.assemble(s,date);
        }
        repository.createDay(s,date,queries.currentSettings(s).config());
        ObjectNode day=queries.assemble(s,date);JsonNode config=day.path("config"),records=day.path("records"),old=records.path(period);
        if(day.path("submitted_records").has(period)&&!actor.hasPermission("daily_ledger.settings.manage"))
            throw new LedgerException("FORBIDDEN",403,"本班已提交，仅管理员可以修改");
        if(version!=old.path("version").asInt())throw LedgerException.conflict("VERSION_CONFLICT","记录已被更新，请重新加载核对，当前输入仍保留");
        if(!input.path("config_snapshot").equals(config))throw LedgerException.conflict("SOURCE_CHANGED","当日配置已更新，请重新加载核对");
        if(!input.path("withdrawal_basis").equals(rules.withdrawalBasis(config,records,period)))
            throw LedgerException.conflict("SOURCE_CHANGED","前班抽款已更新，请重新加载核对");
        boolean draft=action.equals("draft");var now=clock.instant();
        ObjectNode record=rules.record(input,old,config,period,draft,now);
        record.put("version",version+1).put("saved_at",now.toString()).put("saved_by",repository.actorName(actor.actorId()))
            .put("saved_by_id",actor.actorId().toString()).put("client_save_id",key);
        record.set("withdrawal_basis",rules.withdrawalBasis(config,records,period));record.set("withdrawal_snapshot",rules.withdrawalSnapshot(config,records,period));record.set("totals",rules.totals(record));
        if(!draft)record.put("submitted_at",now.toString());
        JsonNode submitted=draft?day.path("submitted_records").get(period):record;
        if(!repository.writeRecord(s,date,period,version,record,submitted))throw LedgerException.conflict("VERSION_CONFLICT","记录已更新");
        repository.saveRequest(s,date,period,key,actor.actorId(),requestHash);
        repository.audit(s,date,period,action,actor.actorId(),record.path("saved_by").asText(),record);
        return queries.assemble(s,date);
    }
    private String canonical(JsonNode node) {
        if(node.isObject()){var names=new java.util.ArrayList<String>();node.fieldNames().forEachRemaining(names::add);java.util.Collections.sort(names);ObjectNode out=json.createObjectNode();for(String name:names)out.set(name,parseCanonical(node.get(name)));return out.toString();}
        if(node.isArray()){var out=json.createArrayNode();node.forEach(n->out.add(parseCanonical(n)));return out.toString();}return node.toString();
    }
    private JsonNode parseCanonical(JsonNode node){try{return json.readTree(canonical(node));}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
