-- Expand safely: old money is principal, never award historical bonuses or rewrite immutable entries.
alter table wallet_accounts add column principal_balance_cents bigint not null default 0,
 add column bonus_balance_cents bigint not null default 0,
 add column principal_reserved_cents bigint not null default 0,
 add column bonus_reserved_cents bigint not null default 0,
 add column funding_cutover_version integer not null default 0;
update wallet_accounts set principal_balance_cents=balance_cents,principal_reserved_cents=reserved_cents,funding_cutover_version=version;
alter table wallet_accounts add constraint wallet_account_components_check check(
 balance_cents=principal_balance_cents+bonus_balance_cents and reserved_cents=principal_reserved_cents+bonus_reserved_cents
 and principal_balance_cents>=0 and bonus_balance_cents>=0 and principal_reserved_cents between 0 and principal_balance_cents and bonus_reserved_cents between 0 and bonus_balance_cents);
alter table wallet_topups add column bonus_cents bigint not null default 0 check(bonus_cents>=0),
 add column deduction_policy text not null default 'proportional' check(deduction_policy in ('proportional','principal_first')),
 add column principal_cents bigint generated always as (amount_cents) stored,
 add column credit_total_cents bigint generated always as (amount_cents+bonus_cents) stored,
 add constraint wallet_topup_credit_limit check(amount_cents+bonus_cents<=9999999999999);
alter table wallet_bills add column principal_paid_cents bigint not null default 0,add column bonus_paid_cents bigint not null default 0;
update wallet_bills set principal_paid_cents=wallet_paid_cents;
alter table wallet_bills add constraint wallet_bill_components_check check(principal_paid_cents>=0 and bonus_paid_cents>=0 and wallet_paid_cents=principal_paid_cents+bonus_paid_cents);
alter table wallet_authorizations add column principal_cents bigint not null default 0,add column bonus_cents bigint not null default 0;
update wallet_authorizations set principal_cents=amount_cents;
-- Old unconsumed quotes must be re-reviewed under the component model.
update wallet_authorizations set expires_at=least(expires_at,now()) where consumed_at is null;
alter table wallet_refunds add column principal_cents bigint not null default 0,add column bonus_cents bigint not null default 0;
update wallet_refunds set principal_cents=wallet_cents;
-- With gifts, wallet rights removed may exceed the cash principal refunded.
do $$ declare c record;begin for c in select conname from pg_constraint where conrelid='wallet_refunds'::regclass and contype='c' and pg_get_constraintdef(oid) like '%wallet_cents <= amount_cents%' loop execute format('alter table wallet_refunds drop constraint %I',c.conname);end loop;end $$;
alter table wallet_topups add unique(id,tenant_id,program_id,account_id);
alter table wallet_entries add unique(id,tenant_id,program_id,account_id);
alter table wallet_refunds add unique(id,tenant_id,program_id,account_id);
create table wallet_funding_lots(
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,account_id uuid not null,
 topup_id uuid null,legacy boolean not null default false,policy text not null check(policy in ('proportional','principal_first')),
 initial_principal bigint not null check(initial_principal>=0),initial_bonus bigint not null check(initial_bonus>=0),
 principal bigint not null check(principal>=0),bonus bigint not null check(bonus>=0),
 reserved_principal bigint not null default 0,reserved_bonus bigint not null default 0,
 spent_principal bigint not null default 0 check(spent_principal>=0),spent_bonus bigint not null default 0 check(spent_bonus>=0),
 refunded_principal bigint not null default 0 check(refunded_principal>=0),revoked_bonus bigint not null default 0 check(revoked_bonus>=0),
 credit_sequence bigint not null,version integer not null default 0,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,program_id,account_id),unique(tenant_id,topup_id),unique(account_id,credit_sequence),
 foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
 foreign key(topup_id,tenant_id) references wallet_topups(id,tenant_id),
 foreign key(topup_id,tenant_id,program_id,account_id) references wallet_topups(id,tenant_id,program_id,account_id),
 check(reserved_principal between 0 and principal and reserved_bonus between 0 and bonus),
 check((legacy and topup_id is null and bonus=0 and initial_bonus=0) or (not legacy and topup_id is not null)),
 check(legacy or (initial_principal=principal+spent_principal+refunded_principal and initial_bonus=bonus+spent_bonus+revoked_bonus))
);
create unique index uq_wallet_legacy_pool on wallet_funding_lots(tenant_id,account_id) where legacy;
create index ix_wallet_lot_account on wallet_funding_lots(tenant_id,account_id,credit_sequence);
insert into wallet_funding_lots(tenant_id,program_id,account_id,legacy,policy,initial_principal,initial_bonus,principal,bonus,reserved_principal,credit_sequence)
 select tenant_id,program_id,id,true,'principal_first',balance_cents,0,balance_cents,0,reserved_cents,0 from wallet_accounts;
create table wallet_entry_allocations(
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,account_id uuid not null,
 entry_id uuid not null,lot_id uuid not null,action text not null check(action in ('credit','debit','bill_refund','topup_refund','bonus_revoke')),
 principal_cents bigint not null check(principal_cents>=0),bonus_cents bigint not null check(bonus_cents>=0),original_allocation_id uuid null,
 created_at timestamptz not null default now(),unique(id,tenant_id),unique(id,tenant_id,program_id,account_id,lot_id),unique(entry_id,lot_id),check(principal_cents+bonus_cents>0),
 foreign key(entry_id,tenant_id) references wallet_entries(id,tenant_id),
 foreign key(entry_id,tenant_id,program_id,account_id) references wallet_entries(id,tenant_id,program_id,account_id),
 foreign key(lot_id,tenant_id,program_id,account_id) references wallet_funding_lots(id,tenant_id,program_id,account_id),
 foreign key(original_allocation_id,tenant_id) references wallet_entry_allocations(id,tenant_id),
 foreign key(original_allocation_id,tenant_id,program_id,account_id,lot_id) references wallet_entry_allocations(id,tenant_id,program_id,account_id,lot_id)
);
create index ix_wallet_allocation_original on wallet_entry_allocations(tenant_id,original_allocation_id);
create table wallet_refund_allocations(
 tenant_id uuid not null,refund_id uuid not null,lot_id uuid not null,program_id uuid not null,account_id uuid not null,
 principal_cents bigint not null check(principal_cents>=0),bonus_cents bigint not null check(bonus_cents>=0),status text not null default 'reserved' check(status in ('reserved','completed','cancelled')),
 primary key(tenant_id,refund_id,lot_id),foreign key(refund_id,tenant_id) references wallet_refunds(id,tenant_id),
 foreign key(refund_id,tenant_id,program_id,account_id) references wallet_refunds(id,tenant_id,program_id,account_id),
 foreign key(lot_id,tenant_id,program_id,account_id) references wallet_funding_lots(id,tenant_id,program_id,account_id),check(principal_cents+bonus_cents>0)
);
-- Preserve old in-flight principal refund reservations without fabricating historical lot allocations.
insert into wallet_refund_allocations(tenant_id,refund_id,lot_id,program_id,account_id,principal_cents,bonus_cents)
 select r.tenant_id,r.id,l.id,r.program_id,r.account_id,r.wallet_cents,0 from wallet_refunds r join wallet_funding_lots l on l.tenant_id=r.tenant_id and l.account_id=r.account_id and l.legacy
 where r.topup_id is not null and r.exception_collection_id is null and r.status='pending' and r.wallet_cents>0;
create table wallet_bonus_exits(id uuid primary key default gen_random_uuid(),tenant_id uuid not null,lot_id uuid not null,account_id uuid not null,program_id uuid not null,bonus_cents bigint not null check(bonus_cents>0),reason text not null,created_by uuid not null,created_at timestamptz not null default now(),foreign key(lot_id,tenant_id,program_id,account_id) references wallet_funding_lots(id,tenant_id,program_id,account_id));
alter table wallet_entries drop constraint wallet_entries_kind_check,drop constraint wallet_entries_check;
alter table wallet_entries add constraint wallet_entries_kind_check check(kind in ('topup_credit','bill_debit','bill_refund','topup_refund','bonus_revoke')),
 add constraint wallet_entries_check check((kind in ('topup_credit','bill_refund') and after_cents=before_cents+amount_cents) or(kind in ('bill_debit','topup_refund','bonus_revoke') and after_cents=before_cents-amount_cents));
create function wallet_verify_funding() returns trigger language plpgsql as $$
declare aid uuid;tid uuid;a wallet_accounts%rowtype; p bigint;g bigint;rp bigint;rg bigint;
begin
 if TG_TABLE_NAME='wallet_accounts' then aid:=NEW.id;else aid:=NEW.account_id;end if;tid:=NEW.tenant_id;
 select * into a from wallet_accounts where id=aid and tenant_id=tid;
 if not found then return null;end if;
 select coalesce(sum(principal),0),coalesce(sum(bonus),0),coalesce(sum(reserved_principal),0),coalesce(sum(reserved_bonus),0) into p,g,rp,rg from wallet_funding_lots where tenant_id=tid and account_id=aid;
 if (a.principal_balance_cents,a.bonus_balance_cents,a.principal_reserved_cents,a.bonus_reserved_cents) is distinct from (p,g,rp,rg) then raise exception 'wallet funding components inconsistent' using errcode='23514';end if;
 if exists(select 1 from wallet_entries e left join wallet_entry_allocations x on x.entry_id=e.id and x.tenant_id=e.tenant_id where e.account_id=aid and e.tenant_id=tid and e.account_version>a.funding_cutover_version group by e.id,e.amount_cents having coalesce(sum(x.principal_cents+x.bonus_cents),0)<>e.amount_cents) then raise exception 'wallet entry allocations inconsistent' using errcode='23514';end if;
 return null;
end $$;
create constraint trigger wallet_account_funding_guard after insert or update on wallet_accounts deferrable initially deferred for each row execute function wallet_verify_funding();
create constraint trigger wallet_lot_funding_guard after insert or update on wallet_funding_lots deferrable initially deferred for each row execute function wallet_verify_funding();
create constraint trigger wallet_entry_funding_guard after insert on wallet_entries deferrable initially deferred for each row execute function wallet_verify_funding();
create constraint trigger wallet_allocation_funding_guard after insert on wallet_entry_allocations deferrable initially deferred for each row execute function wallet_verify_funding();
create function wallet_funding_immutable() returns trigger language plpgsql as $$ begin raise exception 'wallet funding history is immutable' using errcode='23514';end $$;
create trigger wallet_allocation_immutable before update or delete on wallet_entry_allocations for each row execute function wallet_funding_immutable();
create trigger wallet_lot_no_delete before delete on wallet_funding_lots for each row execute function wallet_funding_immutable();
create function wallet_refund_components_guard() returns trigger language plpgsql as $$ declare r wallet_refunds%rowtype;begin select * into r from wallet_refunds where id=NEW.id and tenant_id=NEW.tenant_id;if r.wallet_cents<>r.principal_cents+r.bonus_cents or r.principal_cents<0 or r.bonus_cents<0 or (r.topup_id is null and r.wallet_cents>r.amount_cents) or (r.topup_id is not null and r.principal_cents>r.amount_cents) then raise exception 'refund component amounts inconsistent' using errcode='23514';end if;return null;end $$;
create constraint trigger wallet_refund_components_check after insert or update on wallet_refunds deferrable initially deferred for each row execute function wallet_refund_components_guard();

create function wallet_lot_identity_guard() returns trigger language plpgsql as $$ begin
 if (new.id,new.tenant_id,new.program_id,new.account_id,new.topup_id,new.legacy,new.policy,new.credit_sequence,new.initial_bonus) is distinct from (old.id,old.tenant_id,old.program_id,old.account_id,old.topup_id,old.legacy,old.policy,old.credit_sequence,old.initial_bonus) or (not old.legacy and new.initial_principal<>old.initial_principal) then raise exception 'wallet funding source is immutable' using errcode='23514';end if;
 return new;
end $$;
create trigger wallet_lot_identity_immutable before update on wallet_funding_lots for each row execute function wallet_lot_identity_guard();
