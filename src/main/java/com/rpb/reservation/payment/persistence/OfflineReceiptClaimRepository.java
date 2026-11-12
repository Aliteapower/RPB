package com.rpb.reservation.payment.persistence;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class OfflineReceiptClaimRepository {
 private final JdbcTemplate jdbc;public OfflineReceiptClaimRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void claim(StoreScope s,UUID source,String type,String channel,String account,String receipt){jdbc.update("insert into payment_offline_receipt_claims(tenant_id,store_id,channel,account,receipt,source_type,source_id) values(?,?,?,?,?,?,?)",s.tenantId().value(),s.storeId().value(),channel.split(":",2)[0],account,receipt,type,source);}
}
