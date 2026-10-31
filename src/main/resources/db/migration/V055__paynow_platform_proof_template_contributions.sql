create table if not exists payment_proof_template_contributions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid null references stores(id),
    source_template_id uuid null references payment_proof_templates(id),
    platform_template_id uuid null references payment_proof_templates(id),
    bank_code text not null,
    bank_name text not null,
    locale text not null default 'zh-CN',
    template_name text not null,
    layout_json jsonb not null default '{}'::jsonb,
    sample_file_name text null,
    sample_content_type text null,
    sample_file_digest text null,
    sample_raw_text text null,
    sample_ocr_reference text null,
    sample_ocr_amount numeric(12, 2) null,
    status text not null default 'submitted',
    review_note text null,
    submitted_by uuid null,
    reviewed_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    reviewed_at timestamptz null,
    version integer not null default 0,
    constraint ck_payment_proof_template_contributions_bank_code check (
        bank_code = lower(bank_code) and length(btrim(bank_code)) between 2 and 32
    ),
    constraint ck_payment_proof_template_contributions_status check (
        status in ('submitted', 'accepted', 'rejected', 'withdrawn')
    ),
    constraint ck_payment_proof_template_contributions_content_type check (
        sample_content_type is null or sample_content_type in ('image/png', 'image/jpeg', 'image/webp')
    ),
    constraint ck_payment_proof_template_contributions_amount check (
        sample_ocr_amount is null or sample_ocr_amount > 0
    )
);

create index if not exists ix_payment_proof_template_contributions_review
    on payment_proof_template_contributions (status, created_at desc);

create index if not exists ix_payment_proof_template_contributions_tenant
    on payment_proof_template_contributions (tenant_id, created_at desc);

create index if not exists ix_payment_proof_template_contributions_bank
    on payment_proof_template_contributions (bank_code, locale, status);

with platform_admin_accounts as (
    select account.id as account_id
    from auth_accounts account
    where account.actor_type = 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'platform.payment_proof_template.manage'
from platform_admin_accounts
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = platform_admin_accounts.account_id
      and existing.permission_code = 'platform.payment_proof_template.manage'
      and existing.deleted_at is null
);
