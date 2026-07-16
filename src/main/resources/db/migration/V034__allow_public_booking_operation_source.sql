alter table reservations
    drop constraint if exists ck_reservations_source_channel,
    add constraint ck_reservations_source_channel
        check (source_channel in ('staff', 'customer', 'public_booking', 'integration', 'system'));

alter table idempotency_records
    drop constraint if exists ck_idempotency_records_source,
    add constraint ck_idempotency_records_source
        check (source in ('staff', 'customer', 'public_booking', 'integration', 'system'));

alter table audit_logs
    drop constraint if exists ck_audit_logs_source,
    add constraint ck_audit_logs_source
        check (source in ('staff', 'customer', 'public_booking', 'integration', 'system'));

alter table business_events
    drop constraint if exists ck_business_events_source,
    add constraint ck_business_events_source
        check (source in ('staff', 'customer', 'public_booking', 'integration', 'system'));

alter table state_transition_logs
    drop constraint if exists ck_state_transition_logs_triggered_by,
    add constraint ck_state_transition_logs_triggered_by
        check (triggered_by in ('staff', 'customer', 'public_booking', 'integration', 'system'));
