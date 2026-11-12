-- Optimization/operation evidence only: no rewrite of wage snapshots, commands or existing facts.
alter table workforce_pay_commands drop constraint workforce_pay_commands_action_check;
alter table workforce_pay_commands add constraint workforce_pay_commands_action_check
 check(action in ('calculate','calculate_full','confirm','publish','void'));
create table workforce_pay_computations (
 tenant_id uuid not null,batch_id uuid not null,company_id uuid not null,
 snapshot jsonb not null check(jsonb_typeof(snapshot)='object'),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,batch_id),
 foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id)
);
create table workforce_pay_bulk_operations (
 id uuid not null,tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,
 payload jsonb not null check(jsonb_typeof(payload)='object'),
 response jsonb null check(response is null or jsonb_typeof(response)='object'),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
