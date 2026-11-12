package com.rpb.reservation.catalog.persistence;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.catalog.domain.CatalogException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Tenant-wide product code allocation inside the caller's write transaction. */
public final class CatalogProductCodes {
 private final JdbcTemplate jdbc;
 public CatalogProductCodes(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void lock(StoreScope scope){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Product code allocation requires a transaction");
  jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,71829))",scope.tenantId().value().toString());
 }
 public String next(StoreScope scope){
  lock(scope);
  var free=jdbc.queryForList("select n::text from generate_series(1,99999) n where not exists(select 1 from catalog_products p where p.tenant_id=? and p.code=n::text) limit 1",String.class,scope.tenantId().value());
  if(free.isEmpty())throw new CatalogException("CATALOG_CODE_EXHAUSTED",409,"短编号已用完，请填写自定义编号 / Short codes exhausted; enter a custom code");
  return free.get(0);
 }
}
