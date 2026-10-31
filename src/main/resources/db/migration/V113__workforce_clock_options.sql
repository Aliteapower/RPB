-- Additive only: NULL retains the exact former singleton policy and old JSON evidence.
create function workforce_clock_modes_valid(default_mode text,modes text[]) returns boolean
language sql immutable as $$
 select modes is null or (default_mode is not null and cardinality(modes) between 1 and 3
  and array_ndims(modes)=1 and array_position(modes,null) is null
  and default_mode=any(modes) and modes <@ array['legacy','onsite','home','field','free']::text[]
  and cardinality(modes)=(select count(distinct m) from unnest(modes) m)
  and (cardinality(modes)=1 or not (modes && array['legacy','free']::text[])))
$$;
alter table workforce_positions add column attendance_modes text[] null,
 add constraint ck_workforce_position_modes check(workforce_clock_modes_valid(attendance_mode,attendance_modes));
alter table workforce_work_modes add column allowed_modes text[] null,
 add constraint ck_workforce_assignment_modes check(workforce_clock_modes_valid(mode,allowed_modes));
alter table workforce_clock_events
 add column clock_mode text null check(clock_mode in ('legacy','onsite','home','field')),
 add column requested_mode text null check(requested_mode in ('legacy','onsite','home','field'));
create table workforce_work_mode_commands (
 tenant_id uuid not null,command_id uuid not null,store_id uuid not null,employee_id uuid not null,actor_id uuid not null,
 request_json jsonb not null check(jsonb_typeof(request_json)='object'),
 response_json jsonb not null check(jsonb_typeof(response_json)='object'),
 created_at timestamptz not null default now(),
 primary key(tenant_id,command_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_mode_commands_employee on workforce_work_mode_commands(tenant_id,employee_id,created_at);
