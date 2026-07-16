create table if not exists tenant_product_subscription_items (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid not null references tenant_product_subscriptions(id) on delete cascade,
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    scope_type text not null,
    store_id uuid null,
    quantity integer not null default 1,
    unit_amount numeric(12, 2) not null default 0,
    amount numeric(12, 2) not null default 0,
    currency text not null default 'SGD',
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ck_tenant_product_subscription_items_scope check (scope_type in ('tenant', 'store')),
    constraint ck_tenant_product_subscription_items_store_required check (
        (scope_type = 'store' and store_id is not null)
        or (scope_type = 'tenant' and store_id is null)
    ),
    constraint ck_tenant_product_subscription_items_quantity check (quantity > 0),
    constraint ck_tenant_product_subscription_items_amount check (unit_amount >= 0 and amount >= 0),
    constraint ck_tenant_product_subscription_items_currency check (currency = upper(currency) and length(currency) = 3),
    constraint ck_tenant_product_subscription_items_status check (status in ('active', 'suspended', 'cancelled', 'expired')),
    constraint fk_tenant_product_subscription_items_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id)
);

create index if not exists ix_tenant_product_subscription_items_tenant
    on tenant_product_subscription_items (tenant_id, app_key, status);

create index if not exists ix_tenant_product_subscription_items_subscription
    on tenant_product_subscription_items (subscription_id, status);

create unique index if not exists ux_tenant_product_subscription_items_store_scope
    on tenant_product_subscription_items (subscription_id, store_id)
    where scope_type = 'store';

with active_store_counts as (
    select tenant_id, count(*)::numeric as store_count
    from stores
    where status = 'active'
      and deleted_at is null
    group by tenant_id
),
subscription_duration as (
    select
        subscription.id as subscription_id,
        subscription.tenant_id,
        subscription.app_key,
        subscription.billing_cycle,
        subscription.status,
        subscription.amount as subscription_amount,
        subscription.currency as subscription_currency,
        case subscription.billing_cycle
            when 'monthly' then greatest(
                1,
                (
                    extract(year from age(subscription.current_period_end, subscription.current_period_start))::integer * 12
                    + extract(month from age(subscription.current_period_end, subscription.current_period_start))::integer
                )
            )
            when 'yearly' then greatest(
                1,
                extract(year from age(subscription.current_period_end, subscription.current_period_start))::integer
            )
            else 1
        end as duration_count
    from tenant_product_subscriptions subscription
),
backfill_items as (
    select
        duration.subscription_id,
        duration.tenant_id,
        duration.app_key,
        store.id as store_id,
        duration.status,
        case
            when duration.billing_cycle in ('monthly', 'yearly') and price.amount is not null
                then price.amount
            when coalesce(store_count.store_count, 0) > 0
                then round(duration.subscription_amount / store_count.store_count, 2)
            else duration.subscription_amount
        end as unit_amount,
        case
            when duration.billing_cycle in ('monthly', 'yearly') and price.amount is not null
                then price.amount * duration.duration_count
            when coalesce(store_count.store_count, 0) > 0
                then round(duration.subscription_amount / store_count.store_count, 2)
            else duration.subscription_amount
        end as amount,
        case
            when duration.billing_cycle in ('monthly', 'yearly') and price.currency is not null
                then price.currency
            when length(trim(coalesce(duration.subscription_currency, ''))) = 3
                then upper(trim(duration.subscription_currency))
            else 'SGD'
        end as currency
    from subscription_duration duration
    join stores store
      on store.tenant_id = duration.tenant_id
     and store.status = 'active'
     and store.deleted_at is null
    left join platform_product_line_prices price
      on price.app_key = duration.app_key
     and price.billing_cycle = duration.billing_cycle
     and price.status = 'active'
    left join active_store_counts store_count
      on store_count.tenant_id = duration.tenant_id
)
insert into tenant_product_subscription_items (
    subscription_id,
    tenant_id,
    app_key,
    scope_type,
    store_id,
    quantity,
    unit_amount,
    amount,
    currency,
    status
)
select
    subscription_id,
    tenant_id,
    app_key,
    'store',
    store_id,
    1,
    unit_amount,
    amount,
    currency,
    status
from backfill_items
on conflict (subscription_id, store_id) where scope_type = 'store' do nothing;
