-- Add a third explicit source without changing any historical consumption or assessment.
alter table workforce_wage_event_consumptions add column segment_id uuid;
alter table workforce_wage_event_consumptions drop constraint wage_consumption_source;
alter table workforce_wage_event_consumptions add constraint wage_consumption_source
 check(num_nonnulls(batch_id,supplemental_id,segment_id)=1);
alter table workforce_wage_event_consumptions add foreign key(segment_id,tenant_id,company_id,employee_id)
 references workforce_pay_segments(id,tenant_id,company_id,employee_id);
create unique index uq_segment_event_source on workforce_wage_event_consumptions(tenant_id,company_id,event_id,segment_id) where segment_id is not null;
create table workforce_segment_aw_assessments (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null,
 segment_id uuid not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 primary key(tenant_id,segment_id,employee_id),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects,
 foreign key(segment_id,tenant_id,company_id,employee_id) references workforce_pay_segments(id,tenant_id,company_id,employee_id)
);
create trigger segment_aw_assessment_immutable before update or delete on workforce_segment_aw_assessments
 for each row execute function workforce_pay_template_immutable();
