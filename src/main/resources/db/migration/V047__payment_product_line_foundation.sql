insert into platform_apps (
    app_key,
    app_name,
    status,
    default_entry_route,
    description,
    sort_order,
    config_json
)
values (
    'payment',
    'PayNow 支付产线',
    'active',
    '/stores/:storeId/payments',
    'PayNow QR collection, quick payment terminal, payment proof review, and reusable payment sessions.',
    30,
    '{"entryPermissions":["payment.intent.view","payment.intent.create","payment.verification.review"]}'::jsonb
)
on conflict (app_key) do update
set
    app_name = excluded.app_name,
    status = excluded.status,
    default_entry_route = excluded.default_entry_route,
    description = excluded.description,
    sort_order = excluded.sort_order,
    config_json = excluded.config_json,
    updated_at = now();

insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
values
    ('payment', 'monthly', 0.00, 'SGD', 'active'),
    ('payment', 'yearly', 0.00, 'SGD', 'active')
on conflict (app_key, billing_cycle) do nothing;

create table if not exists payment_method_profiles (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid null,
    method text not null,
    status text not null default 'disabled',
    paynow_type text null,
    paynow_mobile text null,
    paynow_uen text null,
    merchant_name text null,
    currency text not null default 'SGD',
    config_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_method_profiles_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_payment_method_profiles_id_scope unique (id, tenant_id, store_id),
    constraint ck_payment_method_profiles_method check (method in ('paynow')),
    constraint ck_payment_method_profiles_status check (status in ('active', 'disabled')),
    constraint ck_payment_method_profiles_paynow_type check (
        method <> 'paynow'
        or paynow_type in ('mobile', 'uen')
    ),
    constraint ck_payment_method_profiles_currency check (currency = upper(currency) and length(currency) = 3),
    constraint ck_payment_method_profiles_active_fields check (
        status <> 'active'
        or (
            merchant_name is not null
            and btrim(merchant_name) <> ''
            and (
                (paynow_type = 'mobile' and paynow_mobile is not null and btrim(paynow_mobile) <> '')
                or (paynow_type = 'uen' and paynow_uen is not null and btrim(paynow_uen) <> '')
            )
        )
    )
);

create unique index if not exists ux_payment_method_profiles_store
    on payment_method_profiles (tenant_id, store_id, method)
    where store_id is not null;

create unique index if not exists ux_payment_method_profiles_tenant_default
    on payment_method_profiles (tenant_id, method)
    where store_id is null;

create index if not exists ix_payment_method_profiles_lookup
    on payment_method_profiles (tenant_id, store_id, method, status);

create table if not exists payment_intents (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_no text not null,
    source_type text not null,
    source_id uuid null,
    method text not null,
    amount numeric(12, 2) not null,
    currency text not null default 'SGD',
    payment_reference text not null,
    status text not null default 'pending',
    expires_at timestamptz null,
    idempotency_key text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_intents_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_payment_intents_id_scope unique (id, tenant_id, store_id),
    constraint uq_payment_intents_tenant_intent_no unique (tenant_id, intent_no),
    constraint uq_payment_intents_tenant_reference unique (tenant_id, payment_reference),
    constraint uq_payment_intents_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_payment_intents_source check (
        source_type in ('quick_pay', 'generic_merchant', 'pos_order', 'reservation_deposit', 'platform_billing')
    ),
    constraint ck_payment_intents_method check (method in ('paynow')),
    constraint ck_payment_intents_status check (
        status in ('pending', 'awaiting_verification', 'paid', 'expired', 'cancelled', 'failed')
    ),
    constraint ck_payment_intents_amount check (amount > 0),
    constraint ck_payment_intents_currency check (currency = upper(currency) and length(currency) = 3)
);

create index if not exists ix_payment_intents_store_status
    on payment_intents (tenant_id, store_id, status, created_at desc);

create index if not exists ix_payment_intents_source
    on payment_intents (tenant_id, store_id, source_type, source_id);

create table if not exists payment_display_counters (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    business_date date not null,
    next_display_number integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_display_counters_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_payment_display_counters_scope unique (tenant_id, store_id, business_date),
    constraint ck_payment_display_counters_next check (next_display_number > 0)
);

create table if not exists payment_sessions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_id uuid not null,
    session_no text not null,
    display_number integer not null,
    business_date date not null,
    terminal_code text null,
    cashier_name text null,
    status text not null default 'pending',
    qr_payloads_json jsonb not null default '{}'::jsonb,
    expires_at timestamptz not null,
    idempotency_key text not null,
    created_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_sessions_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_payment_sessions_intent_scope foreign key (intent_id, tenant_id, store_id) references payment_intents(id, tenant_id, store_id) on delete cascade,
    constraint uq_payment_sessions_id_scope unique (id, tenant_id, store_id),
    constraint uq_payment_sessions_tenant_session_no unique (tenant_id, session_no),
    constraint uq_payment_sessions_display unique (tenant_id, store_id, business_date, display_number),
    constraint uq_payment_sessions_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_payment_sessions_status check (
        status in ('pending', 'awaiting_verification', 'paid', 'expired', 'cancelled', 'failed')
    ),
    constraint ck_payment_sessions_display_number check (display_number > 0)
);

create index if not exists ix_payment_sessions_intent
    on payment_sessions (tenant_id, store_id, intent_id, created_at desc);

create index if not exists ix_payment_sessions_active
    on payment_sessions (tenant_id, store_id, status, expires_at);

create table if not exists payment_proofs (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_id uuid not null,
    session_id uuid null,
    status text not null default 'submitted',
    storage_key text null,
    file_name text null,
    content_type text null,
    expected_reference text null,
    expected_amount numeric(12, 2) null,
    submitted_by uuid null,
    idempotency_key text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_proofs_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_payment_proofs_intent_scope foreign key (intent_id, tenant_id, store_id) references payment_intents(id, tenant_id, store_id) on delete cascade,
    constraint fk_payment_proofs_session_scope foreign key (session_id, tenant_id, store_id) references payment_sessions(id, tenant_id, store_id),
    constraint uq_payment_proofs_id_scope unique (id, tenant_id, store_id),
    constraint uq_payment_proofs_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_payment_proofs_status check (status in ('submitted', 'matched', 'confirmed', 'rejected')),
    constraint ck_payment_proofs_expected_amount check (expected_amount is null or expected_amount > 0)
);

create index if not exists ix_payment_proofs_intent
    on payment_proofs (tenant_id, store_id, intent_id, created_at desc);

create table if not exists payment_ocr_results (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    proof_id uuid not null,
    extracted_reference text null,
    extracted_amount numeric(12, 2) null,
    extracted_paid_at timestamptz null,
    bank_code text null,
    confidence numeric(5, 4) null,
    raw_text text null,
    raw_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint fk_payment_ocr_results_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_payment_ocr_results_proof_scope foreign key (proof_id, tenant_id, store_id) references payment_proofs(id, tenant_id, store_id) on delete cascade,
    constraint ck_payment_ocr_results_amount check (extracted_amount is null or extracted_amount > 0),
    constraint ck_payment_ocr_results_confidence check (confidence is null or (confidence >= 0 and confidence <= 1))
);

create index if not exists ix_payment_ocr_results_proof
    on payment_ocr_results (tenant_id, store_id, proof_id, created_at desc);

create table if not exists payment_verifications (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_id uuid not null,
    proof_id uuid null,
    status text not null default 'pending',
    matched_reference boolean null,
    matched_amount boolean null,
    reviewed_by uuid null,
    reviewed_at timestamptz null,
    rejection_reason text null,
    idempotency_key text null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_verifications_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_payment_verifications_intent_scope foreign key (intent_id, tenant_id, store_id) references payment_intents(id, tenant_id, store_id) on delete cascade,
    constraint fk_payment_verifications_proof_scope foreign key (proof_id, tenant_id, store_id) references payment_proofs(id, tenant_id, store_id),
    constraint uq_payment_verifications_id_scope unique (id, tenant_id, store_id),
    constraint ck_payment_verifications_status check (status in ('pending', 'confirmed', 'rejected')),
    constraint ck_payment_verifications_review_fields check (
        status = 'pending'
        or reviewed_at is not null
    )
);

create unique index if not exists ux_payment_verifications_tenant_idempotency
    on payment_verifications (tenant_id, idempotency_key)
    where idempotency_key is not null;

create index if not exists ix_payment_verifications_status
    on payment_verifications (tenant_id, store_id, status, created_at desc);

create table if not exists payment_events (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_id uuid null,
    session_id uuid null,
    proof_id uuid null,
    verification_id uuid null,
    event_type text not null,
    actor_user_id uuid null,
    idempotency_key text null,
    event_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint fk_payment_events_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_payment_events_intent_scope foreign key (intent_id, tenant_id, store_id) references payment_intents(id, tenant_id, store_id),
    constraint fk_payment_events_session_scope foreign key (session_id, tenant_id, store_id) references payment_sessions(id, tenant_id, store_id),
    constraint fk_payment_events_proof_scope foreign key (proof_id, tenant_id, store_id) references payment_proofs(id, tenant_id, store_id),
    constraint fk_payment_events_verification_scope foreign key (verification_id, tenant_id, store_id) references payment_verifications(id, tenant_id, store_id),
    constraint ck_payment_events_type check (
        event_type in (
            'intent_created',
            'session_created',
            'intent_expired',
            'intent_cancelled',
            'proof_submitted',
            'verification_pending',
            'verification_confirmed',
            'verification_rejected',
            'source_confirmed',
            'source_rejected'
        )
    )
);

create unique index if not exists ux_payment_events_tenant_idempotency
    on payment_events (tenant_id, event_type, idempotency_key)
    where idempotency_key is not null;

create index if not exists ix_payment_events_intent
    on payment_events (tenant_id, store_id, intent_id, created_at desc);
