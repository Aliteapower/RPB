create table workforce_shifts (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,name text not null check(length(name) between 1 and 80),
 snapshot jsonb not null,enabled boolean not null,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),unique(id,tenant_id,store_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_workforce_shift_store on workforce_shifts(tenant_id,store_id);
create table workforce_schedules (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,company_id uuid not null,shift_id uuid not null,
 work_date date not null,start_at timestamptz not null,end_at timestamptz not null,timezone text not null,shift_snapshot jsonb not null,
 status text not null check(status in ('scheduled','cancelled')),version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,store_id,employee_id),unique(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(shift_id,tenant_id,store_id) references workforce_shifts(id,tenant_id,store_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),check(end_at>start_at and end_at<=start_at+interval '24 hours')
);
create index ix_workforce_schedule_store_date on workforce_schedules(tenant_id,store_id,work_date,id);
create index ix_workforce_schedule_employee_time on workforce_schedules(tenant_id,employee_id,start_at,end_at) where status='scheduled';
create table workforce_attendance_sessions (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,schedule_id uuid not null unique,
 in_at timestamptz not null,out_at timestamptz null,break_started_at timestamptz null,breaks jsonb not null default '[]',override_break boolean not null default false,
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id),foreign key(schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 check(out_at is null or out_at>in_at),check(break_started_at is null or out_at is null)
);
create unique index ux_workforce_one_active_session on workforce_attendance_sessions(tenant_id,employee_id) where out_at is null;
create index ix_workforce_session_employee on workforce_attendance_sessions(tenant_id,employee_id,in_at,out_at);
create table workforce_clock_events (
 id uuid not null,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,schedule_id uuid not null,session_id uuid not null,
 action text not null check(action in ('IN','BREAK_START','BREAK_END','OUT')),occurred_at timestamptz not null,actor_id uuid not null,response_json jsonb not null,
 created_at timestamptz not null default now(),primary key(tenant_id,id),
 foreign key(session_id,tenant_id) references workforce_attendance_sessions(id,tenant_id),
 foreign key(schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_event_schedule on workforce_clock_events(tenant_id,schedule_id,occurred_at,id);
create table workforce_corrections (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,schedule_id uuid not null,
 based_session_version bigint not null check(based_session_version>=0),in_at timestamptz not null,out_at timestamptz not null,
 breaks jsonb not null default '[]',override_break boolean not null default false,reason text not null check(length(reason) between 1 and 500),
 status text not null check(status in ('pending','approved','rejected','withdrawn')),applicant_id uuid not null,reviewer_id uuid null,review_note text null,
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 foreign key(applicant_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(reviewer_id,tenant_id) references auth_accounts(id,tenant_id),
 check(out_at>in_at and out_at<=in_at+interval '36 hours'),check(review_note is null or length(review_note)<=500)
);
create unique index ux_workforce_pending_correction on workforce_corrections(schedule_id) where status='pending';
create index ix_workforce_correction_store on workforce_corrections(tenant_id,store_id,created_at);
create table workforce_schedule_copies (
 id uuid not null,tenant_id uuid not null,store_id uuid not null,source_week date not null,target_week date not null,response_json jsonb not null,
 actor_id uuid not null,created_at timestamptz not null default now(),primary key(tenant_id,id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
