-- Additive, explicit adoption only. No existing facts, policies, accounts or wages are rewritten.
alter table workforce_positions add column attendance_mode text null check(attendance_mode in ('legacy','onsite','field','home','free'));
create table workforce_work_modes (
 id uuid primary key,tenant_id uuid not null,employee_id uuid not null,effective_from date not null,
 mode text not null check(mode in ('legacy','onsite','field','home','free')),origin text not null check(origin in ('position','personal')),
 position_id uuid null,position_version bigint not null default 0 check(position_version>=0),version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,employee_id,effective_from),unique(tenant_id,employee_id,version),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(position_id,tenant_id) references workforce_positions(id,tenant_id)
);
create index ix_workforce_work_mode_date on workforce_work_modes(tenant_id,employee_id,effective_from desc);
create table workforce_clock_sites (
 tenant_id uuid not null,store_id uuid not null,point jsonb not null check(jsonb_typeof(point)='object'),version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,store_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table workforce_home_addresses (
 id uuid primary key,tenant_id uuid not null,employee_id uuid not null,point jsonb not null check(jsonb_typeof(point)='object'),effective_from date not null,
 status text not null check(status in ('pending','approved','rejected')),version bigint not null check(version in (1,2)),
 applicant_id uuid not null,reviewer_id uuid null,review_note text not null default '' check(length(review_note)<=500),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(applicant_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(reviewer_id,tenant_id) references auth_accounts(id,tenant_id),
 check((status='pending' and version=1 and reviewer_id is null) or (status<>'pending' and version=2 and reviewer_id is not null and reviewer_id<>applicant_id and length(review_note)>0))
);
create unique index ux_workforce_home_approved_date on workforce_home_addresses(tenant_id,employee_id,effective_from) where status='approved';
create index ix_workforce_home_employee on workforce_home_addresses(tenant_id,employee_id,effective_from desc,created_at desc);
alter table workforce_attendance_sessions add column clock_policy jsonb null check(clock_policy is null or jsonb_typeof(clock_policy)='object');
create table workforce_clock_locations (
 tenant_id uuid not null,event_id uuid not null,location jsonb null check(location is null or jsonb_typeof(location)='object'),
 policy jsonb null check(policy is null or jsonb_typeof(policy)='object'),created_at timestamptz not null default now(),
 primary key(tenant_id,event_id),foreign key(tenant_id,event_id) references workforce_clock_events(tenant_id,id)
);
