create extension if not exists pgcrypto;

create table tenants (
    id uuid primary key default gen_random_uuid(),
    tenant_code text not null,
    display_name text not null,
    status text not null,
    default_locale text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_tenants_status check (status in ('created', 'active', 'suspended', 'closed')),
    constraint uq_tenants_id_tenant unique (id),
    constraint uq_tenants_id_for_scope unique (id, tenant_code)
);

create unique index ux_tenants_code_active
    on tenants (tenant_code)
    where deleted_at is null;

create table stores (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_code text not null,
    display_name text not null,
    status text not null,
    timezone text not null,
    locale text not null,
    date_format text not null,
    time_format text not null,
    currency text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_stores_status check (status in ('created', 'active', 'inactive', 'archived')),
    constraint uq_stores_id_tenant unique (id, tenant_id)
);

create unique index ux_stores_tenant_code_active
    on stores (tenant_id, store_code)
    where deleted_at is null;

create index ix_stores_tenant_status
    on stores (tenant_id, status, deleted_at);

create table store_policies (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    reservation_hold_minutes integer not null default 15,
    queue_call_hold_minutes integer not null default 3,
    expected_dining_minutes integer not null default 90,
    queue_rejoin_policy_code text not null default 'same_group_tail',
    table_assignment_policy_code text not null default 'default_capacity_time_area_group',
    effective_from_at timestamptz not null default now(),
    effective_to_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_policies_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_policies_minutes check (
        reservation_hold_minutes > 0
        and queue_call_hold_minutes > 0
        and expected_dining_minutes > 0
    ),
    constraint ck_store_policies_effective_range check (
        effective_to_at is null or effective_to_at > effective_from_at
    )
);

create unique index ux_store_policies_current
    on store_policies (tenant_id, store_id)
    where effective_to_at is null and deleted_at is null;

create table reason_codes (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid null,
    reason_type text not null,
    code text not null,
    i18n_key text not null,
    status text not null,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_reason_codes_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_reason_codes_type check (
        reason_type in ('cancellation', 'no_show', 'skip', 'override', 'cleaning', 'table_release')
    ),
    constraint ck_reason_codes_status check (status in ('active', 'inactive'))
);

create unique index ux_reason_codes_tenant
    on reason_codes (tenant_id, reason_type, code)
    where store_id is null and deleted_at is null;

create unique index ux_reason_codes_store
    on reason_codes (tenant_id, store_id, reason_type, code)
    where store_id is not null and deleted_at is null;

create index ix_reason_codes_active
    on reason_codes (tenant_id, store_id, reason_type, status, sort_order);

create table i18n_message_catalog (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    store_id uuid null,
    i18n_key text not null,
    locale text not null,
    message text not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_i18n_message_catalog_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_i18n_message_catalog_scope check (
        (tenant_id is not null) or (tenant_id is null and store_id is null)
    ),
    constraint ck_i18n_message_catalog_status check (status in ('active', 'inactive'))
);

create unique index ux_i18n_platform_message
    on i18n_message_catalog (i18n_key, locale)
    where tenant_id is null and store_id is null and deleted_at is null;

create unique index ux_i18n_tenant_message
    on i18n_message_catalog (tenant_id, i18n_key, locale)
    where tenant_id is not null and store_id is null and deleted_at is null;

create unique index ux_i18n_store_message
    on i18n_message_catalog (tenant_id, store_id, i18n_key, locale)
    where tenant_id is not null and store_id is not null and deleted_at is null;

create index ix_i18n_message_catalog_locale_status
    on i18n_message_catalog (locale, status);

create table store_areas (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    area_code text not null,
    display_name text not null,
    status text not null,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_areas_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_areas_status check (status in ('created', 'active', 'inactive', 'archived')),
    constraint uq_store_areas_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_store_areas_code_active
    on store_areas (tenant_id, store_id, area_code)
    where deleted_at is null;

create index ix_store_areas_active
    on store_areas (tenant_id, store_id, status, sort_order);

create table dining_tables (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    area_id uuid not null,
    table_code text not null,
    display_name text not null,
    capacity_min integer not null,
    capacity_max integer not null,
    status text not null,
    is_combinable boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_dining_tables_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_dining_tables_area_scope foreign key (area_id, tenant_id, store_id) references store_areas(id, tenant_id, store_id),
    constraint ck_dining_tables_capacity check (capacity_min > 0 and capacity_max >= capacity_min),
    constraint ck_dining_tables_status check (status in ('available', 'locked', 'reserved', 'occupied', 'cleaning', 'inactive')),
    constraint uq_dining_tables_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_dining_tables_code_active
    on dining_tables (tenant_id, store_id, table_code)
    where deleted_at is null;

create index ix_dining_tables_area_status
    on dining_tables (tenant_id, store_id, area_id, status);

create index ix_dining_tables_capacity
    on dining_tables (tenant_id, store_id, status, capacity_max);

create table table_groups (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    group_code text not null,
    group_type text not null,
    status text not null,
    display_name text null,
    capacity_min integer not null,
    capacity_max integer not null,
    active_from_at timestamptz null,
    active_until_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_table_groups_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_table_groups_type check (group_type in ('fixed', 'temporary')),
    constraint ck_table_groups_status check (
        (group_type = 'fixed' and status in ('created', 'active', 'inactive', 'deleted'))
        or (group_type = 'temporary' and status in ('created', 'locked', 'occupied', 'released', 'ended'))
    ),
    constraint ck_table_groups_capacity check (capacity_min > 0 and capacity_max >= capacity_min),
    constraint ck_table_groups_active_range check (
        active_until_at is null or active_from_at is null or active_until_at > active_from_at
    ),
    constraint uq_table_groups_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_table_groups_code_active
    on table_groups (tenant_id, store_id, group_code)
    where deleted_at is null;

create index ix_table_groups_active
    on table_groups (tenant_id, store_id, group_type, status);

create table table_group_members (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    table_group_id uuid not null,
    table_id uuid not null,
    member_role text null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_table_group_members_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_table_group_members_group_scope foreign key (table_group_id, tenant_id, store_id) references table_groups(id, tenant_id, store_id),
    constraint fk_table_group_members_table_scope foreign key (table_id, tenant_id, store_id) references dining_tables(id, tenant_id, store_id)
);

create unique index ux_table_group_members_active_member
    on table_group_members (table_group_id, table_id)
    where deleted_at is null;

create index ix_table_group_members_group
    on table_group_members (tenant_id, store_id, table_group_id);

create index ix_table_group_members_table
    on table_group_members (tenant_id, store_id, table_id, deleted_at);

create table customers (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    customer_code text not null,
    customer_type text not null,
    display_name text null,
    nickname text null,
    phone_e164 text null,
    email text null,
    lookup_note text null,
    status text not null,
    merged_into_customer_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_customers_merged_scope foreign key (merged_into_customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_customers_type check (customer_type in ('regular', 'anonymous', 'temporary', 'walk_in_guest', 'boss_friend', 'special_note')),
    constraint ck_customers_status check (status in ('active', 'merged', 'archived')),
    constraint ck_customers_phone_e164 check (phone_e164 is null or phone_e164 ~ '^[+][1-9][0-9]{1,14}$'),
    constraint uq_customers_id_tenant unique (id, tenant_id)
);

create unique index ux_customers_code_active
    on customers (tenant_id, customer_code)
    where deleted_at is null;

create unique index ux_customers_phone_active
    on customers (tenant_id, phone_e164)
    where phone_e164 is not null and deleted_at is null;

create index ix_customers_display_name
    on customers (tenant_id, display_name);

create index ix_customers_nickname
    on customers (tenant_id, nickname);

create table reservations (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    customer_id uuid null,
    reservation_code text not null,
    party_size integer not null,
    business_date date not null,
    reserved_start_at timestamptz not null,
    reserved_end_at timestamptz not null,
    hold_until_at timestamptz not null,
    status text not null,
    source_channel text not null,
    cancellation_reason_code text null,
    no_show_reason_code text null,
    note text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_reservations_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_reservations_customer_scope foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_reservations_party_size check (party_size > 0),
    constraint ck_reservations_time_range check (reserved_end_at > reserved_start_at and hold_until_at >= reserved_start_at),
    constraint ck_reservations_status check (status in ('draft', 'confirmed', 'arrived', 'seated', 'completed', 'cancelled', 'no_show')),
    constraint ck_reservations_source_channel check (source_channel in ('staff', 'customer', 'integration', 'system')),
    constraint uq_reservations_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_reservations_code_active
    on reservations (tenant_id, store_id, reservation_code)
    where deleted_at is null;

create unique index ux_reservations_active_customer_slot
    on reservations (tenant_id, store_id, customer_id, reserved_start_at, reserved_end_at)
    where customer_id is not null and status in ('confirmed', 'arrived', 'seated') and deleted_at is null;

create index ix_reservations_store_schedule
    on reservations (tenant_id, store_id, business_date, reserved_start_at);

create index ix_reservations_customer_start
    on reservations (tenant_id, store_id, customer_id, reserved_start_at);

create index ix_reservations_status_hold
    on reservations (tenant_id, store_id, status, hold_until_at);

create table reservation_preassignments (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    reservation_id uuid not null,
    resource_type text not null,
    table_id uuid null,
    table_group_id uuid null,
    status text not null,
    preassigned_at timestamptz not null default now(),
    released_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_reservation_preassignments_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_reservation_preassignments_reservation_scope foreign key (reservation_id, tenant_id, store_id) references reservations(id, tenant_id, store_id),
    constraint fk_reservation_preassignments_table_scope foreign key (table_id, tenant_id, store_id) references dining_tables(id, tenant_id, store_id),
    constraint fk_reservation_preassignments_group_scope foreign key (table_group_id, tenant_id, store_id) references table_groups(id, tenant_id, store_id),
    constraint ck_reservation_preassignments_resource check (
        (resource_type = 'dining_table' and table_id is not null and table_group_id is null)
        or (resource_type = 'table_group' and table_id is null and table_group_id is not null)
    ),
    constraint ck_reservation_preassignments_status check (status in ('active', 'released', 'cancelled'))
);

create index ix_reservation_preassignments_reservation
    on reservation_preassignments (tenant_id, store_id, reservation_id, status);

create index ix_reservation_preassignments_resource
    on reservation_preassignments (tenant_id, store_id, resource_type, table_id, table_group_id, status);

create table queue_groups (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    group_code text not null,
    min_party_size integer not null,
    max_party_size integer null,
    display_i18n_key text not null,
    status text not null,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_queue_groups_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_queue_groups_range check (
        min_party_size > 0 and (max_party_size is null or max_party_size >= min_party_size)
    ),
    constraint ck_queue_groups_status check (status in ('active', 'inactive')),
    constraint uq_queue_groups_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_queue_groups_code_active
    on queue_groups (tenant_id, store_id, group_code)
    where deleted_at is null;

create index ix_queue_groups_active
    on queue_groups (tenant_id, store_id, status, sort_order);

create table walk_ins (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    customer_id uuid null,
    walk_in_code text not null,
    party_size integer not null,
    business_date date not null,
    arrived_at timestamptz not null,
    status text not null,
    note text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_walk_ins_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_walk_ins_customer_scope foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_walk_ins_party_size check (party_size > 0),
    constraint ck_walk_ins_status check (status in ('arrived', 'queued', 'seated', 'cancelled', 'abandoned')),
    constraint uq_walk_ins_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_walk_ins_code_active
    on walk_ins (tenant_id, store_id, walk_in_code)
    where deleted_at is null;

create index ix_walk_ins_arrivals
    on walk_ins (tenant_id, store_id, business_date, arrived_at);

create index ix_walk_ins_status
    on walk_ins (tenant_id, store_id, status, arrived_at);

create table queue_tickets (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    queue_group_id uuid not null,
    customer_id uuid null,
    reservation_id uuid null,
    walk_in_id uuid null,
    ticket_number integer not null,
    party_size integer not null,
    business_date date not null,
    status text not null,
    queue_position integer null,
    called_at timestamptz null,
    skipped_at timestamptz null,
    rejoined_at timestamptz null,
    expires_at timestamptz null,
    cancellation_reason_code text null,
    note text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_queue_tickets_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_queue_tickets_group_scope foreign key (queue_group_id, tenant_id, store_id) references queue_groups(id, tenant_id, store_id),
    constraint fk_queue_tickets_customer_scope foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint fk_queue_tickets_reservation_scope foreign key (reservation_id, tenant_id, store_id) references reservations(id, tenant_id, store_id),
    constraint fk_queue_tickets_walk_in_scope foreign key (walk_in_id, tenant_id, store_id) references walk_ins(id, tenant_id, store_id),
    constraint ck_queue_tickets_source check (num_nonnulls(reservation_id, walk_in_id) <= 1),
    constraint ck_queue_tickets_party_size check (party_size > 0),
    constraint ck_queue_tickets_status check (status in ('waiting', 'called', 'skipped', 'rejoined', 'seated', 'cancelled', 'expired')),
    constraint uq_queue_tickets_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_queue_tickets_number
    on queue_tickets (tenant_id, store_id, queue_group_id, business_date, ticket_number);

create index ix_queue_tickets_active_queue
    on queue_tickets (tenant_id, store_id, queue_group_id, business_date, status, queue_position);

create index ix_queue_tickets_call_timeout
    on queue_tickets (tenant_id, store_id, status, called_at);

create index ix_queue_tickets_reservation
    on queue_tickets (reservation_id);

create index ix_queue_tickets_walk_in
    on queue_tickets (walk_in_id);

create table seatings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    reservation_id uuid null,
    queue_ticket_id uuid null,
    walk_in_id uuid null,
    seating_code text not null,
    party_size_snapshot integer not null,
    status text not null,
    seated_at timestamptz null,
    completed_at timestamptz null,
    manual_override_reason_code text null,
    note text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_seatings_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_seatings_reservation_scope foreign key (reservation_id, tenant_id, store_id) references reservations(id, tenant_id, store_id),
    constraint fk_seatings_queue_ticket_scope foreign key (queue_ticket_id, tenant_id, store_id) references queue_tickets(id, tenant_id, store_id),
    constraint fk_seatings_walk_in_scope foreign key (walk_in_id, tenant_id, store_id) references walk_ins(id, tenant_id, store_id),
    constraint ck_seatings_source check (num_nonnulls(reservation_id, queue_ticket_id, walk_in_id) = 1),
    constraint ck_seatings_party_size_snapshot check (party_size_snapshot > 0),
    constraint ck_seatings_status check (status in ('planned', 'locked', 'occupied', 'completed', 'cleaning_triggered', 'cancelled')),
    constraint ck_seatings_completed_at check (
        status <> 'completed' or completed_at is not null
    ),
    constraint uq_seatings_id_scope unique (id, tenant_id, store_id)
);

create unique index ux_seatings_code_active
    on seatings (tenant_id, store_id, seating_code)
    where deleted_at is null;

create index ix_seatings_active
    on seatings (tenant_id, store_id, status, seated_at);

create index ix_seatings_reservation
    on seatings (reservation_id);

create index ix_seatings_queue_ticket
    on seatings (queue_ticket_id);

create index ix_seatings_walk_in
    on seatings (walk_in_id);

create table seating_resources (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    seating_id uuid not null,
    resource_type text not null,
    table_id uuid null,
    table_group_id uuid null,
    assigned_at timestamptz not null default now(),
    released_at timestamptz null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_seating_resources_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_seating_resources_seating_scope foreign key (seating_id, tenant_id, store_id) references seatings(id, tenant_id, store_id),
    constraint fk_seating_resources_table_scope foreign key (table_id, tenant_id, store_id) references dining_tables(id, tenant_id, store_id),
    constraint fk_seating_resources_group_scope foreign key (table_group_id, tenant_id, store_id) references table_groups(id, tenant_id, store_id),
    constraint ck_seating_resources_resource check (
        (resource_type = 'dining_table' and table_id is not null and table_group_id is null)
        or (resource_type = 'table_group' and table_id is null and table_group_id is not null)
    ),
    constraint ck_seating_resources_status check (status in ('active', 'released', 'cancelled'))
);

create index ix_seating_resources_seating
    on seating_resources (tenant_id, store_id, seating_id);

create index ix_seating_resources_resource_status
    on seating_resources (tenant_id, store_id, resource_type, table_id, table_group_id, status);

create unique index ux_seating_resources_active_table
    on seating_resources (tenant_id, store_id, table_id)
    where table_id is not null and status = 'active' and deleted_at is null;

create unique index ux_seating_resources_active_group
    on seating_resources (tenant_id, store_id, table_group_id)
    where table_group_id is not null and status = 'active' and deleted_at is null;

create table cleanings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    seating_id uuid not null,
    resource_type text not null,
    table_id uuid null,
    table_group_id uuid null,
    status text not null,
    started_at timestamptz null,
    completed_at timestamptz null,
    released_at timestamptz null,
    note text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_cleanings_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_cleanings_seating_scope foreign key (seating_id, tenant_id, store_id) references seatings(id, tenant_id, store_id),
    constraint fk_cleanings_table_scope foreign key (table_id, tenant_id, store_id) references dining_tables(id, tenant_id, store_id),
    constraint fk_cleanings_group_scope foreign key (table_group_id, tenant_id, store_id) references table_groups(id, tenant_id, store_id),
    constraint ck_cleanings_resource check (
        (resource_type = 'dining_table' and table_id is not null and table_group_id is null)
        or (resource_type = 'table_group' and table_id is null and table_group_id is not null)
    ),
    constraint ck_cleanings_status check (status in ('pending', 'cleaning', 'completed', 'released', 'cancelled')),
    constraint ck_cleanings_completed_at check (
        status <> 'completed' or completed_at is not null
    ),
    constraint uq_cleanings_id_scope unique (id, tenant_id, store_id)
);

create index ix_cleanings_active
    on cleanings (tenant_id, store_id, status, started_at);

create index ix_cleanings_resource
    on cleanings (tenant_id, store_id, resource_type, table_id, table_group_id, status);

create table turnovers (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    seating_id uuid not null,
    cleaning_id uuid null,
    business_date date not null,
    seated_at timestamptz not null,
    completed_at timestamptz null,
    cleaning_completed_at timestamptz null,
    duration_minutes integer null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_turnovers_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_turnovers_seating_scope foreign key (seating_id, tenant_id, store_id) references seatings(id, tenant_id, store_id),
    constraint fk_turnovers_cleaning_scope foreign key (cleaning_id, tenant_id, store_id) references cleanings(id, tenant_id, store_id),
    constraint ck_turnovers_duration check (duration_minutes is null or duration_minutes >= 0),
    constraint ck_turnovers_status check (status in ('pending', 'recorded', 'archived'))
);

create unique index ux_turnovers_seating_active
    on turnovers (tenant_id, store_id, seating_id)
    where deleted_at is null;

create index ix_turnovers_report
    on turnovers (tenant_id, store_id, business_date, status);

create table table_locks (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    resource_type text not null,
    resource_id uuid not null,
    lock_key text not null,
    lock_owner text not null,
    locked_until_at timestamptz not null,
    source_type text not null,
    source_id uuid null,
    idempotency_key text null,
    status text not null,
    locked_at timestamptz not null default now(),
    released_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_table_locks_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_table_locks_resource_type check (resource_type in ('dining_table', 'table_group')),
    constraint ck_table_locks_source_type check (source_type in ('reservation', 'queue_ticket', 'walk_in', 'seating', 'manual', 'system')),
    constraint ck_table_locks_status check (status in ('active', 'released', 'expired', 'cancelled')),
    constraint ck_table_locks_time check (locked_until_at > locked_at)
);

create unique index ux_table_locks_active_key
    on table_locks (lock_key)
    where status = 'active';

create unique index ux_table_locks_active_resource
    on table_locks (tenant_id, store_id, resource_type, resource_id)
    where status = 'active';

create index ix_table_locks_resource_status
    on table_locks (resource_type, resource_id, status);

create index ix_table_locks_expiry
    on table_locks (status, locked_until_at);

create index ix_table_locks_idempotency
    on table_locks (tenant_id, store_id, idempotency_key);

create table idempotency_records (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    store_id uuid null,
    idempotency_key text not null,
    source text not null,
    action text not null,
    target_type text null,
    target_id uuid null,
    request_hash text not null,
    response_snapshot jsonb null,
    status text not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_idempotency_records_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_idempotency_records_scope check (
        (tenant_id is not null) or (tenant_id is null and store_id is null)
    ),
    constraint ck_idempotency_records_source check (source in ('staff', 'customer', 'integration', 'system')),
    constraint ck_idempotency_records_status check (status in ('started', 'completed', 'failed', 'expired'))
);

create unique index ux_idempotency_platform
    on idempotency_records (source, action, idempotency_key)
    where tenant_id is null and store_id is null;

create unique index ux_idempotency_tenant
    on idempotency_records (tenant_id, source, action, idempotency_key)
    where tenant_id is not null and store_id is null;

create unique index ux_idempotency_store
    on idempotency_records (tenant_id, store_id, source, action, idempotency_key)
    where tenant_id is not null and store_id is not null;

create index ix_idempotency_records_expiry
    on idempotency_records (status, expires_at);

create table audit_logs (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    store_id uuid null,
    operation_code text not null,
    target_type text not null,
    target_id uuid null,
    actor_type text not null,
    actor_id uuid null,
    actor_role text null,
    source text not null,
    before_state jsonb null,
    after_state jsonb null,
    reason_code text null,
    idempotency_key text null,
    failure_reason text null,
    metadata jsonb null,
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint fk_audit_logs_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_audit_logs_scope check (
        (tenant_id is not null) or (tenant_id is null and store_id is null)
    ),
    constraint ck_audit_logs_source check (source in ('staff', 'customer', 'integration', 'system'))
);

create index ix_audit_logs_target
    on audit_logs (target_type, target_id, occurred_at);

create index ix_audit_logs_scope
    on audit_logs (tenant_id, store_id, occurred_at);

create index ix_audit_logs_operation
    on audit_logs (operation_code, occurred_at);

create table business_events (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    store_id uuid null,
    event_type text not null,
    target_type text not null,
    target_id uuid not null,
    actor_type text not null,
    actor_id uuid null,
    source text not null,
    before_state jsonb null,
    after_state jsonb null,
    reason_code text null,
    idempotency_key text null,
    metadata jsonb null,
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint fk_business_events_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_business_events_scope check (
        (tenant_id is not null) or (tenant_id is null and store_id is null)
    ),
    constraint ck_business_events_source check (source in ('staff', 'customer', 'integration', 'system'))
);

create index ix_business_events_target
    on business_events (target_type, target_id, occurred_at);

create index ix_business_events_scope
    on business_events (tenant_id, store_id, occurred_at);

create table state_transition_logs (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    store_id uuid null,
    target_type text not null,
    target_id uuid not null,
    actor_type text not null,
    actor_id uuid null,
    from_status text null,
    to_status text not null,
    transition_code text not null,
    triggered_by text not null,
    before_state jsonb null,
    after_state jsonb null,
    reason_code text null,
    idempotency_key text null,
    metadata jsonb null,
    occurred_at timestamptz not null default now(),
    audit_log_id uuid null references audit_logs(id),
    created_at timestamptz not null default now(),
    constraint fk_state_transition_logs_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_state_transition_logs_scope check (
        (tenant_id is not null) or (tenant_id is null and store_id is null)
    ),
    constraint ck_state_transition_logs_triggered_by check (triggered_by in ('staff', 'customer', 'integration', 'system'))
);

create index ix_state_transition_logs_target
    on state_transition_logs (target_type, target_id, occurred_at);

create index ix_state_transition_logs_scope
    on state_transition_logs (tenant_id, store_id, occurred_at);
