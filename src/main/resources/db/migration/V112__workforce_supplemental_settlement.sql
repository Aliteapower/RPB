-- Additive supplemental approval and settlement ledgers. V108-V111 and original payroll data are unchanged.
alter table workforce_pay_change_proofs drop constraint workforce_pay_change_proofs_kind_check;
alter table workforce_pay_change_proofs add constraint workforce_pay_change_proofs_kind_check check(kind in ('profile','template','monthly','supplemental'));

create table workforce_supplemental_batches (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,store_id uuid not null,
 origin_month date not null,settlement_month date not null,status text not null check(status in ('draft','calculated','confirmed','published','voided')),
 version bigint not null check(version>0),request_id uuid not null,revision bigint not null,proposal_id uuid not null,snapshot jsonb not null,
 created_by uuid not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id),unique(id,tenant_id,company_id,employee_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),
 foreign key(request_id,revision,tenant_id,company_id) references workforce_pay_change_revisions(request_id,revision,tenant_id,company_id),
 check(extract(day from origin_month)=1 and extract(day from settlement_month)=1 and settlement_month>=origin_month)
);
create unique index uq_supplemental_proposal_active on workforce_supplemental_batches(tenant_id,company_id,request_id,revision,proposal_id) where status<>'voided';
create index ix_supplemental_company_month on workforce_supplemental_batches(tenant_id,company_id,origin_month,status);

alter table workforce_wage_event_consumptions drop constraint workforce_wage_event_consumptions_pkey;
alter table workforce_wage_event_consumptions alter column batch_id drop not null;
alter table workforce_wage_event_consumptions add column supplemental_id uuid;
alter table workforce_wage_event_consumptions add column id uuid not null default gen_random_uuid();
alter table workforce_wage_event_consumptions add primary key(id);
alter table workforce_wage_event_consumptions add constraint wage_consumption_source check((batch_id is null)<>(supplemental_id is null));
alter table workforce_wage_event_consumptions add foreign key(supplemental_id,tenant_id,company_id,employee_id) references workforce_supplemental_batches(id,tenant_id,company_id,employee_id);
create unique index uq_normal_event_source on workforce_wage_event_consumptions(tenant_id,company_id,event_id,batch_id) where batch_id is not null;
create unique index uq_supplemental_event_source on workforce_wage_event_consumptions(tenant_id,company_id,event_id,supplemental_id) where supplemental_id is not null;

create table workforce_statutory_evidence (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,period_month date not null,version bigint not null check(version>0),snapshot jsonb not null,
 created_by uuid not null,created_at timestamptz not null default now(),primary key(tenant_id,company_id,employee_id,period_month,version),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_statutory_liability_entries (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid,origin_month date not null,settlement_month date not null,
 component text not null check(component in ('cpf_employee','cpf_employer','sdl','cdac')),amount numeric(18,4) not null,
 supplemental_id uuid,assessment_id uuid,settlement_id uuid,source_key text not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 unique nulls not distinct(tenant_id,company_id,source_key,employee_id,origin_month,component),check((component='sdl')=(employee_id is null)),check(num_nonnulls(supplemental_id,assessment_id,settlement_id)=1),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(supplemental_id,tenant_id,company_id,employee_id) references workforce_supplemental_batches(id,tenant_id,company_id,employee_id),
 foreign key(supplemental_id,tenant_id,company_id) references workforce_supplemental_batches(id,tenant_id,company_id)
);
alter table workforce_aw_reconciliations add constraint uq_aw_reconciliation_scope unique(id,tenant_id,company_id,employee_id);
alter table workforce_statutory_liability_entries add foreign key(assessment_id,tenant_id,company_id,employee_id) references workforce_aw_reconciliations(id,tenant_id,company_id,employee_id);
create index ix_liability_subject_month on workforce_statutory_liability_entries(tenant_id,company_id,employee_id,origin_month);
create table workforce_statutory_settlements (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,assessment_id uuid,snapshot jsonb not null,created_at timestamptz not null default now(),
 unique(tenant_id,assessment_id),unique(id,tenant_id,company_id,employee_id),foreign key(assessment_id,tenant_id,company_id,employee_id) references workforce_aw_reconciliations(id,tenant_id,company_id,employee_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
alter table workforce_statutory_liability_entries add foreign key(settlement_id,tenant_id,company_id,employee_id) references workforce_statutory_settlements(id,tenant_id,company_id,employee_id) deferrable initially deferred;
create table workforce_statutory_remittances (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,snapshot jsonb not null,reverses_id uuid,created_by uuid not null,created_at timestamptz not null default now(),
 unique(id,tenant_id,company_id),unique(reverses_id),foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(reverses_id,tenant_id,company_id) references workforce_statutory_remittances(id,tenant_id,company_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),check(reverses_id is null or reverses_id<>id)
);
create table workforce_statutory_remittance_allocations (
 id uuid primary key default gen_random_uuid(),remittance_id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid,period_month date not null,component text not null check(component in ('cpf_employee','cpf_employer','sdl','cdac','interest')),
 amount numeric(18,2) not null check(amount>0),unique nulls not distinct(remittance_id,employee_id,period_month,component),check((component='sdl')=(employee_id is null)),
 foreign key(remittance_id,tenant_id,company_id) references workforce_statutory_remittances(id,tenant_id,company_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
create index ix_remittance_subject on workforce_statutory_remittance_allocations(tenant_id,company_id,employee_id,period_month);
create table workforce_statutory_company_evidence (
 tenant_id uuid not null,company_id uuid not null,period_month date not null,version bigint not null check(version>0),snapshot jsonb not null,created_by uuid not null,created_at timestamptz not null default now(),
 primary key(tenant_id,company_id,period_month,version),foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_supplemental_payments (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,batch_id uuid not null,employee_id uuid not null,snapshot jsonb not null,reverses_id uuid unique,
 created_at timestamptz not null default now(),unique(id,tenant_id,company_id,batch_id,employee_id),
 foreign key(batch_id,tenant_id,company_id,employee_id) references workforce_supplemental_batches(id,tenant_id,company_id,employee_id),
 foreign key(reverses_id,tenant_id,company_id,batch_id,employee_id) references workforce_supplemental_payments(id,tenant_id,company_id,batch_id,employee_id),check(reverses_id is null or reverses_id<>id)
);
create table workforce_supplemental_commands (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,store_id uuid not null,actor_id uuid not null,subject_id uuid not null,kind text not null,payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create function workforce_supplemental_guard() returns trigger language plpgsql as $$begin
 if TG_OP='DELETE' then raise exception 'supplemental history immutable' using errcode='23514';end if;
 if old.status='confirmed' and new.status<>'published' then raise exception 'confirmed supplemental only publishes' using errcode='23514';end if;
 if (new.id,new.tenant_id,new.company_id,new.employee_id,new.store_id,new.request_id,new.revision,new.proposal_id,new.origin_month,new.settlement_month) is distinct from (old.id,old.tenant_id,old.company_id,old.employee_id,old.store_id,old.request_id,old.revision,old.proposal_id,old.origin_month,old.settlement_month) then raise exception 'supplemental source immutable' using errcode='23514';end if;
 if old.status in ('confirmed','published','voided') and (new.snapshot->'calculation' is distinct from old.snapshot->'calculation' or new.snapshot->'proposal' is distinct from old.snapshot->'proposal' or new.snapshot->'approval' is distinct from old.snapshot->'approval') then raise exception 'supplemental calculation immutable' using errcode='23514';end if;
 if old.status in ('published','voided') and new is distinct from old then raise exception 'terminal supplemental immutable' using errcode='23514';end if;return new;
end$$;
create trigger supplemental_guard before update or delete on workforce_supplemental_batches for each row execute function workforce_supplemental_guard();
create trigger statutory_evidence_immutable before update or delete on workforce_statutory_evidence for each row execute function workforce_pay_template_immutable();
create trigger statutory_company_evidence_immutable before update or delete on workforce_statutory_company_evidence for each row execute function workforce_pay_template_immutable();
create trigger statutory_liability_immutable before update or delete on workforce_statutory_liability_entries for each row execute function workforce_pay_template_immutable();
create trigger statutory_settlement_immutable before update or delete on workforce_statutory_settlements for each row execute function workforce_pay_template_immutable();
create trigger statutory_remittance_immutable before update or delete on workforce_statutory_remittances for each row execute function workforce_pay_template_immutable();
create trigger statutory_allocation_immutable before update or delete on workforce_statutory_remittance_allocations for each row execute function workforce_pay_template_immutable();
create trigger supplemental_payment_immutable before update or delete on workforce_supplemental_payments for each row execute function workforce_pay_template_immutable();
create trigger supplemental_command_immutable before update or delete on workforce_supplemental_commands for each row execute function workforce_pay_template_immutable();
