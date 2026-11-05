-- Explicit applicability, no name-based guesses or historical snapshot rewrites.
alter table catalog_categories add column product_lines text[] not null default '{}';
alter table catalog_categories add constraint ck_catalog_category_product_lines
 check (product_lines <@ array['wallet','wine_storage']::text[] and array_position(product_lines,null) is null);
-- Aggregate existing usage once rather than scanning all products once per category.
with usage(tenant_id,category_id,owner) as (
 select p.tenant_id,p.category_id::text,case when channel='storage' then 'wine_storage' else 'wallet' end
 from catalog_products p join catalog_product_stores l on l.tenant_id=p.tenant_id and l.product_id=p.id
 cross join lateral unnest(l.channels) channel
 union select tenant_id,category_id::text,'wine_storage' from wine_storage_rules
 union select tenant_id,original->>'categoryId','wine_storage' from wine_storage_application_lines
 where original->>'categoryId' is not null
), assignments as (
 select tenant_id,category_id,array_agg(distinct owner order by owner) product_lines
 from usage group by tenant_id,category_id
)
update catalog_categories c set product_lines=a.product_lines from assignments a
where a.tenant_id=c.tenant_id and a.category_id=c.id::text;
