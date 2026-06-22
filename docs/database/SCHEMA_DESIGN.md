# Schema Design V1

## Purpose

This document converts the approved governance and database design documents into a reviewable PostgreSQL schema draft. It is Markdown-only and does not create a Flyway migration, executable SQL file, database connection, Java code, API, Vue UI, test, mock data, configuration, or dependency change.

## Inputs Read

Governance documents:

- docs/governance/BUSINESS_GLOSSARY.md
- docs/governance/BUSINESS_RULES.md
- docs/governance/DATA_STANDARD.md
- docs/governance/DATA_CHECKLIST.md
- docs/architecture/ARCHITECTURE.md
- docs/skills/reservation-system/SKILL_OVERVIEW.md
- docs/skills/reservation-system/SKILL.md

Database design documents:

- docs/database/DATABASE_DESIGN.md
- docs/database/ERD.md
- docs/database/DATA_MODEL_CHECKLIST.md

## Schema Design Rules

- Table and field names use lower snake case.
- Primary identifiers use `uuid`.
- Business time instants use `timestamptz` and represent UTC instants.
- Store-local business grouping dates may use `date`, derived from Store timezone.
- Money is not part of V1 reservation flow except Store currency configuration.
- Status fields store stable codes, not display text.
- Display text uses `i18n_key`, `locale`, and `message` where configurable.
- Phone numbers are nullable and, when present, use E.164 string format.
- `tenant_id` and `store_id` follow data level, not a blanket rule.
- Soft deletion uses `deleted_at` where lifecycle/history matters.
- Critical state changes are captured in `state_transition_logs` and `audit_logs`.

## Common Column Patterns

| Pattern | Fields | Applies To |
|---|---|---|
| Primary identity | `id uuid not null primary key` | All primary tables. |
| Store scope | `tenant_id uuid not null`, `store_id uuid not null` | Store configuration and Store operation tables. |
| Tenant scope | `tenant_id uuid not null` | Tenant-owned shared data such as customers and reason codes. |
| Optional Store scope | `store_id uuid nullable` | Audit/event/config override tables where scope may be Tenant-level. |
| Lifecycle audit | `created_at timestamptz not null`, `updated_at timestamptz not null`, `deleted_at timestamptz nullable` | Business/config tables with soft delete. |
| Actor audit metadata | `created_by_actor_id uuid nullable`, `updated_by_actor_id uuid nullable` | Tables changed by users/integrations. |
| Optimistic concurrency | `version integer not null` | Operational tables with state transitions. |
| Status | `status text not null` | Stateful tables; allowed values are documented per table. |

## Tenant / Store Base Layer

### tenants

Purpose: Tenant lifecycle and Tenant-level isolation root.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_code | text | No | Human/business code. |
| display_name | text | No | Tenant display name; user-facing translation can be added later if needed. |
| status | text | No | Allowed values: created, active, suspended, closed. |
| default_locale | text | Yes | Optional Tenant default, Store may override. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/closure boundary. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_code` unique at Platform scope among non-deleted tenants.
- `status` constrained to allowed values.

Index suggestions:

- Lookup by `tenant_code`.
- Filter by `status` and `deleted_at`.

### stores

Purpose: Physical/operational Store under one Tenant; owns locale, timezone, and Store operation configuration boundary.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_code | text | No | Unique within Tenant. |
| display_name | text | No | Store display name. |
| status | text | No | Allowed values: created, active, inactive, archived. |
| timezone | text | No | Default V1 value for Singapore Stores: Asia/Singapore. |
| locale | text | No | Default V1 value: en-SG. |
| date_format | text | No | Default V1 value: DD-MM-YYYY. |
| time_format | text | No | Default V1 value: 24H. |
| currency | text | No | Default V1 value: SGD. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive boundary. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_code` unique among non-deleted Stores.
- `status` constrained to allowed values.
- Locale/timezone fields required from day one.

Index suggestions:

- Lookup by `tenant_id + store_code`.
- Filter active Stores by `tenant_id + status + deleted_at`.

### store_policies

Purpose: Store-level operational defaults and policy knobs.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| reservation_hold_minutes | integer | No | V1 default: 15. |
| queue_call_hold_minutes | integer | No | V1 default: 3. |
| expected_dining_minutes | integer | No | V1 default: 90. |
| queue_rejoin_policy_code | text | No | Default: same_group_tail. |
| table_assignment_policy_code | text | No | Default policy code for capacity/time/area/group rules. |
| effective_from_at | timestamptz | No | UTC instant. |
| effective_to_at | timestamptz | Yes | Nullable means current until replaced. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- Only one current effective policy per Store.
- Minute values must be positive.

Index suggestions:

- Current policy lookup by `tenant_id + store_id + effective_to_at`.

### store_areas

Purpose: Store zones for table grouping and assignment priority.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| area_code | text | No | Store-scoped area code. |
| display_name | text | No | Area name. |
| status | text | No | Allowed values: created, active, inactive, archived. |
| sort_order | integer | No | Operational display/order hint. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + area_code` unique among non-deleted areas.
- `status` constrained to allowed values.

Index suggestions:

- Active area list by `tenant_id + store_id + status + sort_order`.

### dining_tables

Purpose: Physical table resource in a Store and Area.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| area_id | uuid | No | References store_areas. |
| table_code | text | No | Store-scoped table code or label. |
| display_name | text | No | Staff-facing label. |
| capacity_min | integer | No | Minimum recommended guest count. |
| capacity_max | integer | No | Maximum guest count. |
| status | text | No | available, locked, reserved, occupied, cleaning, inactive. |
| is_combinable | boolean | No | Whether table can join TableGroup. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + table_code` unique among non-deleted tables.
- `capacity_max >= capacity_min`.
- `status` constrained to Table status values.
- Area and Table must share Tenant and Store scope.

Index suggestions:

- Table list by `tenant_id + store_id + area_id + status`.
- Capacity search by `tenant_id + store_id + status + capacity_max`.

## Table Management

### table_groups

Purpose: Fixed or temporary combined table resource.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| group_code | text | No | Store-scoped group code. |
| group_type | text | No | fixed or temporary. |
| status | text | No | Fixed: created, active, inactive, deleted. Temporary: created, locked, occupied, released, ended. |
| display_name | text | Yes | Optional label. |
| capacity_min | integer | No | Derived or configured minimum guest count. |
| capacity_max | integer | No | Derived or configured maximum guest count. |
| active_from_at | timestamptz | Yes | Useful for temporary group lifecycle. |
| active_until_at | timestamptz | Yes | Required for temporary group planning when known. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + group_code` unique among non-deleted groups.
- `group_type` constrained to fixed/temporary.
- Status constrained according to group type.
- Temporary group cannot be used after released or ended.

Index suggestions:

- Active groups by `tenant_id + store_id + group_type + status`.

### table_group_members

Purpose: Join boundary between TableGroup and DiningTable.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| table_group_id | uuid | No | References table_groups. |
| dining_table_id | uuid | No | References dining_tables. |
| member_role | text | Yes | Optional hint such as primary or joined. |
| created_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |

Constraint suggestions:

- `table_group_id + dining_table_id` unique among active members.
- TableGroup and DiningTable must share Tenant and Store.
- DiningTable cannot be in more than one active temporary group at the same time.
- Circular TableGroup membership is prevented by not allowing TableGroup-to-TableGroup members.
- Inactive or deleted DiningTable cannot be added to active groups.

Index suggestions:

- Members by `tenant_id + store_id + table_group_id`.
- Active membership lookup by `tenant_id + store_id + dining_table_id + deleted_at`.

### table_locks

Purpose: Durable table or TableGroup lock boundary for concurrent assignment protection.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| lock_key | text | No | Stable lock identity. |
| lock_owner | text | No | Staff, system, integration, or workflow owner. |
| resource_type | text | No | dining_table or table_group. |
| dining_table_id | uuid | Yes | Required when resource_type is dining_table. |
| table_group_id | uuid | Yes | Required when resource_type is table_group. |
| source_type | text | No | reservation, queue_ticket, walk_in, seating, manual, system. |
| source_id | uuid | Yes | Related source identity when applicable. |
| idempotency_key | text | Yes | Reusable operation key. |
| status | text | No | active, released, expired, cancelled. |
| locked_at | timestamptz | No | UTC instant. |
| locked_until_at | timestamptz | No | UTC expiry instant. |
| released_at | timestamptz | Yes | UTC release instant. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `lock_key` unique while lock is active.
- Exactly one resource reference must be present: `dining_table_id` or `table_group_id`.
- Active lock requires `locked_until_at` in the future at acquisition time.
- Active locks must prevent conflicting active lock or active seating for the same resource.

Index suggestions:

- Active lock lookup by `tenant_id + store_id + resource_type + resource id + status`.
- Expiry scan by `status + locked_until_at`.
- Idempotency lookup by `tenant_id + store_id + idempotency_key`.

## Customer

### customers

Purpose: Tenant-scoped customer identity, including no-phone and temporary guests.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| customer_code | text | No | Tenant-scoped customer or temporary code. |
| customer_type | text | No | regular, anonymous, temporary, walk_in_guest, special_note. |
| display_name | text | Yes | Nullable for anonymous. |
| nickname | text | Yes | Optional lookup aid. |
| phone_e164 | text | Yes | Nullable; E.164 when present. |
| email | text | Yes | Optional. |
| lookup_note | text | Yes | Staff lookup context for no-phone guests. |
| status | text | No | active, merged, archived. |
| merged_into_customer_id | uuid | Yes | Optional self-reference for merge history. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + customer_code` unique among non-deleted customers.
- `tenant_id + phone_e164` unique only where phone exists and merge policy accepts uniqueness.
- `phone_e164` nullable and never primary key.
- `customer_type` and `status` constrained to allowed values.

Index suggestions:

- Lookup by `tenant_id + phone_e164`.
- Lookup no-phone guests by `tenant_id + customer_code`.
- Search by `tenant_id + display_name` and `tenant_id + nickname`.

## Reservation / Queue / WalkIn

### reservations

Purpose: Advance capacity reservation for Store + date + time slot + party size.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| customer_id | uuid | Yes | Nullable only when captured through temporary customer data before customer creation; preferred to create Customer. |
| reservation_code | text | No | Store-scoped business code. |
| party_size | integer | No | Number of guests. |
| business_date | date | No | Store-local reservation date derived from Store timezone. |
| reserved_start_at | timestamptz | No | UTC instant. |
| reserved_end_at | timestamptz | No | UTC instant, usually expected dining duration. |
| hold_until_at | timestamptz | No | UTC instant, default reservation time + 15 minutes. |
| status | text | No | draft, confirmed, arrived, seated, completed, cancelled, no_show. |
| source_channel | text | No | staff, customer, integration, system. |
| cancellation_reason_code | text | Yes | References reason_codes logically. |
| no_show_reason_code | text | Yes | References reason_codes logically. |
| note | text | Yes | Staff/customer free-form note. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + reservation_code` unique among non-deleted reservations.
- Active duplicate rule: same `tenant_id + store_id + customer_id + reserved_start_at + reserved_end_at` cannot have more than one Reservation in confirmed, arrived, or seated status.
- `party_size > 0`.
- `reserved_end_at > reserved_start_at`.
- `hold_until_at >= reserved_start_at`.
- `status` constrained to Reservation status values.

Index suggestions:

- Store schedule by `tenant_id + store_id + business_date + reserved_start_at`.
- Active customer conflict by `tenant_id + store_id + customer_id + reserved_start_at + reserved_end_at + status`.
- Status queue by `tenant_id + store_id + status + hold_until_at`.

### reservation_preassignments

Purpose: Optional table or TableGroup pre-assignment before final Seating.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| reservation_id | uuid | No | References reservations. |
| resource_type | text | No | dining_table or table_group. |
| dining_table_id | uuid | Yes | Required for dining_table resource. |
| table_group_id | uuid | Yes | Required for table_group resource. |
| status | text | No | active, released, cancelled. |
| preassigned_at | timestamptz | No | UTC instant. |
| released_at | timestamptz | Yes | UTC instant. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |

Constraint suggestions:

- Exactly one resource reference must be present.
- Active preassignment does not equal occupied Seating.
- Reservation and resource must share Tenant and Store.

Index suggestions:

- Active preassignments by `tenant_id + store_id + reservation_id + status`.
- Resource availability checks by `tenant_id + store_id + resource_type + resource id + status`.

### queue_groups

Purpose: Store-scoped queue grouping policy. V1 default is party-size groups.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| group_code | text | No | Example: party_1_2, party_3_4, party_5_6, party_7_plus. |
| min_party_size | integer | No | Lower bound. |
| max_party_size | integer | Yes | Nullable for 7+. |
| display_i18n_key | text | No | Label key. |
| status | text | No | active, inactive. |
| sort_order | integer | No | Queue display/order hint. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + group_code` unique among active/non-deleted groups.
- Party size range must be valid and non-overlapping by Store policy.

Index suggestions:

- Active groups by `tenant_id + store_id + status + sort_order`.

### queue_tickets

Purpose: Waiting ticket after arrival when no table resource is available.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| queue_group_id | uuid | No | References queue_groups. |
| customer_id | uuid | Yes | Nullable when anonymous context is captured separately; preferred to use customers. |
| reservation_id | uuid | Yes | Optional source after CheckIn. |
| walk_in_id | uuid | Yes | Optional source from WalkIn. |
| ticket_number | integer | No | Number unique within Store + group + business_date/window. |
| party_size | integer | No | Number of guests. |
| business_date | date | No | Store-local queue operating date. |
| status | text | No | waiting, called, skipped, rejoined, seated, cancelled, expired. |
| queue_position | integer | Yes | Mutable operational order hint. |
| called_at | timestamptz | Yes | UTC instant. |
| skipped_at | timestamptz | Yes | UTC instant. |
| rejoined_at | timestamptz | Yes | UTC instant. |
| expires_at | timestamptz | Yes | UTC expiry instant. |
| cancellation_reason_code | text | Yes | Reason code. |
| note | text | Yes | Staff note. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + queue_group_id + business_date + ticket_number` unique.
- At most one of `reservation_id` or `walk_in_id` should be present as source.
- QueueTicket must not be required by Reservation or WalkIn.
- `status` constrained to QueueTicket status values.
- `party_size > 0`.

Index suggestions:

- Active queue by `tenant_id + store_id + queue_group_id + business_date + status + queue_position`.
- Call timeout scan by `tenant_id + store_id + status + called_at`.
- Source lookup by `reservation_id` and `walk_in_id`.

### walk_ins

Purpose: On-site arrival without advance Reservation.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| customer_id | uuid | Yes | Nullable only for anonymous capture before Customer creation; preferred to create Customer. |
| walk_in_code | text | No | Store-scoped business code. |
| party_size | integer | No | Number of guests. |
| business_date | date | No | Store-local operating date. |
| arrived_at | timestamptz | No | UTC instant. |
| status | text | No | arrived, queued, seated, cancelled, abandoned. |
| note | text | Yes | Staff note. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- `tenant_id + store_id + walk_in_code` unique among non-deleted walk-ins.
- `party_size > 0`.
- WalkIn can have Seating without QueueTicket.

Index suggestions:

- Store arrivals by `tenant_id + store_id + business_date + arrived_at`.
- Status list by `tenant_id + store_id + status + arrived_at`.

## Seating / Cleaning / Turnover

### seatings

Purpose: Formal seating event and occupancy record.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| reservation_id | uuid | Yes | Source option 1. |
| queue_ticket_id | uuid | Yes | Source option 2. |
| walk_in_id | uuid | Yes | Source option 3. |
| seating_code | text | No | Store-scoped business code. |
| party_size_snapshot | integer | No | Number of guests seated, captured as the Seating-time snapshot. |
| status | text | No | planned, locked, occupied, completed, cleaning_triggered, cancelled. |
| seated_at | timestamptz | Yes | UTC instant. |
| completed_at | timestamptz | Yes | UTC instant. |
| manual_override_reason_code | text | Yes | Required when system recommendation is overridden. |
| note | text | Yes | Staff note. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- Exactly one source must be present: `reservation_id`, `queue_ticket_id`, or `walk_in_id`.
- `tenant_id + store_id + seating_code` unique among non-deleted seatings.
- `status` constrained to Seating status values.
- `party_size_snapshot > 0`.
- Completed seating should have `completed_at`.

Index suggestions:

- Active seating by `tenant_id + store_id + status + seated_at`.
- Source lookup by each source id.

### seating_resources

Purpose: Resource assignment for a Seating.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| seating_id | uuid | No | References seatings. |
| resource_type | text | No | dining_table or table_group. |
| dining_table_id | uuid | Yes | Required when resource_type is dining_table. |
| table_group_id | uuid | Yes | Required when resource_type is table_group. |
| assigned_at | timestamptz | No | UTC instant. |
| released_at | timestamptz | Yes | UTC instant. |
| status | text | No | active, released, cancelled. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |

Constraint suggestions:

- Exactly one resource reference must be present.
- Active seating resource must not overlap active seating resource for the same Table or effective TableGroup.
- Seating and resource must share Tenant and Store.

Index suggestions:

- Active resource occupancy by `tenant_id + store_id + resource_type + resource id + status`.
- Seating lookup by `tenant_id + store_id + seating_id`.

### cleanings

Purpose: Cleaning status flow after Seating completion or guest departure.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| seating_id | uuid | No | References seatings. |
| resource_type | text | No | dining_table or table_group. |
| dining_table_id | uuid | Yes | Required when resource_type is dining_table. |
| table_group_id | uuid | Yes | Required when resource_type is table_group. |
| status | text | No | pending, cleaning, completed, released, cancelled. |
| started_at | timestamptz | Yes | UTC instant. |
| completed_at | timestamptz | Yes | UTC instant. |
| released_at | timestamptz | Yes | UTC instant. |
| note | text | Yes | Staff note. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |
| version | integer | No | Optimistic concurrency. |

Constraint suggestions:

- Exactly one cleaned resource reference must be present.
- Cleaning completion required before resource returns to available unless resource is inactive by policy.
- `status` constrained to Cleaning status values.

Index suggestions:

- Active cleaning by `tenant_id + store_id + status + started_at`.
- Resource cleaning lookup by `tenant_id + store_id + resource_type + resource id + status`.

### turnovers

Purpose: Business result/metric for one table-use cycle.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | No | References stores. |
| seating_id | uuid | No | References seatings. |
| cleaning_id | uuid | Yes | Nullable until cleaning completion if result is prepared early. |
| business_date | date | No | Store-local date for reporting grouping. |
| seated_at | timestamptz | No | UTC instant copied/derived from Seating. |
| completed_at | timestamptz | Yes | UTC instant. |
| cleaning_completed_at | timestamptz | Yes | UTC instant. |
| duration_minutes | integer | Yes | Derived metric. |
| status | text | No | pending, recorded, archived. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete/archive. |

Constraint suggestions:

- Turnover must reference Seating.
- Turnover must not be sourced from Reservation alone.
- One recorded Turnover per Seating.

Index suggestions:

- Store report by `tenant_id + store_id + business_date + status`.
- Seating lookup by `seating_id`.

## Governance / Event / Audit

### business_events

Purpose: Business event stream for CheckIn, queue call, skipped, rejoined, manual override, table release, and other domain events.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | Yes | Nullable only for Platform-level events. |
| store_id | uuid | Yes | Nullable for Tenant/Platform events. |
| event_type | text | No | Stable event code such as reservation_checked_in. |
| target_type | text | No | reservation, queue_ticket, walk_in, seating, cleaning, table, table_group, store, tenant. |
| target_id | uuid | No | Related object id. |
| occurred_at | timestamptz | No | UTC instant. |
| source | text | No | staff, customer, integration, system. |
| actor_id | uuid | Yes | Actor identity when available. |
| idempotency_key | text | Yes | Deduplication key when applicable. |
| payload | jsonb | Yes | Event-specific snapshot, not source of core state. |
| created_at | timestamptz | No | UTC instant. |

Constraint suggestions:

- Event type uses stable code and i18n mapping where shown.
- Scope must match target object.

Index suggestions:

- Target event lookup by `target_type + target_id + occurred_at`.
- Store event timeline by `tenant_id + store_id + occurred_at`.

### state_transition_logs

Purpose: Reusable state transition history for stateful objects.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | Yes | Nullable only for Platform-level state objects. |
| store_id | uuid | Yes | Nullable for Tenant/Platform state objects. |
| target_type | text | No | reservation, queue_ticket, dining_table, table_group, seating, cleaning, turnover. |
| target_id | uuid | No | Related object id. |
| from_status | text | Yes | Nullable for initial transition. |
| to_status | text | No | New status. |
| transition_code | text | No | Stable transition code. |
| triggered_by | text | No | staff, customer, integration, system. |
| actor_id | uuid | Yes | Actor identity when available. |
| reason_code | text | Yes | Optional reason code. |
| idempotency_key | text | Yes | Deduplication key. |
| occurred_at | timestamptz | No | UTC instant. |
| audit_log_id | uuid | Yes | Optional link to audit_logs. |
| created_at | timestamptz | No | UTC instant. |

Constraint suggestions:

- Transition target scope must match referenced object scope.
- Legal transition validation is enforced by domain policy; this log preserves evidence.

Index suggestions:

- Target transition history by `target_type + target_id + occurred_at`.
- Store transition timeline by `tenant_id + store_id + occurred_at`.

### audit_logs

Purpose: Mandatory audit trail for critical operations, overrides, permissions, and integration calls.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | Yes | Nullable for Platform-only audit. |
| store_id | uuid | Yes | Nullable for Tenant/Platform audit. |
| operation_code | text | No | Stable operation code. |
| target_type | text | No | Related object type. |
| target_id | uuid | Yes | Nullable for broad configuration or failed target creation. |
| actor_id | uuid | Yes | Actor identity when available. |
| actor_role | text | Yes | Role at operation time. |
| source | text | No | staff, customer, integration, system. |
| before_state | jsonb | Yes | Snapshot before operation. |
| after_state | jsonb | Yes | Snapshot after operation. |
| idempotency_key | text | Yes | Related idempotency key. |
| failure_reason | text | Yes | Failure detail when operation fails. |
| occurred_at | timestamptz | No | UTC instant. |
| created_at | timestamptz | No | UTC instant. |

Constraint suggestions:

- Critical operations must write audit.
- Scope must match actor and target.

Index suggestions:

- Target audit by `target_type + target_id + occurred_at`.
- Scope audit by `tenant_id + store_id + occurred_at`.
- Operation audit by `operation_code + occurred_at`.

### idempotency_records

Purpose: Reusable deduplication for critical operations and third-party calls.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | Yes | Nullable for Platform-only operation. |
| store_id | uuid | Yes | Nullable when operation is Tenant/Platform scoped. |
| idempotency_key | text | No | Caller-provided or system-generated key. |
| source | text | No | staff, customer, integration, system. |
| action_code | text | No | Stable operation action. |
| target_type | text | Yes | Optional related target type. |
| target_id | uuid | Yes | Optional related target id. |
| request_hash | text | No | Stable request fingerprint. |
| response_snapshot | jsonb | Yes | Nullable response snapshot. |
| status | text | No | started, completed, failed, expired. |
| expires_at | timestamptz | No | UTC expiry instant. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |

Constraint suggestions:

- `tenant_id + store_id + source + action_code + idempotency_key` unique, with nullable scope handled explicitly in implementation design.
- `status` constrained to allowed values.

Index suggestions:

- Lookup by scope/action/key.
- Expiry scan by `status + expires_at`.

### reason_codes

Purpose: Configurable cancellation, no_show, skip, override, and operational reason codes.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | No | References tenants. |
| store_id | uuid | Yes | Nullable for Tenant-level shared code. |
| reason_type | text | No | cancellation, no_show, skip, override, cleaning, table_release. |
| code | text | No | Stable reason code. |
| i18n_key | text | No | Display key. |
| status | text | No | active, inactive. |
| sort_order | integer | No | Display/order hint. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |

Constraint suggestions:

- Tenant-level uniqueness: `tenant_id + reason_type + code` where store_id is absent.
- Store override uniqueness: `tenant_id + store_id + reason_type + code`.

Index suggestions:

- Active reason list by `tenant_id + store_id + reason_type + status + sort_order`.

### i18n_message_catalog

Purpose: Message catalog for stable system and domain keys.

| Field | Type | Nullable | Notes |
|---|---|---:|---|
| id | uuid | No | Primary key. |
| tenant_id | uuid | Yes | Nullable for Platform-level catalog. |
| store_id | uuid | Yes | Nullable; Store override is optional future boundary. |
| i18n_key | text | No | Stable message key. |
| locale | text | No | Locale code such as en-SG. |
| message | text | No | Localized message. |
| status | text | No | active, inactive. |
| created_at | timestamptz | No | UTC instant. |
| updated_at | timestamptz | No | UTC instant. |
| deleted_at | timestamptz | Yes | Soft delete. |

Constraint suggestions:

- Unique message by scope + `i18n_key + locale`.
- System status display should reference keys, not hardcoded text.

Index suggestions:

- Message lookup by `tenant_id + store_id + i18n_key + locale`.
- Active catalog by `locale + status`.

## Key Relationship Summary

- `stores.tenant_id` references `tenants.id`.
- `store_policies`, `store_areas`, `dining_tables`, `table_groups`, `queue_groups`, and all Store operation tables reference both Tenant and Store scope.
- `customers.tenant_id` references Tenant; `customers` does not require Store.
- `reservations.customer_id`, `queue_tickets.customer_id`, and `walk_ins.customer_id` reference Tenant-scoped Customer.
- `queue_tickets.reservation_id` and `queue_tickets.walk_in_id` are optional and never required by Reservation or WalkIn.
- `seatings` must have exactly one source among Reservation, QueueTicket, or WalkIn.
- `seating_resources`, `cleanings`, and `table_locks` must have exactly one resource among DiningTable or TableGroup.
- `turnovers` derive from Seating and Cleaning, never Reservation alone.

## Open Questions

- None for this schema design round.

## Open Conflicts

- None found in the reviewed documents for this schema design round.

## Not Created in This Round

- No Flyway migration.
- No `.sql` file.
- No Java Entity, Repository, Service, Controller, or DTO.
- No API implementation.
- No Vue page or component.
- No tests.
- No mock data.
- No database connection or migration execution.
