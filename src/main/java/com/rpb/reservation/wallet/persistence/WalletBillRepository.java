package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.WalletHistoryQuery;
import com.rpb.reservation.wallet.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletBillRepository {
    private final JdbcTemplate jdbc;
    public WalletBillRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Bill create(StoreScope scope,Account a,long amount,UUID actor) {
        return jdbc.query("insert into wallet_bills(tenant_id,program_id,account_id,store_id,amount_cents,code_hash,created_by) values(?,?,?,?,?,?,?) returning *",
            (rs,n)->row(rs),scope.tenantId().value(),a.programId(),a.id(),scope.storeId().value(),amount,WalletTokens.hash(WalletTokens.issue("WB")),actor).getFirst();
    }
    public Bill get(StoreScope scope,UUID id,boolean lock) {
        return jdbc.query("select * from wallet_bills where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public List<Bill> list(StoreScope scope) {
        return list(scope,new WalletHistoryQuery(0,"all"));
    }
    public List<Bill> list(StoreScope scope,WalletHistoryQuery query) {
        return jdbc.query("select b.* from wallet_bills b where b.tenant_id=? and b.store_id=?"+filter(query)+" order by b.created_at desc,b.id limit 100 offset ?",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),query.offset());
    }
    public List<Bill> customerHistory(StoreScope scope,UUID customer,WalletHistoryQuery query) {
        return jdbc.query("select b.* from wallet_bills b join wallet_accounts a on a.id=b.account_id and a.tenant_id=b.tenant_id where b.tenant_id=? and b.store_id=? and a.customer_id=?"+filter(query)+" order by b.created_at desc,b.id limit 100 offset ?",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),customer,query.offset());
    }
    private String filter(WalletHistoryQuery query) {
        return switch(query.status()) {
            case "pending" -> " and not b.closed and b.status<>'paid'";
            case "completed" -> " and not b.closed and b.status='paid'";
            case "closed" -> " and b.closed";
            default -> "";
        };
    }
    public List<Bill> customerPending(StoreScope scope,UUID customer) {
        return jdbc.query("select b.* from wallet_bills b join wallet_accounts a on a.id=b.account_id and a.tenant_id=b.tenant_id where b.tenant_id=? and b.store_id=? and a.customer_id=? and b.closed=false and b.status<>'paid' order by b.created_at desc,b.id limit 100",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),customer);
    }
    public void walletApplied(StoreScope scope,Bill bill,long cents) {
        String status=WalletMoney.status(bill.amountCents(),cents,bill.externalPaidCents());
        int n=jdbc.update("update wallet_bills set wallet_paid_cents=?,principal_paid_cents=?,bonus_paid_cents=0,wallet_applied=true,status=?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=? and version=? and wallet_applied=false and closed=false",
            cents,cents,status,scope.tenantId().value(),scope.storeId().value(),bill.id(),bill.version());
        if(n!=1) throw WalletException.conflict("账单已更新，请勿重复扣款");
    }
    public void externalPaid(StoreScope scope,Bill bill,long cents) {
        String status=WalletMoney.status(bill.amountCents(),bill.walletPaidCents(),bill.externalPaidCents()+cents);
        jdbc.update("update wallet_bills set external_paid_cents=external_paid_cents+?,status=?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",cents,status,scope.tenantId().value(),scope.storeId().value(),bill.id());
    }
    public void refundStatus(StoreScope scope,UUID id,String status,boolean closed) {
        jdbc.update("update wallet_bills set refund_status=?,closed=closed or ?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",status,closed,scope.tenantId().value(),scope.storeId().value(),id);
    }
    public void close(StoreScope scope,UUID id) {
        jdbc.update("update wallet_bills set closed=true,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",scope.tenantId().value(),scope.storeId().value(),id);
    }
    public void issueCode(StoreScope scope,UUID id,String hash,Instant expires) {
        jdbc.update("update wallet_bills set code_hash=?,code_expires_at=?,updated_at=now() where tenant_id=? and store_id=? and id=?",hash,Timestamp.from(expires),scope.tenantId().value(),scope.storeId().value(),id);
    }
    public Bill byCode(StoreScope scope,String hash,Instant now) {
        return jdbc.query("select * from wallet_bills where tenant_id=? and store_id=? and code_hash=? and code_expires_at>? and closed=false",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),hash,Timestamp.from(now)).stream().findFirst().orElseThrow(WalletException::missing);
    }
    private Bill row(ResultSet rs)throws SQLException {
        return new Bill(rs.getObject("id",UUID.class),rs.getObject("program_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getLong("amount_cents"),rs.getLong("wallet_paid_cents"),rs.getLong("external_paid_cents"),rs.getString("status"),rs.getBoolean("wallet_applied"),rs.getBoolean("closed"),rs.getString("refund_status"),rs.getInt("version"),rs.getTimestamp("created_at").toInstant(),rs.getLong("principal_paid_cents"),rs.getLong("bonus_paid_cents"));
    }
}
