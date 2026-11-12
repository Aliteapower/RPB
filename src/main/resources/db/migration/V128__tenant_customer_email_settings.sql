-- Tenant-wide shared mail; keep historical store rows for compatibility and conflict resolution.
create table tenant_customer_email_settings (
    tenant_id uuid primary key references tenants(id),
    enabled boolean not null default false,
    provider text not null default 'smtp' check (provider = 'smtp'),
    from_email text,
    from_name text,
    smtp_host text,
    smtp_port integer not null default 587 check (smtp_port between 1 and 65535),
    smtp_username text,
    smtp_password_secret text,
    smtp_start_tls boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0
);

-- Only unambiguous configured tenants are promoted. Differing credentials are never chosen arbitrarily.
with configurations as (
    select distinct tenant_id, enabled, provider, from_email, from_name, smtp_host, smtp_port,
                    smtp_username, smtp_password_secret, smtp_start_tls
    from store_customer_email_settings
    where deleted_at is null and nullif(trim(from_email), '') is not null
      and nullif(trim(smtp_host), '') is not null
), unique_tenants as (
    select tenant_id from configurations group by tenant_id having count(*) = 1
)
insert into tenant_customer_email_settings
    (tenant_id, enabled, provider, from_email, from_name, smtp_host, smtp_port,
     smtp_username, smtp_password_secret, smtp_start_tls)
select c.* from configurations c join unique_tenants u using (tenant_id);
