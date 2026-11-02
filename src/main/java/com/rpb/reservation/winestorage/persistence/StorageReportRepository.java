package com.rpb.reservation.winestorage.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.winestorage.application.StorageReportDtos.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.winestorage.persistence.StorageRepository.*;

/** Historical business bills, never a sum of current balances or audit events. */
@Repository
public class StorageReportRepository {
 private final JdbcTemplate jdbc;
 public StorageReportRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String DEPOSITS="""
  held as (
   select i.application_id,min(i.received_at) as received_at,jsonb_agg(i.snapshot || jsonb_build_object('quantity',i.quantity,'location',i.location,'label',i.label) order by i.id) as lines
   from wine_storage_items i cross join scope p where i.tenant_id=p.tenant and i.store_id=p.store group by i.application_id
  ), requested as (
   select l.application_id,jsonb_agg(l.original order by l.id) as lines from wine_storage_application_lines l cross join scope p
   where l.tenant_id=p.tenant and l.store_id=p.store group by l.application_id
  ), bills as (
   select a.id,a.customer_id,a.status,coalesce(h.received_at,a.created_at) as occurred_at,
    coalesce(h.lines,a.confirmation->'lines',r.lines,'[]'::jsonb) as lines
   from wine_storage_applications a cross join scope p left join held h on h.application_id=a.id left join requested r on r.application_id=a.id
   where a.tenant_id=p.tenant and a.store_id=p.store
  )
  """;
 private static final String PICKUPS="""
  selected as (
   select l.intent_id,jsonb_agg(i.snapshot || jsonb_build_object('quantity',l.quantity,'location',i.location,'label',i.label) order by i.id) as lines
   from wine_storage_intent_lines l cross join scope p join wine_storage_items i on i.id=l.item_id and i.tenant_id=l.tenant_id and i.store_id=l.store_id and i.customer_id=l.customer_id
   where l.tenant_id=p.tenant and l.store_id=p.store group by l.intent_id
  ), bills as (
   select a.id,a.customer_id,case when a.status in ('authorized','awaiting_authorization') and a.expires_at<=p.now then 'expired' else a.status end as status,
    coalesce(a.completed_at,a.created_at) as occurred_at,coalesce(l.lines,'[]'::jsonb) as lines
   from wine_storage_intents a cross join scope p left join selected l on l.intent_id=a.id where a.tenant_id=p.tenant and a.store_id=p.store
  )
  """;
 public Page list(StoreScope scope,String kind,String query,String status,Instant from,Instant to,int page,int size,Instant now){
  var args=new ArrayList<Object>();args.add(scope.tenantId().value());args.add(scope.storeId().value());args.add(at(now));
  String cte="with scope as (select ?::uuid as tenant,?::uuid as store,?::timestamptz as now),"+(kind.equals("deposit")?DEPOSITS:PICKUPS)+",report as ("+"""
   select b.id,b.customer_id,c.display_name as customer_name,c.phone_e164 as customer_phone,b.status,b.occurred_at,
    coalesce(v.quantity,0) as quantity,v.wines_zh,v.wines_en,v.locations
   from bills b left join customers c on c.id=b.customer_id and c.tenant_id=(select tenant from scope)
   left join lateral (
    select sum((l->>'quantity')::int) as quantity,
     string_agg(coalesce(nullif(l->>'nameZh',''),l->>'nameEn','—') || ' × ' || coalesce(l->>'quantity','0'),'；' order by ord) as wines_zh,
     string_agg(coalesce(nullif(l->>'nameEn',''),l->>'nameZh','—') || ' × ' || coalesce(l->>'quantity','0'),'; ' order by ord) as wines_en,
     string_agg(distinct nullif(concat_ws(' · ',nullif(l->'storageLocation'->>'name',''),nullif(l->>'location',''),nullif(l->>'label','')),''),'；') as locations
    from jsonb_array_elements(b.lines) with ordinality rows(l,ord)
   ) v on true
   )
   """;
  String filter=" from report where true";
  if(!query.isEmpty()){filter+=" and strpos(lower(concat_ws(' ',customer_name,customer_phone,wines_zh,wines_en,locations)),lower(?))>0";args.add(query);}
  if(!status.equals("all")){filter+=" and status=?";args.add(status);}
  if(from!=null){filter+=" and occurred_at>=?";args.add(at(from));}
  if(to!=null){filter+=" and occurred_at<?";args.add(at(to));}
  Summary summary=jdbc.queryForObject(cte+"select count(*),count(*) filter(where status in ('stored','completed')),coalesce(sum(quantity) filter(where status in ('stored','completed')),0)"+filter,(r,n)->new Summary(r.getLong(1),r.getLong(2),r.getLong(3)),args.toArray());
  args.add(size);args.add((long)page*size);
  var items=jdbc.query(cte+"select *"+filter+" order by occurred_at desc,id desc limit ? offset ?",(r,n)->new Row(id(r,"id"),kind,id(r,"customer_id"),r.getString("customer_name"),r.getString("customer_phone"),r.getString("status"),time(r,"occurred_at"),r.getLong("quantity"),r.getString("wines_zh"),r.getString("wines_en"),r.getString("locations")),args.toArray());
  return new Page(items,summary,page,size,((long)page+1)*size<summary.recordCount());
 }
}
