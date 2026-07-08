alter table tenant_product_subscription_items
    add column if not exists billing_cycle text null,
    add column if not exists current_period_start timestamptz null,
    add column if not exists current_period_end timestamptz null,
    add column if not exists payment_note text null;

update tenant_product_subscription_items item
set billing_cycle = coalesce(item.billing_cycle, subscription.billing_cycle),
    current_period_start = coalesce(item.current_period_start, subscription.current_period_start),
    current_period_end = coalesce(item.current_period_end, subscription.current_period_end),
    payment_note = coalesce(item.payment_note, subscription.payment_note)
from tenant_product_subscriptions subscription
where subscription.id = item.subscription_id;

alter table tenant_product_subscription_items
    drop constraint if exists ck_tenant_product_subscription_items_cycle,
    add constraint ck_tenant_product_subscription_items_cycle check (
        billing_cycle is null
        or billing_cycle in ('monthly', 'yearly', 'legacy_grant', 'manual')
    );

alter table tenant_product_subscription_items
    drop constraint if exists ck_tenant_product_subscription_items_period,
    add constraint ck_tenant_product_subscription_items_period check (
        current_period_start is null
        or current_period_end is null
        or current_period_end > current_period_start
    );

create index if not exists ix_tenant_product_subscription_items_store_app
    on tenant_product_subscription_items (tenant_id, store_id, app_key)
    where scope_type = 'store';

create index if not exists ix_tenant_product_subscription_items_period_end
    on tenant_product_subscription_items (tenant_id, app_key, current_period_end)
    where scope_type = 'store' and current_period_end is not null;
