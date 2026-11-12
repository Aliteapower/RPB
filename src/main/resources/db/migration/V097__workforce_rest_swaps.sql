-- Immutable employee-level rest/makeup links. Existing schedules and entitlements stay separate.
create table workforce_rest_swaps (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,
 rest_date date not null,makeup_date date not null,makeup_schedule_id uuid not null,
 cancelled_schedule_ids jsonb not null default '[]',command_id uuid not null,created_at timestamptz not null default now(),
 check(rest_date<>makeup_date),check(abs(makeup_date-rest_date)<=30),
 unique(tenant_id,employee_id,rest_date),unique(tenant_id,employee_id,makeup_date),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(makeup_schedule_id,tenant_id,store_id,employee_id) references workforce_schedules(id,tenant_id,store_id,employee_id),
 foreign key(command_id) references workforce_bulk_schedule_commands(id)
);
create index ix_workforce_rest_swap_store on workforce_rest_swaps(tenant_id,store_id,rest_date,makeup_date);
