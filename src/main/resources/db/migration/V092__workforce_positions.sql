-- Tenant-owned catalog; never infer a default job or grant new permissions.
create table workforce_positions (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null references tenants(id),
    name text not null check(name=btrim(name) and length(name) between 1 and 80),
    enabled boolean not null default true, version bigint not null check(version>0),
    deleted_at timestamptz null,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    unique(id,tenant_id), check(deleted_at is null or not enabled)
);
create unique index ux_workforce_position_name on workforce_positions(tenant_id,lower(name)) where deleted_at is null;
alter table workforce_employees add column position_id uuid null;
alter table workforce_employees add constraint fk_workforce_employee_position foreign key(position_id,tenant_id) references workforce_positions(id,tenant_id);
create index ix_workforce_employee_position on workforce_employees(tenant_id,position_id) where position_id is not null;
insert into workforce_positions(tenant_id,name,enabled,version)
select distinct on (tenant_id,lower(btrim(job_title))) tenant_id,btrim(job_title),true,1
from workforce_employees where btrim(job_title)<>'' order by tenant_id,lower(btrim(job_title)),btrim(job_title);
update workforce_employees e set position_id=p.id from workforce_positions p
where p.tenant_id=e.tenant_id and lower(p.name)=lower(btrim(e.job_title));
