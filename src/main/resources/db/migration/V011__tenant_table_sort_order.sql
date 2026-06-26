alter table dining_tables
    add column if not exists sort_order integer not null default 0;

create index if not exists ix_dining_tables_area_sort
    on dining_tables (tenant_id, store_id, area_id, sort_order, table_code)
    where deleted_at is null;
