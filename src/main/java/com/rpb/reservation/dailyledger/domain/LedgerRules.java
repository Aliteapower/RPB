package com.rpb.reservation.dailyledger.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure validation and accounting. Never accepts client-supplied computed totals. */
@Component
public class LedgerRules {
    public static final List<String> DENOMINATIONS=List.of("100","50","10","5","2","1","0.5","0.2","0.1");
    public static final List<String> PERIODS=List.of("breakfast","lunch","dinner");
    private final ObjectMapper json;
    public LedgerRules(ObjectMapper json) { this.json=json; }
    public ObjectNode defaults() {
        try { return (ObjectNode)json.readTree("""
            {"store_name":"每日营业记录","currency":"SGD","base_amount":"500.00","final_period":"dinner",
             "periods":[{"id":"breakfast","name":"早餐","enabled":false},{"id":"lunch","name":"午餐","enabled":true},{"id":"dinner","name":"晚餐","enabled":true}],
             "payments":[{"id":"nets","name":"Nets","enabled":true},{"id":"credit","name":"Credit","enabled":true},{"id":"delivery","name":"外卖","enabled":true},{"id":"wechat","name":"微信 / 支付宝","enabled":true},{"id":"paynow","name":"PayNow","enabled":true}],
             "expenses":[{"id":"purchase","name":"采购","enabled":true},{"id":"other","name":"其他开支","enabled":true}]}
            """); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public String text(JsonNode node,int limit,boolean required) {
        if(!node.isTextual() || node.textValue().length()>limit || (required&&node.textValue().isBlank()))
            throw LedgerException.invalid("文字为空或超出允许长度");
        return node.textValue().trim();
    }
    public String amount(String value,boolean integer,boolean blank) {
        String raw=value==null?"":value;
        if(raw.isEmpty()) return blank?"":integer?"0":"0.00";
        if(!raw.matches(integer?"\\d+":"\\d+(?:\\.\\d{1,2})?")) throw LedgerException.invalid("数量须为非负整数，金额最多两位小数");
        BigDecimal number=new BigDecimal(raw);
        if(number.compareTo(new BigDecimal("99999999"))>0) throw LedgerException.invalid("金额或数量超出范围");
        return integer?number.toBigIntegerExact().toString():number.setScale(2).toPlainString();
    }
    public ObjectNode settings(JsonNode input) {
        requireObject(input);
        ObjectNode out=json.createObjectNode();
        out.put("store_name",text(input.path("store_name"),60,true));
        String currency=text(input.path("currency"),3,true);
        if(!currency.matches("[A-Z]{3}")) throw LedgerException.invalid("币种无效");
        out.put("currency",currency); out.put("base_amount",amount(input.path("base_amount").asText(),false,false));
        for(String group:List.of("periods","payments","expenses")) {
            JsonNode items=input.path(group);
            if(!items.isArray()||items.isEmpty()||items.size()>30) throw LedgerException.invalid("每组项目需有1至30项");
            Set<String> ids=new HashSet<>(); ArrayNode result=out.putArray(group);
            for(JsonNode item:items) {
                String id=text(item.path("id"),50,true);
                if(!id.matches("[A-Za-z0-9_-]+")||!ids.add(id)||!item.path("enabled").isBoolean()) throw LedgerException.invalid("项目编号重复或无效");
                result.addObject().put("id",id).put("name",text(item.path("name"),40,true)).put("enabled",item.path("enabled").asBoolean());
            }
            if(group.equals("periods")) {
                if(!ids.equals(Set.copyOf(PERIODS))) throw LedgerException.invalid("保留早午晚三个餐段");
                ArrayNode sorted=json.createArrayNode();
                PERIODS.forEach(id->result.forEach(item->{if(item.path("id").asText().equals(id))sorted.add(item);})); out.set(group,sorted);
            }
        }
        String closing=input.path("final_period").asText("dinner");
        if(!enabled(out.path("periods")).contains(closing)) throw LedgerException.invalid("最终班次必须启用");
        out.put("final_period",closing);
        return out;
    }
    public Set<String> enabled(JsonNode items) {
        Set<String> ids=new HashSet<>();items.forEach(i->{if(i.path("enabled").asBoolean())ids.add(i.path("id").asText());});return ids;
    }
    public ObjectNode field(JsonNode raw,JsonNode old,boolean integer,boolean draft,Instant now) {
        if(raw.isMissingNode()||raw.isNull()) raw=json.createObjectNode();requireObject(raw);
        if(raw.has("value")&&!raw.path("value").isValueNode()) throw LedgerException.invalid("金额格式无效");
        String value=amount(raw.path("value").asText(""),integer,draft);
        String note=text(raw.path("note").isMissingNode()?json.getNodeFactory().textNode(""):raw.path("note"),300,false);
        if(old.isObject()&&old.path("value").asText().equals(value)&&old.path("note").asText().equals(note))return old.deepCopy();
        ObjectNode out=json.createObjectNode().put("value",value).put("note",note);
        if(raw.path("value").asText("").isEmpty()&&note.isEmpty()&&!raw.hasNonNull("entered_at"))out.putNull("entered_at");
        else {
            String stamp=raw.path("entered_at").asText(now.toString());
            try { OffsetDateTime.parse(stamp); } catch(Exception e) { throw LedgerException.invalid("录入时间须包含时区"); }
            out.put("entered_at",stamp);
        }
        return out;
    }
    public ObjectNode record(JsonNode input,JsonNode old,JsonNode config,String period,boolean draft,Instant now) {
        requireObject(input);
        if(!enabled(config.path("periods")).contains(period))throw LedgerException.invalid("餐段未启用");
        ObjectNode out=json.createObjectNode().put("status",draft?"draft":"submitted").put("accounting_version",5)
            .put("base_amount",config.path("base_amount").asText());
        JsonNode cash=input.path("cash"),payments=input.path("payments");
        if(!cash.isMissingNode())requireObject(cash);if(!payments.isMissingNode())requireObject(payments);
        cash.fieldNames().forEachRemaining(k->{if(!DENOMINATIONS.contains(k))throw LedgerException.invalid("现金币值无效");});
        Set<String> channels=enabled(config.path("payments"));
        payments.fieldNames().forEachRemaining(k->{if(!channels.contains(k))throw LedgerException.invalid("非现金项目已停用或不存在");});
        ObjectNode cashOut=out.putObject("cash"),paymentOut=out.putObject("payments");
        DENOMINATIONS.forEach(d->cashOut.set(d,field(cash.path(d),old.path("cash").path(d),true,draft,now)));
        channels.stream().sorted().forEach(k->paymentOut.set(k,field(payments.path(k),old.path("payments").path(k),false,draft,now)));
        out.set("coin",field(input.path("coin"),old.path("coin"),false,draft,now));
        out.set("expenses",rows(input.path("expenses"),old.path("expenses"),enabled(config.path("expenses")),draft,now));
        JsonNode withdrawals=input.path("withdrawals").isMissingNode()?json.createArrayNode():input.path("withdrawals");
        JsonNode previous=old.path("withdrawals").isMissingNode()?json.createArrayNode():old.path("withdrawals");
        if(period.equals(config.path("final_period").asText("dinner"))&&!withdrawals.equals(previous))
            throw LedgerException.invalid("最终班次只汇总前班抽款，不能修改抽大钞");
        out.set("withdrawals",rows(withdrawals,previous,null,draft,now));
        return out;
    }
    private ArrayNode rows(JsonNode items,JsonNode old,Set<String> categories,boolean draft,Instant now) {
        ArrayNode out=json.createArrayNode();if(items.isMissingNode())return out;
        if(!items.isArray()||items.size()>100)throw LedgerException.invalid("明细最多100行");
        Set<String> ids=new HashSet<>();
        for(JsonNode item:items) {
            requireObject(item);String id=text(item.path("id"),80,true);
            if(!ids.add(id))throw LedgerException.invalid("明细编号重复");
            JsonNode previous=json.createObjectNode();
            for(JsonNode candidate:old)if(candidate.path("id").asText().equals(id))previous=candidate;
            ObjectNode row=field(item,previous,false,draft,now);row.put("id",id);
            if(categories!=null) {
                String category=text(item.path("category"),50,true);
                if(!categories.contains(category))throw LedgerException.invalid("开支项目已停用或不存在");row.put("category",category);
            }
            out.add(row);
        }
        return out;
    }
    public ArrayNode withdrawalBasis(JsonNode config,JsonNode records,String period) {
        ArrayNode out=json.createArrayNode();if(!period.equals(config.path("final_period").asText("dinner")))return out;
        for(JsonNode p:config.path("periods")) {String id=p.path("id").asText();if(!id.equals(period))out.addObject().put("period",id).put("version",records.path(id).path("version").asInt());}return out;
    }
    public ArrayNode withdrawalSnapshot(JsonNode config,JsonNode records,String period) {
        ArrayNode out=json.createArrayNode();
        for(JsonNode source:withdrawalBasis(config,records,period)) {
            String id=source.path("period").asText();for(JsonNode item:records.path(id).path("withdrawals")) {
                ObjectNode row=item.deepCopy();row.put("source_period",id).put("source_version",source.path("version").asInt());out.add(row);
            }
        }
        return out;
    }
    private BigDecimal money(JsonNode value) {return new BigDecimal(value.asText("0").isEmpty()?"0":value.asText("0"));}
    private BigDecimal sum(JsonNode rows) {BigDecimal sum=BigDecimal.ZERO;for(JsonNode item:rows)sum=sum.add(money(item.path("value")));return sum;}
    public ObjectNode totals(JsonNode record) {
        BigDecimal cash=money(record.path("coin").path("value"));
        for(String d:DENOMINATIONS)cash=cash.add(new BigDecimal(d).multiply(money(record.path("cash").path(d).path("value"))));
        BigDecimal noncash=sum(record.path("payments")),expenses=sum(record.path("expenses"));
        BigDecimal withdrawn=sum(record.path("withdrawals")).add(sum(record.path("withdrawal_snapshot")));
        BigDecimal base=record.path("accounting_version").asInt()>=5?money(record.path("base_amount")):BigDecimal.ZERO;
        ObjectNode out=json.createObjectNode();
        putMoney(out,"cash",cash);putMoney(out,"noncash",noncash);putMoney(out,"expenses",expenses);putMoney(out,"withdrawn",withdrawn);
        putMoney(out,"revenue",cash.add(withdrawn).add(noncash).add(expenses).subtract(base));putMoney(out,"handover",cash.add(withdrawn).subtract(base));
        return out;
    }
    private void putMoney(ObjectNode out,String key,BigDecimal value) {out.put(key,value.setScale(2,RoundingMode.UNNECESSARY).toPlainString());}
    public void requireObject(JsonNode node) {if(node==null||!node.isObject())throw LedgerException.invalid("数据应为对象");}
}
