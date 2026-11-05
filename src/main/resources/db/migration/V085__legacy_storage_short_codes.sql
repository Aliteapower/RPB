-- Only old trusted storage creations used WS-<their own UUID> and this audit action.
-- Keep immutable storage snapshots, business references, and manual product codes intact.
create table catalog_legacy_storage_code_changes (
 tenant_id uuid not null,
 product_id uuid not null,
 old_code text not null,
 new_code text not null check(new_code ~ '^[0-9]{1,5}$'),
 old_version integer not null,
 new_version integer not null,
 old_updated_at timestamptz not null,
 migrated_at timestamptz not null default now(),
 primary key(tenant_id,product_id),
 unique(tenant_id,old_code),
 foreign key(product_id,tenant_id) references catalog_products(id,tenant_id),
 check(new_version=old_version+1)
);

do $migration$
declare
 candidate record;
 short_code text;
begin
 perform set_config('lock_timeout','10s',true);
 -- Serialize all catalog writers while choosing and assigning codes, including old clients.
 lock table catalog_products in share row exclusive mode;
 for candidate in
  select p.id,p.tenant_id,p.code,p.version,p.updated_at
  from catalog_products p
  where p.code='WS-'||p.id::text
   and exists(select 1 from catalog_audit a where a.tenant_id=p.tenant_id and a.source_id=p.id and a.action='storage.product.create')
  order by p.tenant_id,p.id
  for update
 loop
  select n::text into short_code from generate_series(1,99999) n
  where not exists(select 1 from catalog_products p where p.tenant_id=candidate.tenant_id and p.code=n::text)
  limit 1;
  if short_code is null then
   raise exception 'Legacy storage short code range exhausted for tenant %',candidate.tenant_id;
  end if;
  insert into catalog_legacy_storage_code_changes(tenant_id,product_id,old_code,new_code,old_version,new_version,old_updated_at)
  values(candidate.tenant_id,candidate.id,candidate.code,short_code,candidate.version,candidate.version+1,candidate.updated_at);
  update catalog_products set code=short_code,version=version+1,updated_at=now()
  where tenant_id=candidate.tenant_id and id=candidate.id;
 end loop;
end
$migration$;
