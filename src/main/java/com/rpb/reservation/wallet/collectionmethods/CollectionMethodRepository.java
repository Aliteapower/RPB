package com.rpb.reservation.wallet.collectionmethods;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CollectionMethodRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public CollectionMethodRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public Map<String,String> labels(StoreScope s,UUID program){var value=jdbc.queryForObject("select channel_labels::text from wallet_programs where tenant_id=? and id=?",String.class,s.tenantId().value(),program);try{return new LinkedHashMap<>(json.readValue(value,new TypeReference<Map<String,String>>(){}));}catch(Exception e){throw new IllegalStateException(e);}}
 public void save(StoreScope s,UUID program,Map<String,String> labels){try{jdbc.update("update wallet_programs set channel_labels=?::jsonb where tenant_id=? and id=?",json.writeValueAsString(labels),s.tenantId().value(),program);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
}
