package com.rpb.reservation.wallet.funding.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.WalletViews.*;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.domain.FundingAllocation;
import com.rpb.reservation.wallet.funding.persistence.WalletFundingRepository;
import static com.rpb.reservation.wallet.funding.persistence.WalletFundingRepository.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** One component posting boundary shared by employee/customer debit, credit and original-order recovery. */
@Service
public class WalletFundingService {
    private final WalletFundingRepository lots;private final WalletAccountRepository accounts;private final WalletLedgerRepository ledger;
    public WalletFundingService(WalletFundingRepository lots,WalletAccountRepository accounts,WalletLedgerRepository ledger){this.lots=lots;this.accounts=accounts;this.ledger=ledger;}
    public record Split(long principalCents,long bonusCents,List<Portion> allocations){public long totalCents(){return principalCents+bonusCents;}}
    public Split preview(StoreScope s,Account a,long amount){return plan(s,a,amount,false);}
    private Split plan(StoreScope s,Account a,long amount,boolean lock){
        long left=amount;var parts=new ArrayList<Portion>();
        for(var l:lots.lots(s,a.id(),lock)){long d=Math.min(left,l.availableP()+l.availableG());if(d==0)continue;var split=FundingAllocation.debit(l.policy(),l.initialP(),l.initialG(),l.spentP(),l.spentG(),l.availableP(),l.availableG(),d);parts.add(new Portion(l.id(),split.principal(),split.bonus(),null));left-=d;if(left==0)break;}
        if(left!=0)throw new WalletException("WALLET_LEDGER_MISMATCH",409,"资金批次与账户可用余额不一致");return sum(parts);
    }
    public void credit(StoreScope s,Account a,Topup t,UUID actor){var l=lots.credit(s,a,t);post(s,a,"topup_credit",t.id(),actor,null,List.of(new Portion(l.id(),t.amountCents(),t.bonusCents(),null)),1,0,0);}
    public Split debit(StoreScope s,Account a,long amount,UUID bill,UUID actor){
        if(amount==0)return new Split(0,0,List.of());
        var split=plan(s,a,amount,true);for(var p:split.allocations()){var l=lots.lot(s,a.id(),p.lotId(),false);lots.debit(s,l,p.principal(),p.bonus());}
        post(s,a,"bill_debit",bill,actor,null,split.allocations(),-1,0,0);return split;
    }
    public void billComponents(StoreScope s,UUID bill,Split split){lots.billComponents(s,bill,split.principalCents(),split.bonusCents());}
    public Split previewBillRefund(StoreScope s,Account a,UUID bill,long amount){
        var originals=lots.originals(s,a.id(),bill);if(originals.isEmpty())return new Split(amount,0,List.of());
        var parts=new ArrayList<Portion>();long left=amount;
        for(var o:originals){long d=Math.min(left,o.principal()+o.bonus()-o.returnedP()-o.returnedG());if(d==0)continue;var v=FundingAllocation.refund(o.principal(),o.bonus(),o.returnedP(),o.returnedG(),d);parts.add(new Portion(o.lotId(),v.principal(),v.bonus(),o.id()));left-=d;if(left==0)break;}
        if(left!=0)throw WalletException.conflict("退款超过原钱包扣款");return sum(parts);
    }
    public Split refundBill(StoreScope s,Account a,UUID bill,UUID refund,long amount,UUID actor){
        var originals=lots.originals(s,a.id(),bill);var parts=new ArrayList<Portion>();long left=amount;
        if(originals.isEmpty()) {var l=lots.legacy(s,a);lots.restoreHistorical(s,l,amount);parts.add(new Portion(l.id(),amount,0,null));left=0;}
        else {lots.lots(s,a.id(),true);for(var o:originals){long d=Math.min(left,o.principal()+o.bonus()-o.returnedP()-o.returnedG());if(d==0)continue;var split=FundingAllocation.refund(o.principal(),o.bonus(),o.returnedP(),o.returnedG(),d);var l=lots.lot(s,a.id(),o.lotId(),false);lots.restore(s,l,split.principal(),split.bonus());parts.add(new Portion(l.id(),split.principal(),split.bonus(),o.id()));left-=d;if(left==0)break;}}
        if(left!=0)throw WalletException.conflict("退款超过原钱包扣款");
        post(s,a,"bill_refund",refund,actor,ledger.original(s.tenantId().value(),"bill_debit",bill),parts,1,0,0);return sum(parts);
    }
    public Split topupRefundPreview(StoreScope s,Account a,UUID topup,long principal){
        var l=lots.topupLot(s,a.id(),topup,false);
        if(principal>l.availableP())throw new WalletException("WALLET_REFUND_EXCEEDS_LIMIT",409,"该充值批次可退本金不足");
        if(l.initialG()>0 && (principal!=l.availableP() || l.reservedP()+l.reservedG()>0))throw new WalletException("WALLET_FUNDING_REFUND_NOT_ALLOWED",409,"活动充值需整批退出当前剩余权益，先处理已有冻结");
        long bonus=l.initialG()>0?l.availableG():0;return new Split(principal,bonus,List.of(new Portion(l.id(),principal,bonus,null)));
    }
    public long topupRemainingPrincipal(StoreScope s,Account a,UUID topup){return lots.topupLot(s,a.id(),topup,false).availableP();}
    public List<Lot> lots(StoreScope s,Account a){return lots.lots(s,a.id(),false);}
    public void reserve(StoreScope s,Account a,UUID refund,Split split){
        for(var p:split.allocations()){var l=lots.lot(s,a.id(),p.lotId(),true);if(l.availableP()<p.principal()||l.availableG()<p.bonus())throw WalletException.conflict("批次可用余额已变化");lots.reserve(s,l,p.principal(),p.bonus());lots.reservation(s,a,refund,p);}
        accounts.updateComponents(a,a.principalBalanceCents(),a.bonusBalanceCents(),a.principalReservedCents()+split.principalCents(),a.bonusReservedCents()+split.bonusCents());
    }
    public void finish(StoreScope s,Account a,UUID refund,UUID topup,UUID actor){
        var parts=lots.reservations(s,refund);if(parts.isEmpty())throw WalletException.conflict("退款冻结分项缺失");var split=sum(parts);
        for(var p:parts)lots.finish(s,lots.lot(s,a.id(),p.lotId(),true),p.principal(),p.bonus());
        lots.reservationStatus(s,refund,"completed");post(s,a,"topup_refund",refund,actor,ledger.original(s.tenantId().value(),"topup_credit",topup),parts,-1,-split.principalCents(),-split.bonusCents());
    }
    public void release(StoreScope s,Account a,UUID refund){var parts=lots.reservations(s,refund);var split=sum(parts);for(var p:parts)lots.release(s,lots.lot(s,a.id(),p.lotId(),true),p.principal(),p.bonus());lots.reservationStatus(s,refund,"cancelled");accounts.updateComponents(a,a.principalBalanceCents(),a.bonusBalanceCents(),a.principalReservedCents()-split.principalCents(),a.bonusReservedCents()-split.bonusCents());}
    public Account exitBonus(StoreScope s,Account a,UUID lotId,String reason,UUID actor){var l=lots.lot(s,a.id(),lotId,true);if(l.availableP()!=0 || l.reservedP()+l.reservedG()!=0 || l.availableG()==0)throw WalletException.conflict("只能退出无本金且未冻结的剩余赠送");long g=l.availableG();var id=lots.exit(s,a,l,WalletMoney.text(reason),actor,g);lots.revoke(s,l,g);post(s,a,"bonus_revoke",id,actor,null,List.of(new Portion(l.id(),0,g,null)),-1,0,0);return accounts.account(s,a.id(),false);}
    private void post(StoreScope s,Account a,String kind,UUID source,UUID actor,UUID original,List<Portion> parts,int direction,long rp,long rg){var split=sum(parts);long p=Math.addExact(a.principalBalanceCents(),direction*split.principalCents()),g=Math.addExact(a.bonusBalanceCents(),direction*split.bonusCents());accounts.updateComponents(a,p,g,a.principalReservedCents()+rp,a.bonusReservedCents()+rg);ledger.append(s,a,p+g,kind,source,actor,original);var entry=ledger.original(s.tenantId().value(),kind,source);lots.allocations(s,a,entry,switch(kind){case "topup_credit"->"credit";case "bill_debit"->"debit";default->kind;},parts);}
    private Split sum(List<Portion> parts){long p=0,g=0;for(var x:parts){p=Math.addExact(p,x.principal());g=Math.addExact(g,x.bonus());}return new Split(p,g,List.copyOf(parts));}
}
