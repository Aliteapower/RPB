create table if not exists payment_proof_templates (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    bank_code text not null,
    bank_name text not null,
    locale text not null default 'zh-CN',
    template_name text not null,
    source text not null,
    status text not null default 'draft',
    priority integer not null default 100,
    layout_json jsonb not null default '{}'::jsonb,
    created_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ck_payment_proof_templates_bank_code check (bank_code = lower(bank_code) and length(btrim(bank_code)) between 2 and 32),
    constraint ck_payment_proof_templates_source check (source in ('platform_seed', 'tenant_custom', 'tenant_override')),
    constraint ck_payment_proof_templates_status check (status in ('draft', 'active', 'inactive')),
    constraint ck_payment_proof_templates_priority check (priority between 1 and 999),
    constraint ck_payment_proof_templates_source_scope check (
        (source = 'platform_seed' and tenant_id is null)
        or (source in ('tenant_custom', 'tenant_override') and tenant_id is not null)
    )
);

create unique index if not exists ux_payment_proof_templates_scope_name
    on payment_proof_templates (
        coalesce(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid),
        bank_code,
        locale,
        template_name
    );

create index if not exists ix_payment_proof_templates_effective
    on payment_proof_templates (tenant_id, status, priority, bank_code);

create table if not exists payment_proof_template_samples (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    template_id uuid not null references payment_proof_templates(id) on delete cascade,
    file_name text null,
    content_type text null,
    file_digest text not null,
    ocr_reference text null,
    ocr_amount numeric(12, 2) null,
    raw_text text null,
    layout_json jsonb not null default '{}'::jsonb,
    created_by uuid null,
    created_at timestamptz not null default now(),
    constraint ck_payment_proof_template_samples_content_type check (
        content_type is null or content_type in ('image/png', 'image/jpeg', 'image/webp')
    ),
    constraint ck_payment_proof_template_samples_amount check (ocr_amount is null or ocr_amount > 0)
);

create index if not exists ix_payment_proof_template_samples_template
    on payment_proof_template_samples (tenant_id, template_id, created_at desc);

insert into payment_proof_templates (
    bank_code,
    bank_name,
    locale,
    template_name,
    source,
    status,
    priority,
    layout_json
)
values
    (
        'ocbc',
        'OCBC',
        'zh-CN',
        'OCBC Chinese PayNow',
        'platform_seed',
        'active',
        10,
        '{
            "matchKeywords":["OCBC","您已支付"],
            "successKeywords":["您已支付","支付成功","转账成功"],
            "referencePatterns":["(?:讯息|信息|Message|Ref)\\s*[:：]?\\s*([A-Z0-9.-]{10,32})"],
            "amountPatterns":["您已支付\\s*([0-9OoIl,.]+)\\s*(?:SGD|S6D|SG)"],
            "referenceRoi":[0.00,0.30,1.00,0.66],
            "amountRoi":[0.00,0.15,1.00,0.34]
        }'::jsonb
    ),
    (
        'generic',
        'Generic Singapore Bank',
        'en-SG',
        'Generic English PayNow',
        'platform_seed',
        'active',
        90,
        '{
            "matchKeywords":["Payment successful","Transaction ID","Comment"],
            "successKeywords":["Payment successful","Transfer successful","You have paid","You''ve sent"],
            "referencePatterns":["(?:Comment|Ref|Reference)\\s*[:：]?\\s*\"?([A-Z0-9.-]{10,32})\"?"],
            "amountPatterns":["(?:sent|paid)\\s*S?\\$\\s*([0-9OoIl,.]+)","(?:SGD|S\\$|\\$)\\s*([0-9OoIl,.]+)"],
            "referenceRoi":[0.00,0.34,1.00,0.62],
            "amountRoi":[0.00,0.18,1.00,0.42]
        }'::jsonb
    )
on conflict do nothing;

with required_permissions(permission_code) as (
    values
        ('payment.proof_template.manage')
),
tenant_admin_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code = 'tenant_admin'
     and role.deleted_at is null
    where account.actor_type <> 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from tenant_admin_accounts account
cross join required_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);

update platform_apps
set config_json = jsonb_set(
    coalesce(config_json, '{}'::jsonb),
    '{entryPermissions}',
    coalesce(config_json -> 'entryPermissions', '[]'::jsonb) || '["payment.proof_template.manage"]'::jsonb,
    true
)
where app_key = 'payment'
  and not jsonb_exists(coalesce(config_json -> 'entryPermissions', '[]'::jsonb), 'payment.proof_template.manage');
