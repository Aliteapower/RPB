package com.rpb.reservation.cards.application;
import com.rpb.reservation.common.scope.StoreScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CardSettingsRepository {
 private final JdbcTemplate jdbc;public CardSettingsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean enabled(StoreScope s){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from card_settings where tenant_id=? and store_id=? and enabled)",Boolean.class,s.tenantId().value(),s.storeId().value()));}
 public void set(StoreScope s,boolean enabled){jdbc.update("insert into card_settings(tenant_id,store_id,enabled) values(?,?,?) on conflict(tenant_id,store_id) do update set enabled=excluded.enabled,updated_at=now()",s.tenantId().value(),s.storeId().value(),enabled);}
}
