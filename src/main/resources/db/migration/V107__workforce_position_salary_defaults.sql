create table workforce_position_salary_defaults (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,position_id uuid not null,effective_from date not null,
 template_id uuid not null,template_version bigint not null check(template_version>0),enabled boolean not null,version bigint not null check(version>0),
 snapshot jsonb not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,company_id,position_id,effective_from),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(position_id,tenant_id) references workforce_positions(id,tenant_id),
 foreign key(template_id,tenant_id,company_id) references workforce_pay_templates(id,tenant_id,company_id)
);
create index ix_workforce_position_salary_template on workforce_position_salary_defaults(tenant_id,company_id,template_id);
