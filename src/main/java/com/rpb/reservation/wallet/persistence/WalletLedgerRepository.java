package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletLedgerRepository {
    private final JdbcTemplate jdbc;
    public WalletLedgerRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void append(StoreScope scope,Account before,long after,String kind,UUID source,UUID actor,UUID original) {
        long amount=Math.abs(after-before.balanceCents());
        if(amount==0) return;
        jdbc.update("insert into wallet_entries(tenant_id,program_id,account_id,store_id,actor_id,kind,source_id,amount_cents,before_cents,after_cents,account_version,original_entry_id) values(?,?,?,?,?,?,?,?,?,?,?,?)",
            scope.tenantId().value(),before.programId(),before.id(),scope.storeId().value(),actor,kind,source,amount,before.balanceCents(),after,before.version()+1,original);
    }
    public UUID original(UUID tenant,String kind,UUID source) {
        return jdbc.query("select id from wallet_entries where tenant_id=? and kind=? and source_id=?",(rs,n)->rs.getObject("id",UUID.class),tenant,kind,source).stream().findFirst().orElse(null);
    }
    public List<Entry> entries(StoreScope scope,UUID account,int offset) {
        return jdbc.query("select * from wallet_entries where tenant_id=? and account_id=? order by account_version desc limit 100 offset ?",
            (rs,n)->new Entry(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getString("kind"),rs.getObject("source_id",UUID.class),rs.getLong("amount_cents"),rs.getLong("before_cents"),rs.getLong("after_cents"),rs.getInt("account_version"),rs.getTimestamp("created_at").toInstant()),scope.tenantId().value(),account,Math.max(0,offset));
    }
}
