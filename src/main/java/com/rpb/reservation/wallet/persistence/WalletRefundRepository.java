package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.wallet.application.WalletViews.Collection;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletRefundRepository {
    private final JdbcTemplate jdbc;
    public WalletRefundRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Refund create(StoreScope scope,Account a,UUID topup,UUID bill,long amount,long wallet,String reason,UUID actor,String status) {
        return jdbc.query("insert into wallet_refunds(tenant_id,program_id,account_id,store_id,topup_id,bill_id,amount_cents,wallet_cents,status,reason,created_by,principal_cents) values(?,?,?,?,?,?,?,?,?,?,?,?) returning *",
            (rs,n)->row(rs),scope.tenantId().value(),a.programId(),a.id(),scope.storeId().value(),topup,bill,amount,wallet,status,reason,actor,wallet).getFirst();
    }
    public Refund get(StoreScope scope,UUID id,boolean lock) {
        return jdbc.query("select * from wallet_refunds where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public List<Refund> list(StoreScope scope) {
        return jdbc.query("select * from wallet_refunds where tenant_id=? and store_id=? order by created_at desc,id limit 100",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value());
    }
    public List<Collection> collections(StoreScope scope,UUID topup,UUID bill) {return new WalletCollectionRepository(jdbc).list(scope,topup,bill);}
    public Collection collection(StoreScope scope,UUID id) {return new WalletCollectionRepository(jdbc).get(scope,id);}
    public void exception(StoreScope scope,UUID refund,UUID collection) {jdbc.update("update wallet_refunds set exception_collection_id=? where tenant_id=? and store_id=? and id=?",collection,scope.tenantId().value(),scope.storeId().value(),refund);}
    public long walletAllocated(StoreScope scope,UUID bill) {
        return jdbc.queryForObject("select coalesce(sum(wallet_cents),0) from wallet_refunds where tenant_id=? and bill_id=? and status<>'cancelled'",Long.class,scope.tenantId().value(),bill);
    }
    public long collectionAllocated(UUID tenant,UUID collection) {
        return jdbc.queryForObject("select coalesce(sum(p.amount_cents),0) from wallet_refund_parts p join wallet_refunds r on r.id=p.refund_id and r.tenant_id=p.tenant_id where p.tenant_id=? and p.collection_id=? and r.status<>'cancelled'",Long.class,tenant,collection);
    }
    public long completedAmount(StoreScope scope,UUID bill) {
        return jdbc.queryForObject("select coalesce(sum(amount_cents),0) from wallet_refunds where tenant_id=? and bill_id=? and status='completed' and exception_collection_id is null",Long.class,scope.tenantId().value(),bill);
    }
    public boolean hasPending(StoreScope scope,UUID bill) {
        return jdbc.queryForObject("select exists(select 1 from wallet_refunds where tenant_id=? and bill_id=? and status='pending' and exception_collection_id is null)",Boolean.class,scope.tenantId().value(),bill);
    }
    public void addPart(StoreScope scope,UUID refund,UUID collection,long amount) {
        jdbc.update("insert into wallet_refund_parts(tenant_id,refund_id,collection_id,amount_cents) values(?,?,?,?)",scope.tenantId().value(),refund,collection,amount);
    }
    public List<RefundPart> parts(StoreScope scope,UUID id) {
        return jdbc.query("select p.*,c.channel,c.channel_name from wallet_refund_parts p join wallet_collections c on c.id=p.collection_id and c.tenant_id=p.tenant_id where p.tenant_id=? and p.refund_id=? order by p.id",
            (rs,n)->new RefundPart(rs.getObject("id",UUID.class),rs.getObject("collection_id",UUID.class),rs.getString("channel"),rs.getLong("amount_cents"),rs.getBoolean("completed"),rs.getString("receipt_reference"),rs.getString("channel_name")),scope.tenantId().value(),id);
    }
    public void completePart(StoreScope scope,RefundPart part,String reference) {
        var original=collection(scope,part.collectionId());
        String receiptKey=com.rpb.reservation.wallet.domain.WalletTokens.hash(com.rpb.reservation.wallet.domain.WalletMoney.channelType(original.channel())+"\u0000"+original.collectionAccount()+"\u0000"+reference.toUpperCase(Locale.ROOT));
        jdbc.update("update wallet_refund_parts set completed=true,receipt_reference=?,receipt_key=?,updated_at=now() where tenant_id=? and id=? and completed=false",reference,receiptKey,scope.tenantId().value(),part.id());
        jdbc.update("update wallet_collections set refunded_cents=refunded_cents+? where tenant_id=? and id=?",part.amountCents(),scope.tenantId().value(),part.collectionId());
    }
    public void status(StoreScope scope,UUID id,String status,UUID actor) {
        jdbc.update("update wallet_refunds set status=?,confirmed_by=?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",status,actor,scope.tenantId().value(),scope.storeId().value(),id);
    }
    public void components(StoreScope s,UUID id,long p,long g){jdbc.update("update wallet_refunds set principal_cents=?,bonus_cents=? where tenant_id=? and id=?",p,g,s.tenantId().value(),id);}
    private Refund row(ResultSet rs)throws SQLException {
        return new Refund(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getObject("topup_id",UUID.class),rs.getObject("bill_id",UUID.class),rs.getLong("amount_cents"),rs.getLong("wallet_cents"),rs.getString("status"),rs.getString("reason"),rs.getInt("version"),rs.getTimestamp("created_at").toInstant(),rs.getObject("exception_collection_id",UUID.class),rs.getLong("principal_cents"),rs.getLong("bonus_cents"));
    }
}
