package com.rpb.reservation.dailyledger.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.dailyledger.domain.LedgerRules;
import com.rpb.reservation.dailyledger.persistence.LedgerRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class LedgerQueryService {
    private final LedgerRepository repository;private final LedgerRules rules;private final LedgerAccess access;private final ObjectMapper json;
    public LedgerQueryService(LedgerRepository repository,LedgerRules rules,LedgerAccess access,ObjectMapper json){this.repository=repository;this.rules=rules;this.access=access;this.json=json;}
    public LedgerRepository.Settings settings(StoreScope s,CurrentActor actor){access.requireHistory(s,actor,"daily_ledger.view");return currentSettings(s);}
    public LedgerStoreDisplay display(StoreScope s,CurrentActor actor){access.requireHistory(s,actor,"daily_ledger.view");return repository.display(s);}
    LedgerRepository.Settings currentSettings(StoreScope s){return repository.settings(s).orElseGet(()->new LedgerRepository.Settings(rules.defaults(),json.createObjectNode(),0));}
    public ObjectNode day(StoreScope s,LocalDate date,CurrentActor actor){access.requireHistory(s,actor,"daily_ledger.view");return assemble(s,date);}
    ObjectNode assemble(StoreScope s,LocalDate date) {
        var frozen=repository.dayConfig(s,date);JsonNode config=frozen.orElseGet(()->currentSettings(s).config());
        ObjectNode out=json.createObjectNode().put("date",date.toString()).put("locked_config",frozen.isPresent());out.set("config",config);
        ObjectNode records=out.putObject("records"),submitted=out.putObject("submitted_records");
        for(var row:repository.records(s,date)) {
            ObjectNode raw=row.payload().deepCopy();raw.put("version",row.version());records.set(row.period(),raw);
            JsonNode snap=row.submitted()!=null?row.submitted():!raw.path("status").asText("submitted").equals("draft")?raw:null;
            if(snap!=null){ObjectNode saved=snap.deepCopy();saved.set("totals",rules.totals(saved));submitted.set(row.period(),saved);}
        }
        records.fields().forEachRemaining(entry->{ObjectNode raw=(ObjectNode)entry.getValue();raw.set("withdrawal_basis",rules.withdrawalBasis(config,records,entry.getKey()));raw.set("withdrawal_snapshot",rules.withdrawalSnapshot(config,records,entry.getKey()));raw.set("totals",rules.totals(raw));});
        String closing=config.path("final_period").asText("dinner");if(submitted.has(closing))out.put("last_period",closing);else out.putNull("last_period");return out;
    }
    public List<ObjectNode> history(StoreScope s,LocalDate start,LocalDate end,CurrentActor actor){
        access.requireHistory(s,actor,"daily_ledger.view");
        if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>=366)throw LedgerException.invalid("单次查询1至366天");
        return repository.dates(s,start,end).stream().map(d->assemble(s,d)).toList();
    }
    public List<Integer> years(StoreScope s,CurrentActor actor){access.requireHistory(s,actor,"daily_ledger.view");return repository.years(s);}
    public List<JsonNode> audit(StoreScope s,LocalDate date,String period,CurrentActor actor){access.requireHistory(s,actor,"daily_ledger.view");if(!LedgerRules.PERIODS.contains(period))throw LedgerException.invalid("餐段无效");return repository.audit(s,date,period);}
}
