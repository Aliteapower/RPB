-- Independent points ledger. No historical grants, subscriptions or permission grants.
create table loyalty_programs (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,operating_entity_id uuid not null,
 name text not null check(length(trim(name)) between 1 and 200),status text not null default 'active' check(status in ('active','frozen','closed')),
 version integer not null default 0 check(version>=0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),unique(id,tenant_id,operating_entity_id),
 foreign key(operating_entity_id,tenant_id) references operating_entities(id,tenant_id)
);
create table wallet_loyalty_bindings (
 tenant_id uuid not null,wallet_program_id uuid not null,loyalty_program_id uuid not null,operating_entity_id uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,wallet_program_id),unique(tenant_id,loyalty_program_id),
 foreign key(wallet_program_id,tenant_id,operating_entity_id) references wallet_programs(id,tenant_id,operating_entity_id),
 foreign key(loyalty_program_id,tenant_id,operating_entity_id) references loyalty_programs(id,tenant_id,operating_entity_id)
);
create table loyalty_accounts (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,customer_id uuid not null,
 balance integer not null default 0 check(balance>=0),version integer not null default 0 check(version>=0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,program_id,customer_id),unique(id,tenant_id,program_id),
 foreign key(program_id,tenant_id) references loyalty_programs(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);
create index ix_loyalty_customer on loyalty_accounts(tenant_id,customer_id);
create table loyalty_entries (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,operating_entity_id uuid not null,
 account_id uuid not null,store_id uuid not null,actor_id uuid not null,
 kind text not null check(kind in ('manual_grant','manual_deduct','redemption','reversal')),
 quantity integer not null check(quantity>0),before_balance integer not null check(before_balance>=0),after_balance integer not null check(after_balance>=0),
 account_version integer not null check(account_version>0),reason text not null check(length(trim(reason)) between 1 and 500),
 note text null check(length(note)<=2000),external_reference text null check(length(external_reference)<=200),original_entry_id uuid null,
 created_at timestamptz not null default now(),
 unique(id,tenant_id),unique(id,tenant_id,program_id,account_id),unique(account_id,account_version),
 foreign key(account_id,tenant_id,program_id) references loyalty_accounts(id,tenant_id,program_id),
 foreign key(program_id,tenant_id,operating_entity_id) references loyalty_programs(id,tenant_id,operating_entity_id),
 foreign key(store_id,tenant_id,operating_entity_id) references stores(id,tenant_id,operating_entity_id),
 foreign key(original_entry_id,tenant_id,program_id,account_id) references loyalty_entries(id,tenant_id,program_id,account_id),
 check((kind='reversal')=(original_entry_id is not null)),
 check((kind='manual_grant' and after_balance::bigint=before_balance::bigint+quantity)
    or (kind in ('manual_deduct','redemption') and after_balance::bigint=before_balance::bigint-quantity)
    or (kind='reversal' and abs(after_balance::bigint-before_balance::bigint)=quantity))
);
create unique index ux_loyalty_original_reversal on loyalty_entries(tenant_id,original_entry_id) where original_entry_id is not null;
create index ix_loyalty_entries_account on loyalty_entries(tenant_id,account_id,account_version desc);
-- Enforce opposite full movement even for writes outside the application.
create function loyalty_check_reversal() returns trigger language plpgsql as $$
declare original loyalty_entries%rowtype;
begin
 if new.kind='reversal' then
  select * into original from loyalty_entries where id=new.original_entry_id and tenant_id=new.tenant_id and account_id=new.account_id;
  if not found or original.kind='reversal' or new.quantity<>original.quantity
     or new.after_balance::bigint-new.before_balance::bigint<>original.before_balance::bigint-original.after_balance::bigint then
   raise exception 'invalid points reversal' using errcode='23514';
  end if;
 end if;
 return new;
end; $$;
create trigger trg_loyalty_reversal before insert on loyalty_entries for each row execute function loyalty_check_reversal();
create table loyalty_redemptions (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,entry_id uuid not null,
 redemption_description text not null check(length(trim(redemption_description)) between 1 and 1000),
 external_reference text null check(length(external_reference)<=200),created_at timestamptz not null default now(),
 unique(tenant_id,entry_id),foreign key(entry_id,tenant_id) references loyalty_entries(id,tenant_id)
);
create table loyalty_commands (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,
 operation text not null check(operation in ('manual_grant','manual_deduct','redemption','reversal')),
 idempotency_key text not null check(length(trim(idempotency_key)) between 1 and 100),request_hash text not null,response jsonb not null,
 created_at timestamptz not null default now(),unique(tenant_id,actor_id,operation,idempotency_key),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),check(jsonb_typeof(response)='object')
);
create table loyalty_audit (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,
 action text not null,source_id uuid not null,details jsonb not null,created_at timestamptz not null default now(),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(source_id,tenant_id) references loyalty_entries(id,tenant_id),check(jsonb_typeof(details)='object')
);
create index ix_loyalty_audit_scope on loyalty_audit(tenant_id,store_id,created_at desc);

-- Posted entries, redemptions and their evidence are append-only; corrections use reversal entries.
create function loyalty_reject_history_change() returns trigger language plpgsql as $$
begin raise exception 'posted points history is immutable' using errcode='23514'; end; $$;
create trigger trg_loyalty_entries_immutable before update or delete on loyalty_entries for each row execute function loyalty_reject_history_change();
create trigger trg_loyalty_redemptions_immutable before update or delete on loyalty_redemptions for each row execute function loyalty_reject_history_change();
create trigger trg_loyalty_audit_immutable before update or delete on loyalty_audit for each row execute function loyalty_reject_history_change();
create trigger trg_loyalty_commands_immutable before update or delete on loyalty_commands for each row execute function loyalty_reject_history_change();
-- A redemption and its debit must commit together, including direct SQL callers.
create function loyalty_check_redemption_pair() returns trigger language plpgsql as $$
declare entry_key uuid; entry_tenant uuid; entry_kind text; pair_count integer;
begin
 if tg_table_name='loyalty_entries' then entry_key=new.id; entry_tenant=new.tenant_id;
 else entry_key=new.entry_id; entry_tenant=new.tenant_id; end if;
 select kind into entry_kind from loyalty_entries where id=entry_key and tenant_id=entry_tenant;
 select count(*) into pair_count from loyalty_redemptions where entry_id=entry_key and tenant_id=entry_tenant;
 if (entry_kind='redemption' and pair_count<>1) or (entry_kind<>'redemption' and pair_count<>0) then
  raise exception 'points redemption debit pair mismatch' using errcode='23514';
 end if;
 return null;
end; $$;
create constraint trigger trg_loyalty_entry_redemption_pair after insert on loyalty_entries deferrable initially deferred for each row execute function loyalty_check_redemption_pair();
create constraint trigger trg_loyalty_redemption_entry_pair after insert on loyalty_redemptions deferrable initially deferred for each row execute function loyalty_check_redemption_pair();
