-- Durable command receipts only; existing schedules and payroll snapshots are unchanged.
create table workforce_bulk_schedule_commands (
 id uuid primary key, tenant_id uuid not null references tenants(id),
 store_id uuid not null, actor_id uuid not null,
 request_hash text not null check(request_hash ~ '^[a-f0-9]{64}$'),
 result_json jsonb not null, created_at timestamptz not null default now(),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_bulk_schedule_scope on workforce_bulk_schedule_commands(tenant_id,store_id,created_at);
