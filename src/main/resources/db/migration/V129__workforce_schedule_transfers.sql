create table workforce_schedule_transfers (
    tenant_id uuid not null,
    source_schedule_id uuid not null,
    target_schedule_id uuid not null,
    anchor_store_id uuid not null,
    clock_not_before timestamptz,
    primary key (tenant_id, target_schedule_id),
    unique (tenant_id, source_schedule_id),
    foreign key (source_schedule_id, tenant_id) references workforce_schedules(id, tenant_id),
    foreign key (target_schedule_id, tenant_id) references workforce_schedules(id, tenant_id),
    foreign key (anchor_store_id, tenant_id) references stores(id, tenant_id),
    check (source_schedule_id <> target_schedule_id)
);
create index ix_workforce_transfer_anchor on workforce_schedule_transfers(tenant_id, anchor_store_id, target_schedule_id);
