package com.rpb.reservation.wallet.funding.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.WalletViews.Account;
import com.rpb.reservation.wallet.application.WalletViews.Topup;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletFundingRepository {
    private final JdbcTemplate jdbc;
    public WalletFundingRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Lot(UUID id,UUID accountId,long initialP,long initialG,long principal,long bonus,long reservedP,long reservedG,long spentP,long spentG,String policy,boolean legacy,int version) {
        public long availableP(){return principal-reservedP;}public long availableG(){return bonus-reservedG;}
    }
    public record Portion(UUID lotId,long principal,long bonus,UUID original) {public long total(){return principal+bonus;}}
    public record Original(UUID id,UUID lotId,long principal,long bonus,long returnedP,long returnedG){}
    public List<Lot> lots(StoreScope s,UUID account,boolean lock){return jdbc.query("select * from wallet_funding_lots where tenant_id=? and account_id=? order by credit_sequence,id"+(lock?" for update":""),(r,n)->lot(r),s.tenantId().value(),account);}
    public Lot topupLot(StoreScope s,UUID account,UUID topup,boolean lock){return jdbc.query("select * from wallet_funding_lots where tenant_id=? and account_id=? and (topup_id=? or legacy) order by legacy limit 1"+(lock?" for update":""),(r,n)->lot(r),s.tenantId().value(),account,topup).stream().findFirst().orElseThrow(WalletException::missing);}
    public Lot lot(StoreScope s,UUID account,UUID id,boolean lock){return jdbc.query("select * from wallet_funding_lots where tenant_id=? and account_id=? and id=?"+(lock?" for update":""),(r,n)->lot(r),s.tenantId().value(),account,id).stream().findFirst().orElseThrow(WalletException::missing);}
    public Lot credit(StoreScope s,Account a,Topup t){return jdbc.query("insert into wallet_funding_lots(tenant_id,program_id,account_id,topup_id,policy,initial_principal,initial_bonus,principal,bonus,credit_sequence) values(?,?,?,?,?,?,?,?,?,?) returning *",(r,n)->lot(r),s.tenantId().value(),a.programId(),a.id(),t.id(),t.deductionPolicy(),t.amountCents(),t.bonusCents(),t.amountCents(),t.bonusCents(),a.version()+1).getFirst();}
    public Lot legacy(StoreScope s,Account a){
        jdbc.update("insert into wallet_funding_lots(tenant_id,program_id,account_id,legacy,policy,initial_principal,initial_bonus,principal,bonus,credit_sequence) values(?,?,?,true,'principal_first',0,0,0,0,-1) on conflict(tenant_id,account_id) where legacy do nothing",s.tenantId().value(),a.programId(),a.id());
        return lots(s,a.id(),true).stream().filter(Lot::legacy).findFirst().orElseThrow(WalletException::missing);
    }
    public void debit(StoreScope s,Lot l,long p,long g){change(s,l,-p,-g,0,0,p,g,0,0);}
    public void restore(StoreScope s,Lot l,long p,long g){
        change(s,l,p,g,0,0,-p,-g,0,0);
    }
    public void restoreHistorical(StoreScope s,Lot l,long p){
        if(!l.legacy())throw WalletException.invalid();
        int n=jdbc.update("update wallet_funding_lots set principal=principal+?,initial_principal=initial_principal+?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",p,p,s.tenantId().value(),l.id(),l.version());
        if(n!=1)throw WalletException.conflict("历史本金池已变化");
    }
    public void reserve(StoreScope s,Lot l,long p,long g){change(s,l,0,0,p,g,0,0,0,0);}
    public void finish(StoreScope s,Lot l,long p,long g){change(s,l,-p,-g,-p,-g,0,0,p,g);}
    public void release(StoreScope s,Lot l,long p,long g){change(s,l,0,0,-p,-g,0,0,0,0);}
    public void revoke(StoreScope s,Lot l,long g){change(s,l,0,-g,0,0,0,0,0,g);}
    private void change(StoreScope s,Lot l,long p,long g,long rp,long rg,long sp,long sg,long fp,long vg){
        int n=jdbc.update("update wallet_funding_lots set principal=principal+?,bonus=bonus+?,reserved_principal=reserved_principal+?,reserved_bonus=reserved_bonus+?,spent_principal=spent_principal+?,spent_bonus=spent_bonus+?,refunded_principal=refunded_principal+?,revoked_bonus=revoked_bonus+?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",p,g,rp,rg,sp,sg,fp,vg,s.tenantId().value(),l.id(),l.version());
        if(n!=1)throw new WalletException("WALLET_ALLOCATION_STALE",409,"资金批次已变化，请重新核对");
    }
    public void allocations(StoreScope s,Account a,UUID entry,String action,List<Portion> parts){for(var p:parts)if(p.total()>0)jdbc.update("insert into wallet_entry_allocations(tenant_id,program_id,account_id,entry_id,lot_id,action,principal_cents,bonus_cents,original_allocation_id) values(?,?,?,?,?,?,?,?,?)",s.tenantId().value(),a.programId(),a.id(),entry,p.lotId(),action,p.principal(),p.bonus(),p.original());}
    public List<Original> originals(StoreScope s,UUID account,UUID bill){return jdbc.query("select x.*,coalesce((select sum(z.principal_cents) from wallet_entry_allocations z where z.tenant_id=x.tenant_id and z.original_allocation_id=x.id),0) returned_p,coalesce((select sum(z.bonus_cents) from wallet_entry_allocations z where z.tenant_id=x.tenant_id and z.original_allocation_id=x.id),0) returned_g from wallet_entry_allocations x join wallet_entries e on e.id=x.entry_id and e.tenant_id=x.tenant_id join wallet_funding_lots l on l.id=x.lot_id and l.tenant_id=x.tenant_id where x.tenant_id=? and x.account_id=? and x.action='debit' and e.source_id=? order by l.credit_sequence,x.id",(r,n)->new Original(r.getObject("id",UUID.class),r.getObject("lot_id",UUID.class),r.getLong("principal_cents"),r.getLong("bonus_cents"),r.getLong("returned_p"),r.getLong("returned_g")),s.tenantId().value(),account,bill);}
    public void reservation(StoreScope s,Account a,UUID refund,Portion p){jdbc.update("insert into wallet_refund_allocations(tenant_id,refund_id,lot_id,program_id,account_id,principal_cents,bonus_cents) values(?,?,?,?,?,?,?)",s.tenantId().value(),refund,p.lotId(),a.programId(),a.id(),p.principal(),p.bonus());}
    public List<Portion> reservations(StoreScope s,UUID refund){return jdbc.query("select * from wallet_refund_allocations where tenant_id=? and refund_id=? and status='reserved' order by lot_id for update",(r,n)->new Portion(r.getObject("lot_id",UUID.class),r.getLong("principal_cents"),r.getLong("bonus_cents"),null),s.tenantId().value(),refund);}
    public void reservationStatus(StoreScope s,UUID refund,String status){jdbc.update("update wallet_refund_allocations set status=? where tenant_id=? and refund_id=? and status='reserved'",status,s.tenantId().value(),refund);}
    public void billComponents(StoreScope s,UUID bill,long p,long g){jdbc.update("update wallet_bills set principal_paid_cents=?,bonus_paid_cents=? where tenant_id=? and id=?",p,g,s.tenantId().value(),bill);}
    public UUID exit(StoreScope s,Account a,Lot lot,String reason,UUID actor,long bonus){return jdbc.queryForObject("insert into wallet_bonus_exits(tenant_id,lot_id,account_id,program_id,bonus_cents,reason,created_by) values(?,?,?,?,?,?,?) returning id",UUID.class,s.tenantId().value(),lot.id(),a.id(),a.programId(),bonus,reason,actor);}
    private Lot lot(ResultSet r)throws SQLException{return new Lot(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getLong("initial_principal"),r.getLong("initial_bonus"),r.getLong("principal"),r.getLong("bonus"),r.getLong("reserved_principal"),r.getLong("reserved_bonus"),r.getLong("spent_principal"),r.getLong("spent_bonus"),r.getString("policy"),r.getBoolean("legacy"),r.getInt("version"));}
}
