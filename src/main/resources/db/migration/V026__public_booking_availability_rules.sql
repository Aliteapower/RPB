create table if not exists store_public_booking_availability_rules (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    rule_type text not null,
    business_date date null,
    day_of_week integer null,
    period_key text null,
    quota_mode text not null,
    quota_percent integer null,
    table_count integer null,
    guest_count integer null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version bigint not null default 0,
    constraint fk_store_public_booking_availability_rules_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_public_booking_availability_rules_type
        check (rule_type in ('weekly', 'date_exception')),
    constraint ck_store_public_booking_availability_rules_target
        check (
            (rule_type = 'weekly' and day_of_week between 1 and 7 and business_date is null)
                or
            (rule_type = 'date_exception' and business_date is not null and day_of_week is null)
        ),
    constraint ck_store_public_booking_availability_rules_mode
        check (quota_mode in ('percentage', 'table_count', 'guest_count', 'closed')),
    constraint ck_store_public_booking_availability_rules_percent
        check (quota_percent is null or quota_percent between 0 and 100),
    constraint ck_store_public_booking_availability_rules_table_count
        check (table_count is null or table_count >= 0),
    constraint ck_store_public_booking_availability_rules_guest_count
        check (guest_count is null or guest_count >= 0)
);

create unique index if not exists ux_store_public_booking_availability_rules_weekly_active
    on store_public_booking_availability_rules (tenant_id, store_id, day_of_week, coalesce(period_key, ''))
    where rule_type = 'weekly' and deleted_at is null;

create unique index if not exists ux_store_public_booking_availability_rules_date_active
    on store_public_booking_availability_rules (tenant_id, store_id, business_date, coalesce(period_key, ''))
    where rule_type = 'date_exception' and deleted_at is null;

create index if not exists ix_store_public_booking_availability_rules_scope
    on store_public_booking_availability_rules (tenant_id, store_id, rule_type);

insert into store_public_booking_availability_rules (
    tenant_id, store_id, rule_type, business_date, day_of_week, period_key, quota_mode,
    quota_percent, table_count, guest_count, created_at, updated_at
)
select tenant_id, store_id, 'date_exception', business_date, null, period_key, quota_mode,
       quota_percent, table_count, guest_count, created_at, updated_at
from store_public_booking_quota_overrides
where deleted_at is null
on conflict do nothing;
