-- Additive template governance. Existing salary JSON, profiles and payroll snapshots stay intact.
create table workforce_pay_template_masters (
 id uuid primary key,tenant_id uuid not null references tenants(id),version bigint not null check(version>0),snapshot jsonb not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),unique(id,tenant_id)
);
create index ix_pay_master_tenant on workforce_pay_template_masters(tenant_id);
create table workforce_pay_template_releases (
 master_id uuid not null,tenant_id uuid not null,version bigint not null check(version>0),draft_version bigint not null check(draft_version>0),snapshot jsonb not null,
 published_at timestamptz not null,published_by uuid not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(master_id,version),unique(master_id,version,tenant_id),unique(master_id,draft_version),
 foreign key(master_id,tenant_id) references workforce_pay_template_masters(id,tenant_id),foreign key(published_by,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_pay_release_tenant on workforce_pay_template_releases(tenant_id,master_id,version desc);
create table workforce_pay_template_release_companies (
 master_id uuid not null,release_version bigint not null,tenant_id uuid not null,company_id uuid not null,
 primary key(master_id,release_version,company_id),unique(master_id,release_version,tenant_id,company_id),
 foreign key(master_id,release_version,tenant_id) references workforce_pay_template_releases(master_id,version,tenant_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id)
);
create table workforce_pay_template_release_stores (
 master_id uuid not null,release_version bigint not null,tenant_id uuid not null,company_id uuid not null,store_id uuid not null,
 primary key(master_id,release_version,store_id),
 foreign key(master_id,release_version,tenant_id,company_id) references workforce_pay_template_release_companies(master_id,release_version,tenant_id,company_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table workforce_pay_template_adoptions (
 id uuid primary key,tenant_id uuid not null,master_id uuid not null,release_version bigint not null,company_id uuid not null,store_id uuid not null,
 store_version bigint not null check(store_version>0),materialized_template_id uuid not null unique,snapshot jsonb not null,enabled_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,store_id,master_id,store_version),unique(id,tenant_id,company_id,store_id),
 foreign key(master_id,release_version,tenant_id,company_id) references workforce_pay_template_release_companies(master_id,release_version,tenant_id,company_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(materialized_template_id,tenant_id,company_id) references workforce_pay_templates(id,tenant_id,company_id),
 foreign key(enabled_by,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_pay_adoption_current on workforce_pay_template_adoptions(tenant_id,store_id,master_id,store_version desc);
create table workforce_pay_template_commands (
 id uuid primary key,tenant_id uuid not null,actor_id uuid not null,store_id uuid not null,action text not null check(action in ('publish','enable')),
 payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_pay_template_command_tenant on workforce_pay_template_commands(tenant_id,store_id);
create function workforce_pay_template_immutable() returns trigger language plpgsql as $$begin raise exception 'immutable payroll template governance snapshot' using errcode='23514';end$$;
create trigger pay_release_immutable before update or delete on workforce_pay_template_releases for each row execute function workforce_pay_template_immutable();
create trigger pay_release_company_immutable before update or delete on workforce_pay_template_release_companies for each row execute function workforce_pay_template_immutable();
create trigger pay_release_store_immutable before update or delete on workforce_pay_template_release_stores for each row execute function workforce_pay_template_immutable();
create trigger pay_adoption_immutable before update or delete on workforce_pay_template_adoptions for each row execute function workforce_pay_template_immutable();
create trigger pay_template_command_immutable before update or delete on workforce_pay_template_commands for each row execute function workforce_pay_template_immutable();
create function workforce_pay_materialized_immutable() returns trigger language plpgsql as $$begin
 if exists(select 1 from workforce_pay_template_adoptions a where a.materialized_template_id=old.id) then raise exception 'managed payroll template is immutable' using errcode='23514';end if;
 if TG_OP='UPDATE' then return new;end if;return old;
end$$;
create trigger pay_materialized_immutable before update or delete on workforce_pay_templates for each row execute function workforce_pay_materialized_immutable();
