create function workforce_attendance_arrangement_valid(a jsonb) returns boolean
language plpgsql immutable as $$
declare w integer; b integer; o integer; days integer; unique_days integer;
begin
 if a is null then return true; end if;
 if jsonb_typeof(a)<>'object' or jsonb_typeof(a->'weekdays')<>'array' then return false; end if;
 w:=(a->>'standardWorkMinutes')::integer; b:=(a->>'unpaidBreakMinutes')::integer;
 o:=(a->>'unpaidBreakStartOffsetMinutes')::integer;
 if w is null or b is null or w<=0 or b<0 or w+b>1440 then return false; end if;
 if (b>0 and (o is null or o<0 or o>w)) or (b=0 and o is not null) then return false; end if;
 if not coalesce((a->>'startTime') ~ '^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$',false) then return false; end if;
 select count(*),count(distinct value::integer) into days,unique_days from jsonb_array_elements_text(a->'weekdays');
 if days<1 or days>7 or days<>unique_days then return false; end if;
 return not exists(select 1 from jsonb_array_elements_text(a->'weekdays') d where d.value is null or d.value::integer not between 1 and 7);
exception when others then return false;
end $$;
-- Immutable policy snapshots; no backfill, data removal or runtime enablement.
create table workforce_attendance_policies (
 id uuid not null,tenant_id uuid not null,employee_id uuid null,company_id uuid not null,source_store_id uuid not null,
 effective_from date not null,mode text not null check(mode in ('clock','default_normal','contract','free')),
 origin text not null check(origin in ('store_default','personal','adopted_default')),
 source_default_id uuid null,source_default_version bigint not null default 0,
 arrangement jsonb null check(workforce_attendance_arrangement_valid(arrangement)),timezone text not null,
 version bigint not null check(version>0),actor_id uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,id),
 unique(tenant_id,id,source_store_id,company_id,version),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(source_store_id,tenant_id) references stores(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id),
 foreign key(tenant_id,source_default_id,source_store_id,company_id,source_default_version) references workforce_attendance_policies(tenant_id,id,source_store_id,company_id,version),
 check((origin='store_default')=(employee_id is null)),
 check((origin='adopted_default' and source_default_id is not null and source_default_version>0) or (origin<>'adopted_default' and source_default_id is null and source_default_version=0)),
 check(mode in ('contract','free') or arrangement is not null)
);
create unique index ux_workforce_policy_default_version on workforce_attendance_policies(tenant_id,source_store_id,version) where employee_id is null;
create unique index ux_workforce_policy_default_date on workforce_attendance_policies(tenant_id,source_store_id,effective_from) where employee_id is null;
create unique index ux_workforce_policy_employee_version on workforce_attendance_policies(tenant_id,employee_id,version) where employee_id is not null;
create unique index ux_workforce_policy_employee_date on workforce_attendance_policies(tenant_id,employee_id,effective_from) where employee_id is not null;
create index ix_workforce_policy_company on workforce_attendance_policies(tenant_id,company_id,employee_id,effective_from);
create table workforce_attendance_policy_commands (
 tenant_id uuid not null,command_id uuid not null,store_id uuid not null,employee_id uuid null,actor_id uuid not null,
 request_json jsonb not null check(jsonb_typeof(request_json)='object'),response_json jsonb not null check(jsonb_typeof(response_json)='object'),
 created_at timestamptz not null default now(),primary key(tenant_id,command_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);

-- Check company membership at adoption, without freezing a store's future company assignment.
create function workforce_attendance_policy_scope() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from stores where id=new.source_store_id and tenant_id=new.tenant_id and operating_entity_id=new.company_id) then
  raise exception 'attendance policy store company mismatch' using errcode='23514';
 end if;
 if new.origin='adopted_default' and not exists(select 1 from workforce_attendance_policies where tenant_id=new.tenant_id and id=new.source_default_id and origin='store_default') then
  raise exception 'attendance policy default source required' using errcode='23514';
 end if;
 return new;
end $$;
create trigger workforce_attendance_policy_scope_before_insert before insert on workforce_attendance_policies
 for each row execute function workforce_attendance_policy_scope();
