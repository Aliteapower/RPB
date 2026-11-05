create table workforce_statutory_policies (
 id uuid primary key, tenant_id uuid not null references tenants(id),
 name text not null check(length(trim(name)) between 1 and 100),
 year integer not null check(year between 2000 and 2100), currency text not null check(currency='SGD'),
 effective_from date not null,effective_to date not null,enabled boolean not null,
 version bigint not null check(version>0),config jsonb not null check(jsonb_typeof(config)='object'),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),check(effective_from<=effective_to)
);
create index ix_workforce_statutory_period on workforce_statutory_policies(tenant_id,effective_from,effective_to) where enabled;
create table workforce_statutory_policy_revisions (
 policy_id uuid not null,tenant_id uuid not null,version bigint not null check(version>0),
 snapshot jsonb not null check(jsonb_typeof(snapshot)='object'),created_at timestamptz not null default now(),
 primary key(policy_id,version),foreign key(policy_id,tenant_id) references workforce_statutory_policies(id,tenant_id)
);
