-- Additive workforce-only data. No grants, entitlement or other product data changes.
create table workforce_leave_types (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,
 name text not null check(length(name) between 1 and 100),paid boolean not null,enabled boolean not null,
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_workforce_leave_type_store on workforce_leave_types(tenant_id,store_id);
create table workforce_leaves (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,schedule_id uuid not null,employee_id uuid not null,type_id uuid not null,
 type_name text not null check(length(type_name) between 1 and 100),paid boolean not null,start_at timestamptz not null,end_at timestamptz not null,
 reason text not null check(length(reason) between 1 and 500),
 status text not null check(status in ('pending','approved','rejected','withdrawn','cancel_requested','cancelled')),
 last_action text not null default 'submit',applicant_id uuid not null,reviewer_id uuid null,review_note text null check(length(review_note)<=500),
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 foreign key(type_id,tenant_id,store_id) references workforce_leave_types(id,tenant_id,store_id),
 foreign key(applicant_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(reviewer_id,tenant_id) references auth_accounts(id,tenant_id),
 check(end_at>start_at and end_at<=start_at+interval '24 hours')
);
create unique index ux_workforce_leave_pending on workforce_leaves(tenant_id,schedule_id) where status in ('pending','cancel_requested');
create index ix_workforce_leave_scope on workforce_leaves(tenant_id,store_id,schedule_id);
create index ix_workforce_leave_employee on workforce_leaves(tenant_id,employee_id,start_at,end_at);
create table workforce_calendar_events (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,event_date date not null,
 kind text not null check(kind in ('public_holiday','store_rest')),name text not null check(length(name) between 1 and 100),enabled boolean not null,
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,store_id,event_date,kind),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table workforce_approvals (
 tenant_id uuid not null,store_id uuid not null,schedule_id uuid not null,employee_id uuid not null,
 version bigint not null check(version>0),source_fingerprint text not null check(length(source_fingerprint)=64),
 work_seconds bigint not null check(work_seconds>=0),paid_leave_seconds bigint not null check(paid_leave_seconds>=0),unpaid_leave_seconds bigint not null check(unpaid_leave_seconds>=0),
 snapshot jsonb not null,reviewer_id uuid not null,note text not null check(length(note) between 1 and 500),approved_at timestamptz not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(schedule_id,version),foreign key(schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 foreign key(reviewer_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_approval_scope on workforce_approvals(tenant_id,store_id,schedule_id,version desc);
