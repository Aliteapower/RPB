alter table workforce_pay_template_adoptions add column effective_from date not null default date '0001-01-01';
create index ix_pay_adoption_effective on workforce_pay_template_adoptions(tenant_id,store_id,master_id,effective_from desc,store_version desc);
-- Additive request aggregate. Never rewrites payroll/payment snapshots.
create table workforce_pay_change_requests (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,store_id uuid not null,employee_id uuid,adoption_id uuid,effective_date date not null,
 revision bigint not null check(revision>0),hash text not null,status text not null check(status in ('draft','submitted','withdrawn','approved','rejected','stale')),
 snapshot jsonb not null,created_by uuid not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id),check((employee_id is null)<>(adoption_id is null)),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),
 foreign key(adoption_id,tenant_id,company_id,store_id) references workforce_pay_template_adoptions(id,tenant_id,company_id,store_id)
);
create unique index ux_pay_change_person_open on workforce_pay_change_requests(tenant_id,company_id,employee_id,effective_date) where employee_id is not null and status in ('draft','submitted','withdrawn');
create unique index ux_pay_change_adoption_open on workforce_pay_change_requests(tenant_id,adoption_id) where adoption_id is not null and status in ('draft','submitted','withdrawn');
create index ix_pay_change_list on workforce_pay_change_requests(tenant_id,store_id,company_id,status);
create table workforce_pay_change_revisions (
 request_id uuid not null,tenant_id uuid not null,company_id uuid not null,revision bigint not null check(revision>0),hash text not null,content jsonb not null,
 created_at timestamptz not null default now(),primary key(request_id,revision),unique(request_id,revision,tenant_id,company_id),
 foreign key(request_id,tenant_id,company_id) references workforce_pay_change_requests(id,tenant_id,company_id)
);
create table workforce_pay_change_events (
 id bigint generated always as identity primary key,request_id uuid not null,tenant_id uuid not null,company_id uuid not null,revision bigint not null,
 action text not null check(action in ('save','submit','withdraw','approve','reject','stale')),actor_id uuid not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 foreign key(request_id,revision,tenant_id,company_id) references workforce_pay_change_revisions(request_id,revision,tenant_id,company_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_pay_change_event_request on workforce_pay_change_events(tenant_id,request_id,id);
create table workforce_pay_change_commands (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,request_id uuid not null,company_id uuid not null,payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),
 foreign key(request_id,tenant_id,company_id) references workforce_pay_change_requests(id,tenant_id,company_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table workforce_pay_change_proofs (
 request_id uuid not null,tenant_id uuid not null,company_id uuid not null,revision bigint not null,kind text not null check(kind in ('profile','template','monthly')),
 subject_id uuid not null,period_month text not null default '',version bigint not null check(version>0),hash text not null,result jsonb not null,snapshot jsonb not null,
 created_at timestamptz not null default now(),primary key(request_id,revision,kind,subject_id,period_month),
 foreign key(request_id,revision,tenant_id,company_id) references workforce_pay_change_revisions(request_id,revision,tenant_id,company_id)
);
create index ix_pay_change_proof_subject on workforce_pay_change_proofs(tenant_id,company_id,kind,subject_id,version);
create table workforce_pay_approved_months (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,period_month date not null check(extract(day from period_month)=1),version bigint not null check(version>0),
 request_id uuid not null,revision bigint not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 primary key(tenant_id,company_id,employee_id,period_month,version),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(request_id,revision,tenant_id,company_id) references workforce_pay_change_revisions(request_id,revision,tenant_id,company_id)
);
create trigger pay_change_revision_immutable before update or delete on workforce_pay_change_revisions for each row execute function workforce_pay_template_immutable();
create trigger pay_change_event_immutable before update or delete on workforce_pay_change_events for each row execute function workforce_pay_template_immutable();
create trigger pay_change_command_immutable before update or delete on workforce_pay_change_commands for each row execute function workforce_pay_template_immutable();
create trigger pay_change_proof_immutable before update or delete on workforce_pay_change_proofs for each row execute function workforce_pay_template_immutable();
create trigger pay_approved_month_immutable before update or delete on workforce_pay_approved_months for each row execute function workforce_pay_template_immutable();
create function workforce_pay_request_guard() returns trigger language plpgsql as $$begin
 if TG_OP='DELETE' then raise exception 'request history cannot be deleted' using errcode='23514';end if;
 if (new.id,new.tenant_id,new.company_id,new.store_id,new.employee_id,new.adoption_id,new.effective_date) is distinct from (old.id,old.tenant_id,old.company_id,old.store_id,old.employee_id,old.adoption_id,old.effective_date) then raise exception 'request identity immutable' using errcode='23514';end if;
 if old.status in ('approved','rejected','stale') and new is distinct from old then raise exception 'terminal request immutable' using errcode='23514';end if;
 if old.status not in ('draft','withdrawn') and (new.revision<>old.revision or new.hash<>old.hash or new.snapshot->'content' is distinct from old.snapshot->'content') then raise exception 'submitted content immutable' using errcode='23514';end if;
 return new;
end$$;
create trigger pay_request_guard before update or delete on workforce_pay_change_requests for each row execute function workforce_pay_request_guard();
