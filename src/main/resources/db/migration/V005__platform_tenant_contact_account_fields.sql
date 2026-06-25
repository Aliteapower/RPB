alter table tenants
    add column contact_phone text null,
    add column address text null,
    add column principal_name text null;

create index ix_tenants_keyword_active
    on tenants (lower(tenant_code), lower(display_name))
    where deleted_at is null;
