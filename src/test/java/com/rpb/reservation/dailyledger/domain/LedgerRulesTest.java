package com.rpb.reservation.dailyledger.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LedgerRulesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final LedgerRules rules = new LedgerRules(json);

    private ObjectNode node(String s) throws Exception { return (ObjectNode) json.readTree(s); }

    @Test void cashIncludesFloatAndPaidExpensesAreAddedBackOnlyToRevenue() throws Exception {
        ObjectNode record=node("""
            {"accounting_version":5,"base_amount":"500.00","cash":{"100":{"value":"10"}},
             "coin":{"value":"0.20"},"payments":{"paynow":{"value":"200.10"}},
             "expenses":[{"value":"10.00"}],"withdrawals":[],"withdrawal_snapshot":[{"value":"100.00"}]}
            """);
        var totals=rules.totals(record);
        assertThat(totals.path("cash").asText()).isEqualTo("1000.20");
        assertThat(totals.path("revenue").asText()).isEqualTo("810.30");
        assertThat(totals.path("handover").asText()).isEqualTo("600.20");
        record.put("accounting_version",3);
        assertThat(rules.totals(record).path("handover").asText()).isEqualTo("1100.20");
    }

    @Test void rejectsNegativeFractionalCountAndUnknownChannels() throws Exception {
        var config=rules.defaults();
        assertThatThrownBy(()->rules.amount("-1",false,false)).isInstanceOf(LedgerException.class);
        assertThatThrownBy(()->rules.amount("1.5",true,false)).isInstanceOf(LedgerException.class);
        assertThatThrownBy(()->rules.amount("1.001",false,false)).isInstanceOf(LedgerException.class);
        var input=node("{\"cash\":{},\"payments\":{\"injected\":{\"value\":\"50\"}}}");
        assertThatThrownBy(()->rules.record(input,json.createObjectNode(),config,"lunch",true,Instant.now()))
            .isInstanceOf(LedgerException.class);
    }

    @Test void finalShiftOnlySnapshotsSkimmingAndCannotCreateWithdrawals() throws Exception {
        var config=rules.defaults();
        var records=node("{\"lunch\":{\"version\":3,\"withdrawals\":[{\"id\":\"w\",\"value\":\"200.00\"}]}}");
        assertThat(rules.withdrawalSnapshot(config,records,"dinner").get(0).path("source_version").asInt()).isEqualTo(3);
        assertThat(rules.withdrawalSnapshot(config,records,"lunch")).isEmpty();
        var input=node("{\"withdrawals\":[{\"id\":\"new\",\"value\":\"20\"}]}");
        assertThatThrownBy(()->rules.record(input,json.createObjectNode(),config,"dinner",false,Instant.now()))
            .isInstanceOf(LedgerException.class);
    }

    @Test void settingsRequireEnabledFinalPeriodAndUniqueItems() throws Exception {
        var config=rules.defaults();
        config.put("final_period","breakfast");
        assertThatThrownBy(()->rules.settings(config)).isInstanceOf(LedgerException.class);
        config.put("final_period","dinner");
        assertThat(rules.settings(config).path("base_amount").asText()).isEqualTo("500.00");
    }

    @Test void blankDraftAndUnchangedFieldTimestampArePreserved() throws Exception {
        var old=node("{\"cash\":{\"100\":{\"value\":\"1\",\"note\":\"\",\"entered_at\":\"2026-10-01T00:00:00Z\"}}}");
        var input=node("{\"cash\":{\"100\":{\"value\":\"1\"}}}");
        var record=rules.record(input,old,rules.defaults(),"lunch",true,Instant.now());
        assertThat(record.path("cash").path("100").path("entered_at").asText()).isEqualTo("2026-10-01T00:00:00Z");
        assertThat(record.path("coin").path("value").asText()).isEmpty();
    }
}
