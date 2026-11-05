package com.rpb.reservation.platformbilling.persistence;

import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.platformbilling.application.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SelectedBillingCommandRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public SelectedBillingCommandRepository(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public void lock(UUID tenant,UUID subscription) {jdbc.queryForList("select id from tenant_product_subscriptions where tenant_id=? and id=? for update",tenant,subscription);}
    public ProductSubscriptionMutationResult run(UUID tenant,String operation,String key,Object request,Supplier<ProductSubscriptionMutationResult> work) {
        if(key==null || key.isBlank() || key.length()>100)throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(request)));
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",tenant+":"+operation+":"+key);
            var prior=jdbc.queryForList("select request_hash,response::text from platform_billing_command_results where tenant_id=? and operation=? and idempotency_key=?",tenant,operation,key);
            if(!prior.isEmpty()) {
                if(!hash.equals(prior.getFirst().get("request_hash")))throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
                var result=json.readValue((String)prior.getFirst().get("response"),ProductSubscriptionMutationResult.class);
                return new ProductSubscriptionMutationResult(true,result.subscription(),result.quote());
            }
            var result=work.get();jdbc.update("insert into platform_billing_command_results(tenant_id,operation,idempotency_key,request_hash,response) values(?,?,?,?,?::jsonb)",tenant,operation,key,hash,json.writeValueAsString(result));return result;
        } catch(com.fasterxml.jackson.core.JsonProcessingException|NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
