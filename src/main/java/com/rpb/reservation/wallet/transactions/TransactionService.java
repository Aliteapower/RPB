package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletAccess;
import com.rpb.reservation.wallet.domain.WalletException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.rpb.reservation.wallet.transactions.TransactionViews.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class TransactionService {
    private final TransactionRepository repository; private final WalletAccess access; private final Clock clock;
    public TransactionService(TransactionRepository repository,WalletAccess access,Clock clock){this.repository=repository;this.access=access;this.clock=clock;}
    private record Context(UUID tenant,UUID customer,List<Store> stores,Capabilities capabilities,Set<String> sources) {}
    private Context context(StoreScope s,CurrentActor actor,CustomerAuthPrincipal customer) {
        Capabilities caps;
        if(customer!=null){
            access.historicalCustomer(s,customer);
            caps=new Capabilities(true,true,true,true);
        }
        else {
            if(actor==null)throw new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录");
            if(!s.tenantId().value().equals(actor.tenantId())||"platform_admin".equals(actor.actorType())||!actor.canAccessStore(s.storeId().value()))throw WalletException.forbidden();
            caps=new Capabilities(actor.hasPermission("wallet.view"),actor.hasPermission("wallet.points.view"),actor.hasPermission("wallet.refund.manage"),actor.hasPermission("wallet.refund.manage"));
        }
        Set<String> sources=new TreeSet<>();if(caps.money()){sources.add("bill");sources.add("topup");}if(caps.points())sources.add("points");if(caps.refunds())sources.add("refund");if(caps.reversals())sources.add("money_reversal");
        if(sources.isEmpty())throw WalletException.forbidden();
        var stores=repository.stores(s.tenantId().value(),actor==null?Set.of():actor.storeIds(),customer==null?null:customer.customerId(),s.storeId().value());
        if(stores.stream().noneMatch(v->v.id().equals(s.storeId().value())))throw WalletException.missing();
        return new Context(s.tenantId().value(),customer==null?null:customer.customerId(),stores,caps,sources);
    }
    public Page list(StoreScope s,CurrentActor actor,CustomerAuthPrincipal customer,Map<String,String> input) {
        Context c=context(s,actor,customer);
        String zone=c.stores().stream().filter(v->v.id().equals(s.storeId().value())).findFirst().orElseThrow().timeZone();
        var q=TransactionQuery.parse(input,clock,zone,customer!=null);authorizeType(c,q.type());
        String category=TransactionFilters.category(q.category(),q.type(),c.sources());
        Set<UUID> stores=selectedStores(c,s,q.targetStoreId());
        var p=params(c,stores);String where=security(c)+" and occurred_at >= (cast(:start as date)::timestamp at time zone time_zone) and occurred_at < (cast(:end as date)::timestamp at time zone time_zone)";
        p.addValue("start",q.period().startDate().toString()).addValue("end",q.period().endDate().plusDays(1).toString());
        where+=" and "+TransactionFilters.folder(category,q.type(),c.sources());
        if(!q.direction().equals("all")){where+=" and ("+TransactionFilters.flow(customer!=null)+")=:flow";p.addValue("flow",q.direction());}
        if(!q.type().equals("all")){where+=" and type=:type";p.addValue("type",q.type());}
        if(!q.status().equals("all")){where+=" and status=:status";p.addValue("status",q.status());}
        if(!q.reference().isEmpty()){where+=" and position(lower(:reference) in lower(reference))>0";p.addValue("reference",q.reference());}
        if(!q.customerQuery().isEmpty()){where+=" and (position(lower(:search) in lower(customer_name))>0 or position(:search in coalesce(customer_phone,''))>0)";p.addValue("search",q.customerQuery());}
        var totals=repository.totals(where,p);
        String binding=digest(c.tenant()+"|"+c.customer()+"|"+(actor==null?"":actor.actorId())+"|"+new TreeSet<>(stores)+"|"+c.sources()+"|"+q.period()+"|"+q.type()+"|"+q.status()+"|"+q.reference()+"|"+q.customerQuery()+"|"+category+"|"+q.direction());
        String pageWhere=where;
        if(q.cursor()!=null&&!q.cursor().isBlank()) {
            try {
                String[] v=new String(Base64.getUrlDecoder().decode(q.cursor()),StandardCharsets.UTF_8).split("\\|",-1);
                if(v.length!=4||!v[0].equals(binding)||!c.sources().contains(v[2]))throw WalletException.invalid();
                p.addValue("at",Timestamp.from(Instant.parse(v[1]))).addValue("source",v[2]).addValue("id",UUID.fromString(v[3]));
                pageWhere+=" and (occurred_at,source_type,id)<(:at,:source,:id)";
            }catch(IllegalArgumentException|DateTimeException e){throw WalletException.invalid();}
        }
        var rows=new ArrayList<>(repository.items(pageWhere,p,customer!=null,q.limit()+1));String next=null;
        if(rows.size()>q.limit()){rows.removeLast();var last=rows.getLast();next=Base64.getUrlEncoder().withoutPadding().encodeToString((binding+"|"+last.occurredAt()+"|"+last.sourceType()+"|"+last.id()).getBytes(StandardCharsets.UTF_8));}
        return new Page(List.copyOf(rows),next,totals.count(),totals.summary(),c.stores(),q.period(),c.capabilities(),category);
    }
    public Detail detail(StoreScope s,CurrentActor actor,CustomerAuthPrincipal customer,String source,UUID id) {
        Context c=context(s,actor,customer);
        if(!Set.of("bill","topup","refund","points","money_reversal").contains(source))throw WalletException.invalid();
        if(!c.sources().contains(source))throw WalletException.forbidden();
        // Detail path's store must be the actual source store; no implicit cross-store lookup.
        var p=params(c,Set.of(s.storeId().value())).addValue("source",source).addValue("id",id);
        var rows=repository.items(security(c)+" and source_type=:source and id=:id",p,customer!=null,1);
        if(rows.isEmpty())throw WalletException.missing();var item=rows.getFirst();
        Set<UUID> relatedStores=new HashSet<>();c.stores().forEach(v->relatedStores.add(v.id()));
        var relatedParams=params(c,relatedStores).addValue("source",source).addValue("id",id).addValue("originalSource",item.originalSourceType()).addValue("originalId",item.originalId());
        var related=repository.items(security(c)+" and ((original_source_type=:source and original_id=:id) or (source_type=:originalSource and id=:originalId))",relatedParams,customer!=null,100);
        List<Event> events=new ArrayList<>();events.add(new Event("created",item.createdAt(),"创建"));
        events.addAll(repository.evidence(c.tenant(),item.storeId(),source,id));
        if(item.postedAt()!=null)events.add(new Event("posted",item.postedAt(),item.status().equals("completed")?"完成记账":"实际记账"));
        events.sort(Comparator.comparing(Event::occurredAt).thenComparing(Event::type));
        return new Detail(item,related,List.copyOf(events));
    }
    private Set<UUID> selectedStores(Context c,StoreScope s,String requested) {
        var authorized=new HashSet<UUID>();c.stores().forEach(v->authorized.add(v.id()));
        if("all".equals(requested))return authorized;
        UUID chosen=requested.isBlank()?s.storeId().value():parseStore(requested);
        if(!authorized.contains(chosen))throw WalletException.forbidden();return Set.of(chosen);
    }
    private UUID parseStore(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException e){throw WalletException.invalid();}}
    private void authorizeType(Context c,String type) {
        boolean allowed=switch(type){case "all"->true;case "payment","topup"->c.capabilities().money();case "refund"->c.capabilities().refunds();case "reversal"->c.capabilities().points()||c.capabilities().reversals();default->c.capabilities().points();};
        if(!allowed)throw WalletException.forbidden();
    }
    private MapSqlParameterSource params(Context c,Set<UUID> stores){return new MapSqlParameterSource("tenant",c.tenant()).addValue("stores",stores).addValue("sources",c.sources()).addValue("customer",c.customer());}
    private String security(Context c){return "tenant_id=:tenant and store_id in (:stores) and source_type in (:sources)"+(c.customer()!=null?" and customer_id=:customer":"");}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
