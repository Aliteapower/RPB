create unique index if not exists ux_reservation_preassignments_one_active_reservation
    on reservation_preassignments (tenant_id, store_id, reservation_id)
    where status = 'active' and deleted_at is null;
