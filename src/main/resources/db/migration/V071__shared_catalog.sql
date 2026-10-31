-- Tenant product identity; store price/listing never grants a business product entitlement.
create table catalog_categories (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null references tenants(id),code text not null,
 name_zh text not null default '',name_en text not null default '',enabled boolean not null default true,sort_order int not null default 0,
 version int not null default 0 check(version>=0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),unique(tenant_id,code),check(length(trim(name_zh))+length(trim(name_en))>0)
);
create table catalog_products (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null references tenants(id),category_id uuid not null,
 code text not null,name_zh text not null default '',name_en text not null default '',description_zh text not null default '',description_en text not null default '',
 kind text not null check(kind in('physical','service')),unit text not null,image_url text not null default '',
 status text not null default 'draft' check(status in('draft','active','inactive','archived')),version int not null default 0 check(version>=0),
 base_price_cents bigint not null check(base_price_cents between 0 and 9999999999),currency text not null default 'SGD' check(currency='SGD'),
 tax_mode text not null check(tax_mode in('inclusive','exclusive')),tax_category text not null check(tax_category in('taxable','zero','exempt','not_applicable')),
 tax_rate numeric(7,6) not null check(tax_rate between 0 and 1),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),unique(tenant_id,code),foreign key(category_id,tenant_id) references catalog_categories(id,tenant_id),
 check(length(trim(name_zh))+length(trim(name_en))>0),check(tax_category='taxable' or tax_rate=0)
);
create table catalog_product_stores (
 tenant_id uuid not null,product_id uuid not null,store_id uuid not null,price_cents bigint not null check(price_cents between 0 and 9999999999),
 channels text[] not null default '{}',updated_at timestamptz not null default now(),
 primary key(tenant_id,product_id,store_id),foreign key(product_id,tenant_id) references catalog_products(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),
 check(channels <@ array['cards','rewards','shopping','storage']::text[])
);
create table catalog_commands (
 tenant_id uuid not null,actor_id uuid not null,operation text not null,key text not null,request_hash text not null,response jsonb not null,
 created_at timestamptz not null default now(),primary key(tenant_id,actor_id,operation,key)
);
create table catalog_audit (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,action text not null,source_id uuid not null,
 created_at timestamptz not null default now(),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_catalog_browse on catalog_products(tenant_id,category_id,status,code);
insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,p.permission from auth_accounts a join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
cross join(values('catalog.view'),('catalog.manage'),('catalog.publish'),('wallet.cards.view'),('wallet.cards.manage'),('wallet.cards.sell'),('wallet.cards.stamp'),('wallet.cards.redeem'),('wallet.cards.reverse'),('wallet.cards.refund'))p(permission)
where a.deleted_at is null and a.actor_type<>'platform_admin' and r.role_code in('tenant_admin','store_manager')
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code=p.permission and ep.deleted_at is null);
