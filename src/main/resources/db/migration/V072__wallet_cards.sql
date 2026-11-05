create table card_settings (
 tenant_id uuid not null,store_id uuid not null,enabled boolean not null default false,
 updated_at timestamptz not null default now(),primary key(tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table card_plans (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,issuer_id uuid not null,product_id uuid not null,
 kind text not null check(kind in('stamp','count','period')),name_zh text not null,name_en text not null,
 total int check(total between 1 and 1000000),daily_limit int check(daily_limit between 1 and 1000000),valid_days int not null check(valid_days between 1 and 3660),
 stamp_target int not null check(stamp_target between 0 and 1000000),repeatable boolean not null,reward_days int not null check(reward_days between 1 and 3660),
 price_cents bigint not null check(price_cents between 0 and 9999999999),tax_mode text not null check(tax_mode in('inclusive','exclusive')),tax_rate numeric(7,6) not null check(tax_rate between 0 and 1),
 snapshot jsonb not null,status text not null default 'active' check(status in('active','inactive')),version int not null default 0 check(version>=0),created_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),unique(id,tenant_id),unique(id,tenant_id,store_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(issuer_id,tenant_id) references operating_entities(id,tenant_id),foreign key(product_id,tenant_id) references catalog_products(id,tenant_id),
 check((kind='stamp' and stamp_target>0 and price_cents=0) or (kind<>'stamp' and stamp_target=0 and price_cents>0)),check(kind<>'count' or total is not null),check(kind<>'period' or daily_limit is not null)
);
create table customer_cards (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,plan_id uuid not null,
 kind text not null check(kind in('stamp','count','period','reward')),remaining int check(remaining>=0),initial_total int check(initial_total>0),
 daily_limit int check(daily_limit>0),progress int not null default 0 check(progress>=0),round int not null default 0 check(round>=0),timezone text not null,
 starts_at timestamptz not null,expires_at timestamptz not null,status text not null default 'active' check(status in('active','refunded','revoked')),
 snapshot jsonb not null,version int not null default 0 check(version>=0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),unique(id,tenant_id,store_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id),foreign key(plan_id,tenant_id,store_id) references card_plans(id,tenant_id,store_id),
 check(expires_at>starts_at),check(remaining is null or remaining<=initial_total)
);
create unique index ux_card_stamp_enrollment on customer_cards(tenant_id,customer_id,plan_id) where kind='stamp';
create index ix_card_customer on customer_cards(tenant_id,store_id,customer_id,created_at desc,id);
create table card_daily_usage (
 tenant_id uuid not null,card_id uuid not null,day date not null,used int not null check(used>=0),primary key(tenant_id,card_id,day),foreign key(card_id,tenant_id) references customer_cards(id,tenant_id)
);
create table card_entries (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,card_id uuid not null,customer_id uuid not null,plan_id uuid not null,
 kind text not null check(kind in('grant','stamp','redeem','reverse','refund','stamp_reverse','reward_revoke')),quantity int not null check(quantity>0),
 before_total int,after_total int,business_day date not null,before_daily int not null default 0,after_daily int not null default 0,
 source text not null,original_id uuid,actor_id uuid not null,confirmed_by uuid,reason text not null default '',created_at timestamptz not null default now(),
 foreign key(card_id,tenant_id,store_id) references customer_cards(id,tenant_id,store_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 foreign key(plan_id,tenant_id) references card_plans(id,tenant_id),unique(id,tenant_id),unique(tenant_id,plan_id,kind,source),foreign key(original_id,tenant_id) references card_entries(id,tenant_id)
);
create unique index ux_card_reverse on card_entries(tenant_id,original_id) where original_id is not null;
create index ix_card_history on card_entries(tenant_id,store_id,customer_id,created_at desc,id);
create table card_reward_grants (
 tenant_id uuid not null,card_id uuid not null,round int not null check(round>0),reward_card_id uuid not null,
 primary key(tenant_id,card_id,round),foreign key(card_id,tenant_id) references customer_cards(id,tenant_id),foreign key(reward_card_id,tenant_id) references customer_cards(id,tenant_id)
);
create table card_requests (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,plan_id uuid not null,card_id uuid,customer_id uuid,
 kind text not null check(kind in('stamp','redeem')),quantity int not null check(quantity between 1 and 1000000),created_by uuid not null,
 status text not null default 'pending' check(status in('pending','completed','cancelled')),code_hash text,authorization_hash text,
 expires_at timestamptz not null,entry_id uuid,created_at timestamptz not null default now(),
 foreign key(plan_id,tenant_id,store_id) references card_plans(id,tenant_id,store_id),foreign key(card_id,tenant_id,store_id) references customer_cards(id,tenant_id,store_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),foreign key(entry_id,tenant_id) references card_entries(id,tenant_id),unique(id,tenant_id),unique(tenant_id,code_hash),unique(tenant_id,authorization_hash),
 check((status='completed')=(entry_id is not null))
);
create table card_purchase_orders (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,customer_id uuid not null,plan_id uuid not null,card_id uuid,
 amount_cents bigint not null check(amount_cents>0),snapshot jsonb not null,status text not null default 'pending' check(status in('pending','issued','refunded','cancelled')),
 expires_at timestamptz not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),foreign key(customer_id,tenant_id) references customers(id,tenant_id),foreign key(plan_id,tenant_id,store_id) references card_plans(id,tenant_id,store_id),foreign key(card_id,tenant_id) references customer_cards(id,tenant_id),
 check((status in('issued','refunded'))=(card_id is not null))
);
create table card_payment_links (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,purchase_id uuid not null,channel text not null,account text not null,receipt text not null,
 amount_cents bigint not null check(amount_cents>0),kind text not null check(kind in('payment','refund')),actor_id uuid not null,created_at timestamptz not null default now(),
 foreign key(purchase_id,tenant_id) references card_purchase_orders(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),
 unique(tenant_id,channel,account,receipt),unique(tenant_id,purchase_id,kind)
);
create table card_commands (
 tenant_id uuid not null,store_id uuid not null,principal_id uuid not null,operation text not null,key text not null,request_hash text not null,response jsonb not null,
 created_at timestamptz not null default now(),primary key(tenant_id,principal_id,operation,key),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table card_audit (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,operation text not null,details jsonb not null,
 created_at timestamptz not null default now(),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
