-- Marketing configuration and immutable quoted entitlements. Never auto-publish seed campaigns.
create table wallet_marketing_settings (
 tenant_id uuid not null,program_id uuid not null,default_deduction_policy text not null default 'proportional' check(default_deduction_policy in ('proportional','principal_first')),
 policy_version int not null default 1,config_version int not null default 0,self_topup_enabled boolean not null default false,campaigns_enabled boolean not null default true,points_enabled boolean not null default false,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,program_id),foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id));
create table wallet_campaigns (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,name text not null,status text not null default 'draft' check(status in ('draft','published','disabled','archived')),
 current_published_revision int,version int not null default 0,template_key text,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,program_id),unique(tenant_id,program_id,template_key),foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id));
create table wallet_campaign_revisions (
 tenant_id uuid not null,program_id uuid not null,campaign_id uuid not null,revision int not null check(revision>0),start_at timestamptz not null,end_at timestamptz not null,priority int not null,
 terms_snapshot text not null default '',created_by uuid not null,created_at timestamptz not null default now(),primary key(campaign_id,tenant_id,program_id,revision),
 foreign key(campaign_id,tenant_id,program_id) references wallet_campaigns(id,tenant_id,program_id),check(start_at<end_at));
create table wallet_topup_offer_sets (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,kind text not null check(kind in ('base','campaign')),campaign_id uuid,campaign_revision int,revision int not null,
 created_by uuid not null,created_at timestamptz not null default now(),unique(id,tenant_id,program_id),unique(tenant_id,program_id,kind,revision,campaign_id),
 foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),foreign key(campaign_id,tenant_id,program_id,campaign_revision) references wallet_campaign_revisions(campaign_id,tenant_id,program_id,revision),
 check((kind='base' and campaign_id is null and campaign_revision is null) or (kind='campaign' and campaign_id is not null and campaign_revision is not null)));
create unique index uq_wallet_base_revision on wallet_topup_offer_sets(tenant_id,program_id,revision) where kind='base';
create table wallet_topup_offers (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,offer_set_id uuid not null,principal_cents bigint not null,bonus_cents bigint not null default 0,sort_order int not null,enabled boolean not null default true,
 created_at timestamptz not null default now(),unique(id,tenant_id,program_id),unique(offer_set_id,principal_cents),foreign key(offer_set_id,tenant_id,program_id) references wallet_topup_offer_sets(id,tenant_id,program_id),
 check(principal_cents>0 and bonus_cents>=0 and principal_cents+bonus_cents<=9999999999999));
create table wallet_topup_quotes (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,store_id uuid not null,principal_type text not null check(principal_type in ('staff','customer')),principal_id uuid not null,account_id uuid,
 offer_id uuid not null,offer_version int not null,campaign_id uuid,campaign_version int,principal_cents bigint not null,bonus_cents bigint not null,channel text not null check(channel in ('cash','paynow','credit_card')),
 deduction_policy text not null check(deduction_policy in ('proportional','principal_first')),policy_version int not null,config_version int not null,offer_snapshot jsonb not null,policy_snapshot jsonb not null,expires_at timestamptz not null,consumed_topup_id uuid,
 created_at timestamptz not null default now(),unique(id,tenant_id,program_id),unique(consumed_topup_id,tenant_id),
 foreign key(program_id,tenant_id,store_id) references wallet_program_stores(program_id,tenant_id,store_id),foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
 foreign key(offer_id,tenant_id,program_id) references wallet_topup_offers(id,tenant_id,program_id),foreign key(campaign_id,tenant_id,program_id,campaign_version) references wallet_campaign_revisions(campaign_id,tenant_id,program_id,revision),
 foreign key(consumed_topup_id,tenant_id) references wallet_topups(id,tenant_id),check(principal_cents>0 and bonus_cents>=0 and principal_cents+bonus_cents<=9999999999999));
create index ix_wallet_quote_owner on wallet_topup_quotes(tenant_id,program_id,principal_type,principal_id,expires_at);
alter table wallet_topups add column quote_id uuid,add column customer_id uuid,add column offer_id uuid,add column offer_version int,add column campaign_id uuid,add column campaign_version int,
 add column offer_snapshot jsonb not null default '{"kind":"legacy_no_campaign"}',add column policy_snapshot jsonb not null default '{"deductionPolicy":"proportional","policyVersion":1}',
 add column origin text not null default 'staff' check(origin in ('staff','customer')),add column customer_declared_paid_at timestamptz,add column customer_declared_note text,add column credited_at timestamptz;
update wallet_topups t set customer_id=a.customer_id,credited_at=case when t.status='credited' then t.updated_at end from wallet_accounts a where a.id=t.account_id and a.tenant_id=t.tenant_id;
alter table wallet_topups add constraint fk_wallet_topup_customer foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 add constraint fk_wallet_topup_quote foreign key(quote_id,tenant_id,program_id) references wallet_topup_quotes(id,tenant_id,program_id),
 add constraint fk_wallet_topup_offer foreign key(offer_id,tenant_id,program_id) references wallet_topup_offers(id,tenant_id,program_id),
 add constraint fk_wallet_topup_campaign foreign key(campaign_id,tenant_id,program_id,campaign_version) references wallet_campaign_revisions(campaign_id,tenant_id,program_id,revision),
 add constraint ck_wallet_customer_topup check(origin<>'customer' or (quote_id is not null and customer_id is not null)),
 add constraint ck_wallet_topup_marketing_amount check((principal_cents is null or principal_cents=amount_cents) and (credit_total_cents is null or credit_total_cents=amount_cents+bonus_cents));
create unique index uq_wallet_topup_quote on wallet_topups(tenant_id,quote_id) where quote_id is not null;
create unique index uq_wallet_customer_pending on wallet_topups(tenant_id,program_id,customer_id) where origin='customer' and status in ('pending_collection','verified_pending_credit');
create index ix_wallet_customer_topups on wallet_topups(tenant_id,program_id,customer_id,created_at desc,id);
create table wallet_marketing_rate_limits (
 tenant_id uuid not null,program_id uuid not null,subject text not null,operation text not null,window_at timestamptz not null,counter int not null,
 primary key(tenant_id,program_id,subject,operation,window_at),foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id));

-- Customer attribution must agree with the owning account, including plan and tenant.
alter table wallet_accounts add constraint uq_wallet_account_customer_scope unique(id,tenant_id,program_id,customer_id);
alter table wallet_topups add constraint fk_wallet_topup_account_customer foreign key(account_id,tenant_id,program_id,customer_id) references wallet_accounts(id,tenant_id,program_id,customer_id);
create function wallet_validate_base_offer() returns trigger language plpgsql as $$
begin
 if new.bonus_cents<>0 and exists(select 1 from wallet_topup_offer_sets s where s.id=new.offer_set_id and s.tenant_id=new.tenant_id and s.program_id=new.program_id and s.kind='base') then
  raise exception 'base offers cannot grant bonus' using errcode='23514';
 end if;
 return new;
end $$;
create trigger wallet_base_offer_check before insert or update on wallet_topup_offers for each row execute function wallet_validate_base_offer();
