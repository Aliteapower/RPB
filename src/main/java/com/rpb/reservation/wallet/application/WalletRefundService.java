package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@Service
@Transactional(readOnly=true)
public class WalletRefundService {
    private static final UUID WALLET_TENDER=new UUID(0,0);
    private final WalletAccountRepository accounts;private final WalletLedgerRepository ledger;private final WalletCommandRepository commands;
    private final WalletTopupRepository topups;private final WalletBillRepository bills;private final WalletRefundRepository refunds;private final WalletAccess access;
    public WalletRefundService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,WalletTopupRepository topups,WalletBillRepository bills,WalletRefundRepository refunds,WalletAccess access) {
        this.accounts=accounts;this.ledger=ledger;this.commands=commands;this.topups=topups;this.bills=bills;this.refunds=refunds;this.access=access;
    }
    @Transactional
    public RefundDetail create(StoreScope scope,CurrentActor actor,RefundCreate c) {
        access.staff(scope,actor,"wallet.refund.manage",false);
        if(c==null || c.sourceId()==null || !Set.of("top_up","bill","collection_exception").contains(Objects.toString(c.sourceType(),"")))throw WalletException.invalid();
        long amount=WalletMoney.cents(c.amount());String reason=WalletMoney.text(c.reason());
        return commands.run(scope,actor.actorId(),"refund.create",c.idempotencyKey(),c,RefundDetail.class,()->{
            boolean exception="collection_exception".equals(c.sourceType());var receipt=exception?refunds.collection(scope,c.sourceId()):null;
            if(exception && !receipt.exception())throw WalletException.invalid();
            boolean topup=exception?receipt.topupId()!=null:"top_up".equals(c.sourceType());UUID source=exception?(topup?receipt.topupId():receipt.billId()):c.sourceId();
            UUID accountId=topup?topups.get(scope,source,false).accountId():bills.get(scope,source,false).accountId();
            var a=exception?accounts.lockForExternalRecovery(scope,accountId):accounts.account(scope,accountId,true);if(!exception)accounts.requireActive(a);
            var t=topup?topups.get(scope,source,true):null;var b=topup?null:bills.get(scope,source,true);
            if(topup && !exception && !"credited".equals(t.status()))throw WalletException.conflict("只有已入账充值可以按本金退回；异常实收请选异常凭证退款");
            Map<UUID,Long> available=new LinkedHashMap<>();
            if(!topup && !exception)available.put(WALLET_TENDER,b.walletPaidCents()-refunds.walletAllocated(scope,b.id()));
            var collections=refunds.collections(scope,topup?t.id():null,topup?null:b.id());
            for(var collection:collections)if(exception?collection.id().equals(receipt.id()):!collection.exception())available.put(collection.id(),collection.amountCents()-refunds.collectionAllocated(scope.tenantId().value(),collection.id()));
            var split=WalletMoney.allocate(amount,available);long wallet=exception?0:topup?amount:split.getOrDefault(WALLET_TENDER,0L);
            if(topup && !exception && a.availableCents()<amount)throw new WalletException("WALLET_REFUND_EXCEEDS_LIMIT",409,"可用余额不足以退回该充值，请先处理已消费本金");
            boolean hasExternal=split.entrySet().stream().anyMatch(e->!WALLET_TENDER.equals(e.getKey()) && e.getValue()>0);
            var refund=refunds.create(scope,a,topup?t.id():null,topup?null:b.id(),amount,wallet,reason,actor.actorId(),hasExternal?"pending":"completed");
            if(exception)refunds.exception(scope,refund.id(),receipt.id());
            for(var part:split.entrySet())if(!WALLET_TENDER.equals(part.getKey()) && part.getValue()>0)refunds.addPart(scope,refund.id(),part.getKey(),part.getValue());
            if(topup && !exception)accounts.update(a,a.balanceCents(),a.reservedCents()+amount);
            else if(!exception) {
                if(wallet>0) {long after=Math.addExact(a.balanceCents(),wallet);accounts.update(a,after,a.reservedCents());ledger.append(scope,a,after,"bill_refund",refund.id(),actor.actorId(),ledger.original(scope.tenantId().value(),"bill_debit",b.id()));}
                updateBill(scope,b);
            }
            commands.audit(scope,actor.actorId(),"refund.create",refund.id(),Map.of("sourceId",c.sourceId(),"amountCents",amount,"reason",reason));return detail(scope,refund.id());
        });
    }
    @Transactional
    public RefundDetail confirmPart(StoreScope scope,CurrentActor actor,UUID id,UUID partId,String receipt,String key) {
        access.staff(scope,actor,"wallet.refund.manage",false);String reference=WalletMoney.text(receipt);
        return commands.run(scope,actor.actorId(),"refund.confirm",key,Map.of("id",id,"partId",partId,"reference",reference),RefundDetail.class,()->{
            var initial=refunds.get(scope,id,false);var a=initial.exceptionCollectionId()!=null?accounts.lockForExternalRecovery(scope,initial.accountId()):accounts.account(scope,initial.accountId(),true);lockSource(scope,initial);
            var r=refunds.get(scope,id,true);if("cancelled".equals(r.status()))throw WalletException.conflict("退款已取消");
            var part=refunds.parts(scope,id).stream().filter(p->p.id().equals(partId)).findFirst().orElseThrow(WalletException::missing);
            if(!part.completed())refunds.completePart(scope,part,reference);
            if(!"completed".equals(r.status()) && refunds.parts(scope,id).stream().allMatch(RefundPart::completed)) {
                if(r.topupId()!=null && r.exceptionCollectionId()==null) {
                    if(a.reservedCents()<r.amountCents())throw WalletException.conflict("退款冻结状态异常");
                    long after=a.balanceCents()-r.amountCents();accounts.update(a,after,a.reservedCents()-r.amountCents());
                    ledger.append(scope,a,after,"topup_refund",r.id(),actor.actorId(),ledger.original(scope.tenantId().value(),"topup_credit",r.topupId()));
                    topups.refunded(scope,r.topupId(),r.amountCents());
                }
                refunds.status(scope,id,"completed",actor.actorId());
                if(r.exceptionCollectionId()!=null && r.topupId()!=null && "exception".equals(topups.get(scope,r.topupId(),false).status())
                    && refunds.collections(scope,r.topupId(),null).stream().allMatch(fact->fact.exception() && fact.refundedCents()==fact.amountCents())) {
                    topups.status(scope,r.topupId(),"cancelled");
                    commands.audit(scope,actor.actorId(),"topup.exception_refunded",r.topupId(),Map.of("refundId",id));
                }
            }
            if(r.billId()!=null && r.exceptionCollectionId()==null)updateBill(scope,bills.get(scope,r.billId(),false));
            commands.audit(scope,actor.actorId(),"refund.confirm_part",id,Map.of("partId",partId,"reference",reference));return detail(scope,id);
        });
    }
    @Transactional
    public RefundDetail cancel(StoreScope scope,CurrentActor actor,UUID id,String key) {
        access.staff(scope,actor,"wallet.refund.manage",false);
        return commands.run(scope,actor.actorId(),"refund.cancel",key,id,RefundDetail.class,()->{
            var initial=refunds.get(scope,id,false);var a=initial.exceptionCollectionId()!=null?accounts.lockForExternalRecovery(scope,initial.accountId()):accounts.account(scope,initial.accountId(),true);lockSource(scope,initial);var r=refunds.get(scope,id,true);
            if(!"pending".equals(r.status()) || refunds.parts(scope,id).stream().anyMatch(RefundPart::completed) || (r.billId()!=null && r.walletCents()>0))throw WalletException.conflict("已有退款完成，不能取消或释放冻结");
            if(r.topupId()!=null && r.exceptionCollectionId()==null)accounts.update(a,a.balanceCents(),a.reservedCents()-r.amountCents());
            refunds.status(scope,id,"cancelled",actor.actorId());if(r.billId()!=null && r.exceptionCollectionId()==null)updateBill(scope,bills.get(scope,r.billId(),false));
            commands.audit(scope,actor.actorId(),"refund.cancel",id,Map.of());return detail(scope,id);
        });
    }
    public List<Refund> list(StoreScope scope,CurrentActor actor) {access.staff(scope,actor,"wallet.refund.manage",false);return refunds.list(scope);}
    public RefundDetail get(StoreScope scope,CurrentActor actor,UUID id) {access.staff(scope,actor,"wallet.refund.manage",false);return detail(scope,id);}
    private RefundDetail detail(StoreScope scope,UUID id) {return new RefundDetail(refunds.get(scope,id,false),refunds.parts(scope,id));}
    private void lockSource(StoreScope scope,Refund r) {if(r.topupId()!=null)topups.get(scope,r.topupId(),true);else bills.get(scope,r.billId(),true);}
    private void updateBill(StoreScope scope,Bill bill) {
        long completed=refunds.completedAmount(scope,bill.id());boolean pending=refunds.hasPending(scope,bill.id());
        String status=pending?"refund_pending":completed==0?"none":completed==bill.walletPaidCents()+bill.externalPaidCents()?"refunded":"partially_refunded";
        // An initiated refund closes further collection; it never recreates an amount due.
        bills.refundStatus(scope,bill.id(),status,true);
    }
}
