-- Installation evidence is an exact retained baseline, never a fabricated approval.
create table workforce_pay_effective_evidence (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,
 kind text not null check(kind in ('legacy-valid','direct-adoption','economic-unchanged')),
 subject_kind text not null check(subject_kind in ('template','profile','monthly','batch-input')),
 subject_id uuid not null,period_month text not null default '',version bigint not null check(version>=0),
 source_id uuid,source_version bigint check(source_version is null or source_version>0),source_snapshot jsonb,result jsonb not null,created_at timestamptz not null default now(),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 unique(tenant_id,company_id,kind,subject_kind,subject_id,period_month,version)
);
create index ix_effective_pay_subject on workforce_pay_effective_evidence(tenant_id,company_id,subject_kind,subject_id,period_month,version);
create table workforce_pay_legacy_months (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,period_month date not null,
 state text not null check(state in ('valid','ambiguous')),batch_id uuid,result jsonb,
 created_at timestamptz not null default now(),primary key(tenant_id,company_id,employee_id,period_month),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id),
 check((state='valid' and result is not null) or (state='ambiguous' and result is null))
);
insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,period_month,version,source_id,result,created_at)
 select gen_random_uuid(),tenant_id,company_id,'legacy-valid','template',id,'',version,null,snapshot,now() from workforce_pay_templates;
insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,period_month,version,source_id,result,created_at)
 select gen_random_uuid(),tenant_id,company_id,'legacy-valid','profile',id,'',version,null,snapshot,now() from workforce_pay_profiles;
insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,period_month,version,source_id,result,created_at)
 select gen_random_uuid(),i.tenant_id,i.company_id,'legacy-valid','batch-input',i.employee_id,b.id::text,i.version,b.id,i.snapshot,now()
 from workforce_pay_inputs i join workforce_pay_batches b on b.id=i.batch_id and b.tenant_id=i.tenant_id;
insert into workforce_pay_legacy_months(tenant_id,company_id,employee_id,period_month,state,batch_id,result)
 select i.tenant_id,i.company_id,i.employee_id,b.period_month,'valid',b.id,i.snapshot
 from workforce_pay_inputs i join workforce_pay_batches b on b.id=i.batch_id and b.tenant_id=i.tenant_id
 where b.status<>'voided';
-- A single unambiguous voided baseline survives rebuilding. Different complete
-- inputs block; nullable legacy JSON fields and version metadata do not create false conflicts.
with voided as (
 select i.tenant_id,i.company_id,i.employee_id,b.period_month,b.id batch_id,i.snapshot,
  row_number() over(partition by i.tenant_id,i.company_id,i.employee_id,b.period_month order by i.version desc,b.created_at desc,b.id) ordinal
 from workforce_pay_inputs i join workforce_pay_batches b on b.id=i.batch_id and b.tenant_id=i.tenant_id where b.status='voided'
), alternatives as (
 select tenant_id,company_id,employee_id,period_month,count(distinct jsonb_strip_nulls(snapshot-'version')) choices
 from voided group by tenant_id,company_id,employee_id,period_month
)
insert into workforce_pay_legacy_months(tenant_id,company_id,employee_id,period_month,state,batch_id,result)
 select v.tenant_id,v.company_id,v.employee_id,v.period_month,case when a.choices=1 then 'valid' else 'ambiguous' end,
  case when a.choices=1 then v.batch_id else null end,case when a.choices=1 then v.snapshot else null end
 from voided v join alternatives a using(tenant_id,company_id,employee_id,period_month) where v.ordinal=1
 on conflict(tenant_id,company_id,employee_id,period_month) do nothing;
create trigger effective_pay_evidence_immutable before update or delete on workforce_pay_effective_evidence for each row execute function workforce_pay_template_immutable();
create trigger legacy_pay_month_immutable before update or delete on workforce_pay_legacy_months for each row execute function workforce_pay_template_immutable();
create table workforce_cdac_bulk_attempts (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,store_id uuid not null,actor_id uuid not null,payload jsonb not null,created_at timestamptz not null default now(),unique(id,tenant_id,company_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_cdac_bulk_items (
 command_id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,result jsonb not null,created_at timestamptz not null default now(),primary key(command_id,employee_id),
 foreign key(command_id,tenant_id,company_id) references workforce_cdac_bulk_attempts(id,tenant_id,company_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
create trigger cdac_attempt_immutable before update or delete on workforce_cdac_bulk_attempts for each row execute function workforce_pay_template_immutable();
create trigger cdac_item_immutable before update or delete on workforce_cdac_bulk_items for each row execute function workforce_pay_template_immutable();
