alter table workforce_employees add column birth_date date null;
alter table workforce_employees add column english_name text not null default '' check(length(english_name)<=100);
alter table workforce_employees add column enabled boolean not null default true;
alter table workforce_employees add constraint ck_workforce_birth_date check(birth_date is null or birth_date<=hire_date);
create table workforce_document_types (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null references tenants(id),
 name text not null check(length(trim(name)) between 1 and 80),enabled boolean not null default true,
 version bigint not null default 1 check(version>0),deleted_at timestamptz,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),unique(id,tenant_id)
);
create unique index ux_workforce_document_type_name on workforce_document_types(tenant_id,lower(name)) where deleted_at is null;
insert into workforce_document_types(tenant_id,name) select t.id,n.name from tenants t cross join (values('身份证'),('护照'),('批文')) n(name);
create function workforce_seed_document_types() returns trigger language plpgsql set search_path from current as $$
begin
 insert into workforce_document_types(tenant_id,name) select new.id,n.name from (values('身份证'),('护照'),('批文')) n(name);
 return new;
end $$;
create trigger workforce_tenant_document_types after insert on tenants for each row execute function workforce_seed_document_types();
create table workforce_employee_documents (
 id uuid primary key,tenant_id uuid not null,employee_id uuid not null,type_id uuid not null,
 number text not null default '' check(length(number)<=100),expires_on date,notes text not null default '' check(length(notes)<=2000),
 version bigint not null check(version>0),deleted_at timestamptz,created_by uuid not null,updated_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,employee_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(type_id,tenant_id) references workforce_document_types(id,tenant_id)
);
create index ix_workforce_document_employee on workforce_employee_documents(tenant_id,employee_id) where deleted_at is null;
create index ix_workforce_document_type on workforce_employee_documents(tenant_id,type_id);
create table workforce_document_attachments (
 id uuid primary key,tenant_id uuid not null,employee_id uuid not null,document_id uuid not null,
 filename text not null check(length(filename) between 1 and 160),content_type text not null check(content_type in ('image/jpeg','image/png','image/webp','application/pdf')),
 content bytea not null check(octet_length(content)>0 and octet_length(content)<=5242880),sha256 text not null check(sha256~'^[a-f0-9]{64}$'),
 deleted_at timestamptz,created_by uuid not null,created_at timestamptz not null default now(),
 foreign key(document_id,tenant_id,employee_id) references workforce_employee_documents(id,tenant_id,employee_id),
 check(content_type='application/pdf' or octet_length(content)<=1048576)
);
create index ix_workforce_attachment_document on workforce_document_attachments(tenant_id,employee_id,document_id) where deleted_at is null;
