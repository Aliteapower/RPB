-- Unpriced catalog records are real NULLs and can only be offered for storage.
alter table catalog_products alter column base_price_cents drop not null;
alter table catalog_product_stores alter column price_cents drop not null;
alter table catalog_products alter column tax_mode drop not null;
alter table catalog_products alter column tax_category drop not null;
alter table catalog_products alter column tax_rate drop not null;
alter table catalog_product_stores add constraint ck_storage_unpriced_channels check(price_cents is not null or channels <@ array['storage']::text[]);

insert into platform_apps(app_key,app_name,status,default_entry_route,description,sort_order,config_json)
values('wine_storage','酒水寄存','active','/stores/:storeId/wine-storage','顾客存酒卡与现场寄存交付',50,
 '{"entryPermissions":["wine_storage.view","wine_storage.receive","wine_storage.deliver"]}'::jsonb);

create table wine_storage_rules (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,category_id uuid not null,
 name text not null check(length(trim(name))>0),months int not null check(months between 1 and 120),
 version int not null check(version>0),created_by uuid not null,created_at timestamptz not null default now(),
 unique(id,tenant_id,store_id),unique(tenant_id,store_id,category_id,version),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(category_id,tenant_id) references catalog_categories(id,tenant_id)
);
create table wine_storage_photos (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,
 phase text not null check(phase in('application','receipt','followup')),uploaded_by uuid not null,
 content_type text not null check(content_type in('image/jpeg','image/png')),image bytea not null check(octet_length(image) between 1 and 5242880),
 created_at timestamptz not null default now(),unique(id,tenant_id,store_id,customer_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);
create table wine_storage_applications (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,
 status text not null check(status in('pending','stored','rejected','cancelled')),
 original jsonb not null,confirmation jsonb,reason text,created_at timestamptz not null,updated_at timestamptz not null,
 unique(id,tenant_id,store_id,customer_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);
-- Each uploaded evidence ID is attached once; bytes and phase never change.
create table wine_storage_photo_links (
 tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,photo_id uuid primary key,
 source_id uuid not null,source_type text not null check(source_type in('application','item')),
 created_at timestamptz not null default now(),
 foreign key(photo_id,tenant_id,store_id,customer_id) references wine_storage_photos(id,tenant_id,store_id,customer_id)
);
create table wine_storage_application_lines (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,application_id uuid not null,
 original jsonb not null,unique(id,tenant_id,store_id,customer_id),
 foreign key(application_id,tenant_id,store_id,customer_id) references wine_storage_applications(id,tenant_id,store_id,customer_id)
);
create table wine_storage_items (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,application_id uuid not null,line_id uuid not null,
 product_id uuid not null,rule_id uuid not null,snapshot jsonb not null,quantity int not null check(quantity between 1 and 10000),
 remaining int not null check(remaining>=0 and remaining<=quantity),opened boolean not null,source text not null check(source in('store','customer')),
 location text not null,label text not null,received_at timestamptz not null,expires_at timestamptz not null,
 physical_disposition text not null default 'held' check(physical_disposition in('held','disposed')),
 unique(id,tenant_id,store_id,customer_id),check(not opened or quantity=1),check(expires_at>received_at),
 foreign key(application_id,tenant_id,store_id,customer_id) references wine_storage_applications(id,tenant_id,store_id,customer_id),
 foreign key(line_id,tenant_id,store_id,customer_id) references wine_storage_application_lines(id,tenant_id,store_id,customer_id),
 foreign key(product_id,tenant_id) references catalog_products(id,tenant_id),foreign key(rule_id,tenant_id,store_id) references wine_storage_rules(id,tenant_id,store_id)
);
create table wine_storage_intents (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,
 origin text not null check(origin in('customer','staff')),status text not null check(status in('awaiting_authorization','authorized','completed','cancelled')),
 code_hash text not null unique,expires_at timestamptz not null,created_at timestamptz not null,completed_at timestamptz,
 unique(id,tenant_id,store_id,customer_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);
create table wine_storage_intent_lines (
 tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,intent_id uuid not null,item_id uuid not null,quantity int not null check(quantity between 1 and 10000),
 primary key(intent_id,item_id),foreign key(intent_id,tenant_id,store_id,customer_id) references wine_storage_intents(id,tenant_id,store_id,customer_id),
 foreign key(item_id,tenant_id,store_id,customer_id) references wine_storage_items(id,tenant_id,store_id,customer_id)
);
create table wine_storage_redeposits (
 tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,intent_id uuid not null,item_id uuid not null,application_id uuid not null,quantity int not null check(quantity>0),
 primary key(application_id),foreign key(intent_id,item_id) references wine_storage_intent_lines(intent_id,item_id),
 foreign key(intent_id,tenant_id,store_id,customer_id) references wine_storage_intents(id,tenant_id,store_id,customer_id),
 foreign key(item_id,tenant_id,store_id,customer_id) references wine_storage_items(id,tenant_id,store_id,customer_id),
 foreign key(application_id,tenant_id,store_id,customer_id) references wine_storage_applications(id,tenant_id,store_id,customer_id)
);
create table wine_storage_suggestions (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,line_id uuid not null,
 name_zh text not null,name_en text not null,status text not null check(status in('pending','linked','created')),product_id uuid,
 reviewed_by uuid,reviewed_at timestamptz,created_at timestamptz not null default now(),
 foreign key(line_id,tenant_id,store_id,customer_id) references wine_storage_application_lines(id,tenant_id,store_id,customer_id),
 foreign key(product_id,tenant_id) references catalog_products(id,tenant_id),check(length(trim(name_zh))+length(trim(name_en))>0)
);
create table wine_storage_events (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,source_id uuid not null,
 action text not null,actor_id uuid not null,details jsonb not null,created_at timestamptz not null,
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);
create table wine_storage_commands (
 tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,actor_type text not null,operation text not null,key text not null,
 request_hash text not null,response jsonb not null,created_at timestamptz not null default now(),
 primary key(tenant_id,store_id,actor_id,actor_type,operation,key),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_storage_applications on wine_storage_applications(tenant_id,store_id,customer_id,created_at desc);
create index ix_storage_items on wine_storage_items(tenant_id,store_id,customer_id,expires_at);
create index ix_storage_intents on wine_storage_intents(tenant_id,store_id,customer_id,created_at desc);
create index ix_storage_events on wine_storage_events(tenant_id,store_id,customer_id,created_at desc);
create index ix_storage_photos on wine_storage_photos(tenant_id,store_id,customer_id);

insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,p.permission from auth_accounts a join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
cross join(values('wine_storage.view'),('wine_storage.receive'),('wine_storage.deliver'),('wine_storage.rules.manage'),('wine_storage.correct'))p(permission)
where a.deleted_at is null and a.actor_type<>'platform_admin' and r.role_code in('tenant_admin','store_manager')
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code=p.permission and ep.deleted_at is null);
