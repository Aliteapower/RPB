package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platform.application.SharedStoreIdentityQuery;
import com.rpb.reservation.platformbilling.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Selected-store lifecycle belongs to platform billing, with the same subscription/price/event/entitlement ports. */
@Service
public class SelectedStoreBillingService {
    private final ProductSubscriptionRepository subscriptions;private final ProductSubscriptionEventRepository events;
    private final ProductSubscriptionItemRepository items;private final TenantProductEntitlementSyncGateway entitlements;
    private final SubscriptionQuoteService quotes;private final SelectedBillingCommandRepository commands;
    private final SharedStoreIdentityQuery stores;private final Clock clock;
    public SelectedStoreBillingService(ProductSubscriptionRepository subscriptions,ProductSubscriptionEventRepository events,ProductSubscriptionItemRepository items,TenantProductEntitlementSyncGateway entitlements,SubscriptionQuoteService quotes,SelectedBillingCommandRepository commands,SharedStoreIdentityQuery stores,Clock clock) {
        this.subscriptions=subscriptions;this.events=events;this.items=items;this.entitlements=entitlements;this.quotes=quotes;this.commands=commands;this.stores=stores;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public SubscriptionQuote quote(UUID tenant,ProductSubscriptionCommand c) {
        var selected=selected(tenant,c);return quotes.quote(c.appKey(),new BillingDuration(c.billingCycle(),c.durationCount()),selected.size(),c.amount(),"SGD");
    }
    @Transactional
    public ProductSubscriptionMutationResult purchase(UUID tenant,ProductSubscriptionCommand c,PlatformBillingOperator operator) {
        subscriptions.lockTenantBilling(tenant);
        var selected=selected(tenant,c);
        return commands.run(tenant,"wallet.purchase",c.idempotencyKey(),c,()->{
            if(subscriptions.findByTenantIdAndAppKey(tenant,"wallet").isPresent())throw error(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
            var q=quote(tenant,c);var period=period(null,c);
            var s=subscriptions.create(new ProductSubscriptionDraft(tenant,"wallet",c.billingCycle(),"active",period.periodStart(),period.periodEnd(),q.finalAmount(),"SGD",c.paymentNote(),operator.userId()));
            insert(s,selected,c,q,period);return finish(s,c,operator,q,"purchase",selected);
        });
    }
    @Transactional
    public ProductSubscriptionMutationResult renew(UUID tenant,UUID id,ProductSubscriptionCommand c,PlatformBillingOperator operator) {return mutate(tenant,id,c,operator,false);}
    @Transactional
    public ProductSubscriptionMutationResult addStores(UUID tenant,UUID id,ProductSubscriptionCommand c,PlatformBillingOperator operator) {return mutate(tenant,id,c,operator,true);}
    private ProductSubscriptionMutationResult mutate(UUID tenant,UUID id,ProductSubscriptionCommand c,PlatformBillingOperator operator,boolean add) {
        subscriptions.lockTenantBilling(tenant);
        var selected=selected(tenant,c);String operation=add?"wallet.add_stores":"wallet.renew";
        return commands.run(tenant,operation,c.idempotencyKey(),Map.of("id",id,"command",c),()->{
            commands.lock(tenant,id);var s=current(tenant,id);
            if(c.version()==null || c.version()!=s.version())throw error(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
            var existing=items.listByTenantId(tenant).stream().filter(i->i.subscriptionId().equals(id)).toList();
            var selectedIds=selected.stream().map(BillableStore::storeId).toList();
            if(add && existing.stream().anyMatch(i->selectedIds.contains(i.storeId())))throw error(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
            if(!add && selectedIds.stream().anyMatch(store->existing.stream().noneMatch(i->i.storeId().equals(store))))throw error(PlatformBillingServiceErrorCode.SUBSCRIPTION_ITEM_NOT_FOUND);
            var q=quote(tenant,c);var amounts=allocate(q.finalAmount(),selected.size());
            if(add)insert(s,selected,c,q,period(null,c));
            else for(int n=0;n<selected.size();n++) {
                UUID store=selected.get(n).storeId();var item=existing.stream().filter(i->i.storeId().equals(store)).findFirst().orElseThrow();
                var p=period(item.currentPeriodEnd(),c);
                items.updateStoreItem(new ProductSubscriptionItemUpdate(tenant,id,item.id(),c.billingCycle(),"active",p.periodStart(),p.periodEnd(),q.unitAmount(),amounts.get(n),"SGD",c.paymentNote(),item.version()));
            }
            var activeStoreIds=items.listActiveStores(tenant).stream().map(BillableStore::storeId).collect(java.util.stream.Collectors.toSet());
            var all=items.listByTenantId(tenant).stream().filter(i->i.subscriptionId().equals(id) && "active".equals(i.status()) && activeStoreIds.contains(i.storeId())).toList();
            OffsetDateTime start=all.stream().map(ProductSubscriptionItem::currentPeriodStart).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElseThrow();
            OffsetDateTime end=all.stream().map(ProductSubscriptionItem::currentPeriodEnd).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElseThrow();
            var aggregate=subscriptions.update(new ProductSubscriptionUpdate(tenant,id,c.billingCycle(),"active",start,end,all.stream().map(ProductSubscriptionItem::amount).reduce(BigDecimal.ZERO,BigDecimal::add),"SGD",c.paymentNote(),operator.userId(),s.version()));
            return finish(aggregate,c,operator,q,add?"manual_adjust":"renew",selected);
        });
    }
    @Transactional
    public ProductSubscriptionMutationResult renewItem(UUID tenant,UUID id,UUID itemId,ProductSubscriptionCommand c,PlatformBillingOperator operator) {
        subscriptions.lockTenantBilling(tenant);
        if(c==null)throw error(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        return commands.run(tenant,"wallet.renew_item",c.idempotencyKey(),Map.of("id",id,"itemId",itemId,"command",c),()->{
        commands.lock(tenant,id);var s=current(tenant,id);var item=items.findByTenantSubscriptionAndId(tenant,id,itemId).orElseThrow(()->error(PlatformBillingServiceErrorCode.SUBSCRIPTION_ITEM_NOT_FOUND));
        if(c.version()==null || c.version()!=item.version())throw error(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
        return renew(tenant,id,new ProductSubscriptionCommand(c.idempotencyKey(),"wallet",c.billingCycle(),null,null,c.amount(),c.currency(),c.paymentNote(),c.durationCount(),s.version(),List.of(item.storeId())),operator);
        });
    }
    private List<BillableStore> selected(UUID tenant,ProductSubscriptionCommand c) {
        if(c==null || !"wallet".equals(c.appKey()) || c.durationCount()==null || !Set.of("monthly","yearly").contains(Objects.toString(c.billingCycle(),""))
            || c.currentPeriodStart()!=null || c.currentPeriodEnd()!=null || c.storeIds()==null || c.storeIds().isEmpty() || c.storeIds().stream().anyMatch(Objects::isNull)
            || new HashSet<>(c.storeIds()).size()!=c.storeIds().size() || (c.currency()!=null && !"SGD".equals(c.currency()))
            || (c.amount()!=null && (c.amount().signum()<0 || c.amount().scale()>2)))throw error(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        new BillingDuration(c.billingCycle(),c.durationCount());
        if(!subscriptions.existsActiveTenant(tenant))throw error(PlatformBillingServiceErrorCode.TENANT_NOT_FOUND);
        if(subscriptions.findProductLine("wallet").filter(p->"active".equals(p.status())).isEmpty())throw error(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND);
        var result=items.listActiveStores(tenant).stream().filter(s->c.storeIds().contains(s.storeId())).sorted(Comparator.comparing(s->s.storeId().toString())).toList();
        if(result.size()!=c.storeIds().size())throw error(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        for(var s:result)if(stores.active(tenant,s.storeId()).filter(store->store.operatingEntityId()!=null).isEmpty())throw error(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        return result;
    }
    private ProductSubscription current(UUID tenant,UUID id) {return subscriptions.findByTenantIdAndId(tenant,id).filter(s->"wallet".equals(s.appKey())).orElseThrow(()->error(PlatformBillingServiceErrorCode.SUBSCRIPTION_NOT_FOUND));}
    private BillingPeriodCalculation period(OffsetDateTime previous,ProductSubscriptionCommand c) {
        OffsetDateTime now=OffsetDateTime.now(clock),start=previous!=null && previous.isAfter(now)?previous:now;
        return new BillingPeriodCalculation(start,"monthly".equals(c.billingCycle())?start.plusMonths(c.durationCount()):start.plusYears(c.durationCount()));
    }
    private void insert(ProductSubscription s,List<BillableStore> selected,ProductSubscriptionCommand c,SubscriptionQuote q,BillingPeriodCalculation period) {
        var amounts=allocate(q.finalAmount(),selected.size());for(int n=0;n<selected.size();n++)items.insertStoreItem(s,selected.get(n),period,c.billingCycle(),q.unitAmount(),amounts.get(n));
    }
    private ProductSubscriptionMutationResult finish(ProductSubscription s,ProductSubscriptionCommand c,PlatformBillingOperator operator,SubscriptionQuote q,String event,List<BillableStore> selected) {
        String payload=q.eventPayloadJson().trim();payload=payload.substring(0,payload.length()-1)+",\"selectedStoreIds\":["+String.join(",",selected.stream().map(store->"\""+store.storeId()+"\"").toList())+"]}";
        events.append(new ProductSubscriptionEventDraft(s.id(),s.tenantId(),s.appKey(),event,s.billingCycle(),s.status(),s.currentPeriodStart(),s.currentPeriodEnd(),q.finalAmount(),s.currency(),s.paymentNote(),c.idempotencyKey(),operator.userId(),payload));
        entitlements.enableTenantApp(s.tenantId(),s.appKey(),s.currentPeriodStart(),s.currentPeriodEnd(),operator.userId());
        var withItems=subscriptions.findByTenantIdAndId(s.tenantId(),s.id()).orElseThrow().withItems(items.listByTenantId(s.tenantId()).stream().filter(i->i.subscriptionId().equals(s.id())).toList());
        return new ProductSubscriptionMutationResult(false,withItems,q);
    }
    private List<BigDecimal> allocate(BigDecimal total,int count) {
        BigDecimal each=total.divide(BigDecimal.valueOf(count),2,RoundingMode.DOWN),remaining=total.subtract(each.multiply(BigDecimal.valueOf(count)));
        var result=new ArrayList<BigDecimal>();for(int i=0;i<count;i++) {BigDecimal extra=remaining.signum()>0?new BigDecimal("0.01"):BigDecimal.ZERO;result.add(each.add(extra));remaining=remaining.subtract(extra);}return result;
    }
    private PlatformBillingServiceException error(PlatformBillingServiceErrorCode code) {return new PlatformBillingServiceException(code);}
}
