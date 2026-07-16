create table if not exists platform_product_line_prices (
    id uuid primary key default gen_random_uuid(),
    app_key text not null references platform_apps(app_key),
    billing_cycle text not null,
    amount numeric(12, 2) not null,
    currency text not null default 'SGD',
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ux_platform_product_line_prices_scope unique (app_key, billing_cycle),
    constraint ck_platform_product_line_prices_cycle check (billing_cycle in ('monthly', 'yearly')),
    constraint ck_platform_product_line_prices_amount check (amount >= 0),
    constraint ck_platform_product_line_prices_currency check (currency = upper(currency) and length(currency) = 3),
    constraint ck_platform_product_line_prices_status check (status in ('active', 'disabled'))
);

insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
values
    ('reservation_queue', 'monthly', 0.00, 'SGD', 'active'),
    ('reservation_queue', 'yearly', 0.00, 'SGD', 'active')
on conflict (app_key, billing_cycle) do nothing;
