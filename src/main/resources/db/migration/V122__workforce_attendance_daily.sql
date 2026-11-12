-- Independent append-only evaluations. Raw attendance, leaves and approvals are preserved.
create table workforce_attendance_days (
 id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,work_date date not null,
 created_at timestamptz not null default now(),primary key(tenant_id,id),unique(tenant_id,company_id,employee_id,work_date),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
create table workforce_attendance_day_versions (
 tenant_id uuid not null,day_id uuid not null,version bigint not null check(version>0),source_fingerprint text not null,
 mode text not null check(mode in ('clock','default_normal','contract','free')),state text not null check(state in ('system_valid','human_valid','pending','stale','future','rest','unarranged','not_applicable','outside_employment')),
 snapshot jsonb not null,actor_id uuid not null,created_at timestamptz not null default now(),primary key(tenant_id,day_id,version),
 foreign key(tenant_id,day_id) references workforce_attendance_days(tenant_id,id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_attendance_exceptions (
 id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,store_id uuid not null,work_date date not null,
 kind text not null check(kind in ('leave','absence','extra_work')),type_id uuid,type_name text,paid boolean not null,
 start_at timestamptz not null,end_at timestamptz not null check(end_at>start_at),reason text not null check(length(reason) between 1 and 500),
 status text not null check(status in ('pending','approved','rejected','withdrawn','cancel_requested','cancelled')),
 applicant_id uuid not null,reviewer_id uuid,review_note text,last_action text,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,id),
 check((kind='leave' and type_id is not null) or (kind<>'leave' and type_id is null and not paid)),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(store_id,tenant_id,company_id) references stores(id,tenant_id,operating_entity_id),foreign key(type_id,tenant_id,store_id) references workforce_leave_types(id,tenant_id,store_id),foreign key(applicant_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(reviewer_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index workforce_attendance_exceptions_employee_range on workforce_attendance_exceptions(tenant_id,company_id,employee_id,start_at,end_at);
create index workforce_attendance_exceptions_store_month on workforce_attendance_exceptions(tenant_id,store_id,work_date,employee_id);
