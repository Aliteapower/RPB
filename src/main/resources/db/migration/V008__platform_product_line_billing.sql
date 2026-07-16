create table if not exists tenant_product_subscriptions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    billing_cycle text not null,
    status text not null,
    current_period_start timestamptz null,
    current_period_end timestamptz null,
    amount numeric(12, 2) not null default 0,
    currency text not null default 'SGD',
    payment_note text null,
    operator_user_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ux_tenant_product_subscriptions_scope unique (tenant_id, app_key),
    constraint ck_tenant_product_subscriptions_cycle check (
        billing_cycle in ('monthly', 'yearly', 'legacy_grant', 'manual')
    ),
    constraint ck_tenant_product_subscriptions_status check (
        status in ('active', 'suspended', 'cancelled', 'expired')
    ),
    constraint ck_tenant_product_subscriptions_amount check (amount >= 0),
    constraint ck_tenant_product_subscriptions_currency check (currency = upper(currency) and length(currency) = 3),
    constraint ck_tenant_product_subscriptions_period check (
        current_period_end is null
        or current_period_start is null
        or current_period_end > current_period_start
    ),
    constraint ck_tenant_product_subscriptions_legacy check (
        billing_cycle <> 'legacy_grant'
        or (status = 'active' and current_period_end is null and amount = 0)
    )
);

create index if not exists ix_tenant_product_subscriptions_tenant
    on tenant_product_subscriptions (tenant_id, status, app_key);

create index if not exists ix_tenant_product_subscriptions_period_end
    on tenant_product_subscriptions (status, current_period_end)
    where current_period_end is not null;

create table if not exists tenant_product_subscription_events (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid not null references tenant_product_subscriptions(id) on delete cascade,
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    event_type text not null,
    billing_cycle text null,
    status text null,
    period_start timestamptz null,
    period_end timestamptz null,
    amount numeric(12, 2) null,
    currency text null,
    payment_note text null,
    idempotency_key text not null,
    operator_user_id uuid null,
    event_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint ck_tenant_product_subscription_events_type check (
        event_type in (
            'purchase',
            'renew',
            'suspend',
            'cancel',
            'convert_from_legacy',
            'manual_adjust'
        )
    ),
    constraint ck_tenant_product_subscription_events_amount check (amount is null or amount >= 0),
    constraint ck_tenant_product_subscription_events_currency check (
        currency is null or (currency = upper(currency) and length(currency) = 3)
    ),
    constraint ck_tenant_product_subscription_events_period check (
        period_end is null or period_start is null or period_end > period_start
    )
);

create index if not exists ix_tenant_product_subscription_events_subscription
    on tenant_product_subscription_events (subscription_id, created_at);

create unique index if not exists ux_tenant_product_subscription_events_idempotency
    on tenant_product_subscription_events (tenant_id, app_key, event_type, idempotency_key);

update platform_apps
set
    app_name = '预约排队叫号产线',
    description = '预约、排队、叫号一体化产线',
    updated_at = now()
where app_key = 'reservation_queue';

insert into auth_account_permissions (account_id, permission_code)
select account.id, permission.permission_code
from auth_accounts account
cross join (
    values
        ('platform.product_line.manage'),
        ('platform.billing.manage')
) as permission(permission_code)
where account.actor_type = 'platform_admin'
  and account.deleted_at is null
  and not exists (
      select 1
      from auth_account_permissions existing
      where existing.account_id = account.id
        and existing.permission_code = permission.permission_code
        and existing.deleted_at is null
  );

insert into tenant_product_subscriptions (
    tenant_id,
    app_key,
    billing_cycle,
    status,
    current_period_start,
    current_period_end,
    amount,
    currency,
    payment_note,
    operator_user_id
)
select
    entitlement.tenant_id,
    entitlement.app_key,
    'legacy_grant',
    'active',
    coalesce(entitlement.valid_from, entitlement.enabled_at, entitlement.created_at, now()),
    null,
    0,
    case
        when length(trim(coalesce(store_currency.currency, ''))) = 3
            then upper(trim(store_currency.currency))
        else 'SGD'
    end,
    'Historical permanent grant migrated from App Gate entitlement.',
    entitlement.enabled_by
from tenant_app_entitlements entitlement
left join lateral (
    select stores.currency
    from stores
    where stores.tenant_id = entitlement.tenant_id
      and stores.deleted_at is null
    order by stores.created_at, stores.id
    limit 1
) store_currency on true
where entitlement.app_key = 'reservation_queue'
  and entitlement.status = 'enabled'
  and entitlement.valid_until is null
on conflict (tenant_id, app_key) do nothing;

insert into tenant_product_subscription_events (
    subscription_id,
    tenant_id,
    app_key,
    event_type,
    billing_cycle,
    status,
    period_start,
    period_end,
    amount,
    currency,
    payment_note,
    idempotency_key,
    operator_user_id,
    event_payload
)
select
    subscription.id,
    subscription.tenant_id,
    subscription.app_key,
    'manual_adjust',
    subscription.billing_cycle,
    subscription.status,
    subscription.current_period_start,
    subscription.current_period_end,
    subscription.amount,
    subscription.currency,
    subscription.payment_note,
    'legacy-grant-backfill-' || subscription.tenant_id || '-' || subscription.app_key,
    subscription.operator_user_id,
    jsonb_build_object('source', 'app_gate_foundation', 'legacyGrant', true)
from tenant_product_subscriptions subscription
where subscription.app_key = 'reservation_queue'
  and subscription.billing_cycle = 'legacy_grant'
on conflict (tenant_id, app_key, event_type, idempotency_key) do nothing;
