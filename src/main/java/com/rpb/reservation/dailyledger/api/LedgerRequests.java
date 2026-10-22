package com.rpb.reservation.dailyledger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LedgerRequests {
    private LedgerRequests() {}
    public record Settings(@Min(0) int version,@NotNull JsonNode config,@NotNull JsonNode branding) {}
    public record Save(@Min(0) int version,@NotBlank @Size(max=100) String client_save_id,@NotBlank String action,
                       JsonNode cash,JsonNode coin,JsonNode payments,JsonNode expenses,JsonNode withdrawals,
                       @NotNull JsonNode config_snapshot,@NotNull JsonNode withdrawal_basis) {
        public ObjectNode input(ObjectMapper json){
            ObjectNode node=json.createObjectNode();
            if(cash!=null)node.set("cash",cash);if(coin!=null)node.set("coin",coin);if(payments!=null)node.set("payments",payments);
            if(expenses!=null)node.set("expenses",expenses);if(withdrawals!=null)node.set("withdrawals",withdrawals);
            node.set("config_snapshot",config_snapshot);node.set("withdrawal_basis",withdrawal_basis);return node;
        }
    }
}
