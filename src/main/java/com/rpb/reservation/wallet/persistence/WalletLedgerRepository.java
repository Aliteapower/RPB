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
        Map<UUID,List<FundingPart>> parts=new HashMap<>();
        jdbc.query("select x.* from wallet_entry_allocations x join (select id from wallet_entries where tenant_id=? and account_id=? order by account_version desc limit 100 offset ?) e on e.id=x.entry_id where x.tenant_id=? and x.account_id=? order by x.created_at,x.id",rs->{parts.computeIfAbsent(rs.getObject("entry_id",UUID.class),k->new ArrayList<>()).add(new FundingPart(rs.getObject("lot_id",UUID.class),rs.getLong("principal_cents"),rs.getLong("bonus_cents")));},scope.tenantId().value(),account,Math.max(0,offset),scope.tenantId().value(),account);
        return jdbc.query("select e.*,coalesce((select sum(x.principal_cents) from wallet_entry_allocations x where x.entry_id=e.id and x.tenant_id=e.tenant_id),e.amount_cents) component_p,coalesce((select sum(x.bonus_cents) from wallet_entry_allocations x where x.entry_id=e.id and x.tenant_id=e.tenant_id),0) component_g from wallet_entries e where tenant_id=? and account_id=? order by account_version desc limit 100 offset ?",
            (rs,n)->new Entry(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getString("kind"),rs.getObject("source_id",UUID.class),rs.getLong("amount_cents"),rs.getLong("before_cents"),rs.getLong("after_cents"),rs.getInt("account_version"),rs.getTimestamp("created_at").toInstant(),rs.getLong("component_p"),rs.getLong("component_g"),parts.getOrDefault(rs.getObject("id",UUID.class),List.of())),scope.tenantId().value(),account,Math.max(0,offset));
    }
}
