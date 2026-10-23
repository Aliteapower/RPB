package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletReconciliationService {
    private final WalletAccess access;private final WalletDirectory directory;private final WalletAccountRepository accounts;private final WalletReconciliationRepository reports;
    public WalletReconciliationService(WalletAccess access,WalletDirectory directory,WalletAccountRepository accounts,WalletReconciliationRepository reports) {this.access=access;this.directory=directory;this.accounts=accounts;this.reports=reports;}
    @Transactional(readOnly=true)
    public WalletViews.Reconciliation query(StoreScope scope,CurrentActor actor,LocalDate start,LocalDate end) {
        access.staff(scope,actor,"wallet.reconcile.view",false);
        if(start==null || end==null || end.isBefore(start) || end.isAfter(start.plusDays(366)))throw WalletException.invalid();
        var zone=ZoneId.of(directory.store(scope).timezone());
        return reports.query(scope,accounts.requireProgram(scope,false),start.atStartOfDay(zone).toInstant(),end.plusDays(1).atStartOfDay(zone).toInstant());
    }
    @Transactional(readOnly=true)
    public WalletViews.Reconciliation query(StoreScope scope,CurrentActor actor,LocalDate start,LocalDate end,String group) {
        var own=query(scope,actor,start,end);if("store".equals(group))return own;if(!"issuer".equals(group))throw WalletException.invalid();
        var program=accounts.requireProgram(scope,false);var storeIds=accounts.programStores(scope.tenantId().value(),program.id());
        if(storeIds.stream().anyMatch(id->!actor.canAccessStore(id)))throw WalletException.forbidden();
        var zone=ZoneId.of(directory.store(scope).timezone());var from=start.atStartOfDay(zone).toInstant();var to=end.plusDays(1).atStartOfDay(zone).toInstant();
        var rows=storeIds.stream().map(id->reports.query(new StoreScope(scope.tenantId(),id),program,from,to)).toList();
        return new WalletViews.Reconciliation(sum(rows,WalletViews.Reconciliation::topupCashCents),sum(rows,WalletViews.Reconciliation::topupPaynowCents),sum(rows,WalletViews.Reconciliation::topupCardCents),sum(rows,WalletViews.Reconciliation::walletSpentCents),sum(rows,WalletViews.Reconciliation::externalBillCents),sum(rows,WalletViews.Reconciliation::refundsCents),sum(rows,WalletViews.Reconciliation::pendingTopupCents),sum(rows,WalletViews.Reconciliation::outstandingBillCents),own.programBalanceCents(),own.programReservedCents(),sum(rows,WalletViews.Reconciliation::exceptionReceivedCents),sum(rows,WalletViews.Reconciliation::creditedTopupCents),sum(rows,WalletViews.Reconciliation::pendingRefundCents),own.ledgerMismatchAccounts(),rows.stream().flatMap(r->r.collections().stream()).sorted(java.util.Comparator.comparing(WalletViews.Collection::createdAt).reversed()).limit(1000).toList(),
            sum(rows,WalletViews.Reconciliation::creditedPrincipalCents),sum(rows,WalletViews.Reconciliation::creditedBonusCents),sum(rows,WalletViews.Reconciliation::spentPrincipalCents),sum(rows,WalletViews.Reconciliation::spentBonusCents),
            sum(rows,WalletViews.Reconciliation::returnedPrincipalCents),sum(rows,WalletViews.Reconciliation::returnedBonusCents),sum(rows,WalletViews.Reconciliation::cashPrincipalRefundCents),sum(rows,WalletViews.Reconciliation::revokedBonusCents),
            own.programPrincipalBalanceCents(),own.programBonusBalanceCents(),own.programPrincipalReservedCents(),own.programBonusReservedCents(),own.componentMismatchAccounts(),own.fundingMismatchAccounts());
    }
    private long sum(java.util.List<WalletViews.Reconciliation> rows,java.util.function.ToLongFunction<WalletViews.Reconciliation> value) {return rows.stream().mapToLong(value).sum();}
}
