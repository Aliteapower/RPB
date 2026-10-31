package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import com.rpb.reservation.wallet.application.WalletViews.Collection;

@Repository
public class WalletCollectionRepository {
    private final JdbcTemplate jdbc;
    public WalletCollectionRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Collection confirm(StoreScope scope,UUID topup,UUID bill,String channel,long cents,String account,String reference,String terminal,String note,UUID actor) {
        return confirm(scope,topup,bill,channel,cents,account,reference,terminal,note,actor,false);
    }
    public Collection confirm(StoreScope scope,UUID topup,UUID bill,String channel,long cents,String account,String reference,String terminal,String note,UUID actor,boolean exception) {
        String label=jdbc.query("select case when ?::uuid is not null and t.channel=? then coalesce(t.offer_snapshot->>'channelName','') else coalesce(p.channel_labels->>?, '') end from wallet_programs p join wallet_program_stores ps on ps.program_id=p.id and ps.tenant_id=p.tenant_id left join wallet_topups t on t.id=? and t.tenant_id=p.tenant_id and t.store_id=ps.store_id where p.tenant_id=? and ps.store_id=?",(rs,n)->rs.getString(1),topup,channel,channel,topup,scope.tenantId().value(),scope.storeId().value()).stream().findFirst().orElse("");
        var rows=jdbc.query("insert into wallet_collections(tenant_id,store_id,topup_id,bill_id,channel,amount_cents,collection_account,receipt_reference,terminal_code,note,confirmed_by,is_exception,channel_name) values(?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing returning *",
            (rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),topup,bill,channel,cents,account,reference,terminal,note,actor,exception,label);
        if(rows.isEmpty()) throw new WalletException("WALLET_RECEIPT_USED",409,"此收款凭证已登记，请核查原交易，勿重复收款");
        return rows.getFirst();
    }
    public List<Collection> list(StoreScope scope,UUID topup,UUID bill) {
        return jdbc.query("select * from wallet_collections where tenant_id=? and store_id=? and ((?::uuid is not null and topup_id=?) or (?::uuid is not null and bill_id=?)) order by id",
            (rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),topup,topup,bill,bill);
    }
    public Collection get(StoreScope scope,UUID id) {
        return jdbc.query("select * from wallet_collections where tenant_id=? and store_id=? and id=?",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public Collection markException(StoreScope scope,UUID id,String reason) {
        jdbc.update("update wallet_collections set is_exception=true,exception_reason=? where tenant_id=? and store_id=? and id=?",reason,scope.tenantId().value(),scope.storeId().value(),id);
        return get(scope,id);
    }
    public List<Collection> exceptions(StoreScope scope) {
        return jdbc.query("select * from wallet_collections where tenant_id=? and store_id=? and is_exception=true order by created_at desc,id limit 100",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value());
    }
    public List<Collection> period(StoreScope scope,java.time.Instant from,java.time.Instant to) {
        return jdbc.query("select * from wallet_collections where tenant_id=? and store_id=? and created_at>=? and created_at<? order by created_at desc,id limit 1000",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),Timestamp.from(from),Timestamp.from(to));
    }
    public List<Collection> recent(StoreScope scope) {
        return jdbc.query("select * from wallet_collections where tenant_id=? and store_id=? order by created_at desc,id limit 100",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value());
    }
    static Collection row(ResultSet rs)throws SQLException {
        return new Collection(rs.getObject("id",UUID.class),rs.getObject("topup_id",UUID.class),rs.getObject("bill_id",UUID.class),rs.getString("channel"),rs.getLong("amount_cents"),rs.getLong("refunded_cents"),rs.getString("receipt_reference"),rs.getString("collection_account"),rs.getString("terminal_code"),rs.getBoolean("is_exception"),rs.getString("exception_reason"),rs.getTimestamp("created_at").toInstant(),rs.getString("channel_name"));
    }
}
