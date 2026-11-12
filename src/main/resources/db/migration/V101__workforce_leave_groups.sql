-- Additive grouping only. Existing leave rows and approval snapshots remain unchanged.
create table workforce_leave_groups (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,applicant_id uuid not null,
 request jsonb not null,created_at timestamptz not null default now(),
 unique(id,tenant_id,store_id,employee_id,applicant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(applicant_id,tenant_id) references auth_accounts(id,tenant_id)
);
alter table workforce_leaves add constraint ux_workforce_leave_group_scope unique(id,tenant_id,store_id,employee_id,applicant_id);
create table workforce_leave_group_members (
 group_id uuid not null,leave_id uuid not null,tenant_id uuid not null,store_id uuid not null,employee_id uuid not null,applicant_id uuid not null,
 created_at timestamptz not null default now(),primary key(tenant_id,leave_id),
 foreign key(group_id,tenant_id,store_id,employee_id,applicant_id) references workforce_leave_groups(id,tenant_id,store_id,employee_id,applicant_id),
 foreign key(leave_id,tenant_id,store_id,employee_id,applicant_id) references workforce_leaves(id,tenant_id,store_id,employee_id,applicant_id)
);
create index ix_workforce_leave_group_members on workforce_leave_group_members(tenant_id,store_id,group_id);
create index ix_workforce_leave_group_owner on workforce_leave_groups(tenant_id,store_id,employee_id,created_at);
