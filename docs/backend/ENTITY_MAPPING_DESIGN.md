# Entity Mapping Design V1

## Purpose

This document defines the intended Persistence Entity mapping boundary for each V1 table.

It is design-only. It does not create Java Entity classes, mapper classes, repositories, SQL, migrations, tests, API, or UI.

## Common Entity Mapping Rules

Primary keys:

- Database `uuid` primary keys map to Java UUID-backed identity value objects in Domain Object.
- Persistence Entity may hold raw `UUID`; mapper converts raw ids to value objects.
- No numeric surrogate id should be introduced.

Scope:

- Tenant-level entity fields map to `TenantScope`.
- Store-level entity fields map to `StoreScope`.
- Platform-level rows use `PlatformScope` or explicit no-tenant scope later.
- Store-scoped entities must carry both `tenant_id` and `store_id`.
- Tenant-scoped `customers` must not require `store_id`.

Status:

- Database status is stored as `text`.
- Persistence Entity may hold string status code or a persistence enum later.
- Mapper converts status code to domain status enum.
- Entity must not perform state machine transition validation.

Timestamp:

- Database `timestamptz` maps to Java `Instant`.
- Database `date` maps to Java `LocalDate`.
- All stored timestamps are UTC instants.
- Display conversion uses Store timezone, locale, date format, and time format outside Entity.

JSONB:

- `jsonb` maps to a persistence JSON type later.
- Mapper converts JSONB to `MetadataPayload`, `SnapshotPayload`, or purpose-specific snapshot wrapper.
- Domain Object should not receive scattered raw maps.

Soft delete:

- `deleted_at` maps to nullable `Instant deletedAt`.
- Repository Implementation should filter active rows by default where the use case requires active data.
- History/audit use cases may include deleted rows explicitly.
- Entity must not hide active occupancy, active locks, or pending temporary groups through soft-delete logic.

Optimistic locking:

- Tables with `version` map to an optimistic version field later.
- Version protects concurrent updates but does not replace domain concurrency rules or database unique indexes.

Audit fields:

- `created_at`, `updated_at`, `deleted_at`, and `version` are persistence metadata.
- Audit decisions still belong to audit/event/idempotency orchestration, not Entity.

XOR mapping:

- `reservation_id / queue_ticket_id / walk_in_id` maps to `SeatingSource`.
- `table_id / table_group_id` maps to `SeatingResourceTarget` or `CleaningResourceTarget`.
- Mapper must fail fast if a database row violates the expected XOR shape.

Generic target mapping:

- `target_type + target_id` maps to `TargetRef`.
- Generic targets must not be mapped to incorrect hard foreign keys.

## Entity Mapping Catalog

| Table | Persistence Entity | Domain Object | One Table / One Entity | Read Model Need | Key Mapping |
| --- | --- | --- | --- | --- | --- |
| `tenants` | `TenantEntity` | `Tenant` | Yes | Optional `TenantSummaryReadModel` later | PK `id`; platform-owned root; `status` to tenant lifecycle status; `created_at/updated_at/deleted_at` to `Instant`; no JSONB; soft delete supported; `version` optimistic lock; Entity must not own store operations. |
| `stores` | `StoreEntity` | `Store` | Yes | Optional `StoreOperationalProfileReadModel` later | PK `id`; `tenant_id + id` maps Store scope; timezone/locale/date/time/currency preserved; `status` to Store status; UTC timestamps; no JSONB; soft delete and version; Entity must not decide table availability. |
| `store_policies` | `StorePolicyEntity` | `StorePolicy` | Yes | Current-policy read model may be useful | StoreScope from `tenant_id/store_id`; policy minutes and policy code fields; effective time range as `Instant`; no JSONB; soft delete and version; Entity must not execute hold, queue call, or assignment policy. |
| `store_areas` | `StoreAreaEntity` | `Area` | Yes | Area list read model later | StoreScope; PK `id`; status code; sort order; UTC timestamps; no JSONB; soft delete and version; Entity must not compute table capacity or seating availability. |
| `dining_tables` | `DiningTableEntity` | `DiningTable` | Yes | Table availability read model likely later | StoreScope; `area_id`; `capacity_min/capacity_max` to `CapacityRange`; status to `DiningTableStatus`; `is_combinable`; UTC timestamps; no JSONB; soft delete and version; Entity must not run `TableAssignmentRule`. |
| `table_groups` | `TableGroupEntity` | `TableGroup` | Yes | Table group availability read model likely later | StoreScope; `group_type`, `capacity_min/capacity_max`, active window; status maps by fixed vs temporary lifecycle; UTC timestamps; no JSONB; soft delete and version; Entity must not validate semantic member availability. |
| `table_group_members` | `TableGroupMemberEntity` | `TableGroupMember` | Yes | Member list projection likely enough | StoreScope; `table_group_id/table_id`; active if `deleted_at` null; no status; no JSONB; no version in V1; Entity must not allow group-to-group membership or circular semantic rules. |
| `customers` | `CustomerEntity` | `Customer` | Yes | Customer lookup read models likely later | TenantScope only; no `store_id`; `phone_e164` nullable; `merged_into_customer_id` same tenant; status to Customer status; UTC timestamps; no JSONB; soft delete and version; Entity must not treat Customer as Member. |
| `reservations` | `ReservationEntity` | `Reservation` | Yes | Schedule and conflict read models likely later | StoreScope; nullable `customer_id`; `party_size` to `PartySize`; `business_date` to `LocalDate`; reserved and hold timestamps to `Instant`; status to `ReservationStatus`; no JSONB; soft delete and version; Entity must not perform availability or duplicate policy decisions. |
| `reservation_preassignments` | `ReservationPreassignmentEntity` | `ReservationPreassignment` | Yes | Optional active preassignment projection | StoreScope; required `reservation_id`; resource XOR maps to `ReservationPreassignmentTarget`; status code; timestamps; no JSONB; soft delete; Entity must not create final occupancy. |
| `queue_groups` | `QueueGroupEntity` | `QueueGroup` | Yes | Active queue group list read model likely later | StoreScope; party-size band to capacity/party range; `display_i18n_key` to `I18nKey`; status code; timestamps; no JSONB; soft delete and version; Entity must not order tickets by itself. |
| `queue_tickets` | `QueueTicketEntity` | `QueueTicket` | Yes | Active queue and next-callable projections likely later | StoreScope; required queue group; nullable customer; optional Reservation or WalkIn source with at most one set; `party_size`; `ticket_number`; queue timestamps; status to `QueueTicketStatus`; no JSONB; soft delete and version; Entity must not call, skip, rejoin, or seat. |
| `walk_ins` | `WalkInEntity` | `WalkIn` | Yes | Store-day arrival projection likely later | StoreScope; nullable customer; `party_size`; `business_date`; `arrived_at`; status code; note; no JSONB; soft delete and version; Entity must not create QueueTicket automatically. |
| `seatings` | `SeatingEntity` | `Seating` | Yes | Active occupancy read model likely later | StoreScope; exactly one source maps to `SeatingSource`; `party_size_snapshot`; status to `SeatingStatus`; `seated_at/completed_at`; no JSONB; soft delete and version; Entity must not decide source validity or table assignment. |
| `seating_resources` | `SeatingResourceEntity` | `SeatingResource` | Yes | Resource occupancy projection likely later | StoreScope; required `seating_id`; resource XOR maps to `SeatingResourceTarget`; assigned/released timestamps; status code; no JSONB; soft delete; Entity must not decide availability or occupancy conflicts. |
| `cleanings` | `CleaningEntity` | `Cleaning` | Yes | Active cleaning board read model likely later | StoreScope; required `seating_id`; resource XOR maps to `CleaningResourceTarget`; status to `CleaningStatus`; started/completed/released timestamps; no JSONB; soft delete and version; Entity must not calculate turnover. |
| `turnovers` | `TurnoverEntity` | `Turnover` | Yes | Reporting read model likely later | StoreScope; required seating, optional cleaning; `business_date`; seated/completed/cleaning completed timestamps; duration minutes; status to `TurnoverStatus`; no JSONB; soft delete; Entity must not source turnover from Reservation alone. |
| `table_locks` | `TableLockEntity` | `TableLock` | Yes | Active lock projection likely later | StoreScope; `resource_type/resource_id` maps to lock target; source type/id maps to source ref; status to `TableLockStatus`; lock/release timestamps; no JSONB; version; Entity must not replace seating occupancy or table availability rules. |
| `idempotency_records` | `IdempotencyRecordEntity` | `IdempotencyRecord` | Yes | Key lookup projection likely enough | Scope can be Platform/Tenant/Store; `target_type/target_id` maps to `TargetRef`; `response_snapshot` JSONB to `SnapshotPayload`; status to `IdempotencyStatus`; expires/created/updated timestamps; no soft delete; Entity must not decide replay semantics alone. |
| `audit_logs` | `AuditLogEntity` | `AuditLog` | Yes | Timeline read model likely later | Contextual scope; `target_type/target_id` maps to `TargetRef`; `before_state/after_state/metadata` JSONB to snapshot/metadata wrappers; `occurred_at`; no status; no soft delete; Entity must not decide whether an operation needs audit. |
| `business_events` | `BusinessEventEntity` | `BusinessEvent` | Yes | Event timeline read model likely later | Contextual scope; `target_type/target_id` maps to `TargetRef`; `before_state/after_state/metadata` JSONB wrappers; event type and actor/source fields; `occurred_at`; no soft delete; Entity must not become a CheckInEntity. |
| `state_transition_logs` | `StateTransitionLogEntity` | `StateTransitionLog` | Yes | Transition history read model likely later | Contextual scope; `target_type/target_id` maps to `TargetRef`; from/to status codes; JSONB wrappers; optional `audit_log_id`; `occurred_at`; no soft delete; Entity must not validate transitions. |
| `reason_codes` | `ReasonCodeEntity` | `ReasonCode` | Yes | Active reason list read model likely later | TenantScope with nullable Store override; `i18n_key`; status code; sort order; timestamps; no JSONB; soft delete; Entity must not render display text or execute cancellation/no-show decisions. |
| `i18n_message_catalog` | `I18nMessageEntity` | `I18nMessage` | Yes | Message lookup projection likely enough | Platform/Tenant/Store scope; `i18n_key + locale`; message text; status code; timestamps; no JSONB; soft delete; Entity must not hardcode business copy or decide locale fallback alone. |

## Key Field Mapping Decisions

### Seating Source

Database shape:

```text
reservation_id uuid null
queue_ticket_id uuid null
walk_in_id uuid null
constraint: exactly one non-null
```

Domain shape:

```text
SeatingSource
```

Allowed source variants:

- Reservation source.
- QueueTicket source.
- WalkIn source.

Mapper responsibility:

- Convert the three nullable ids into one explicit source variant.
- Reject impossible shapes if a corrupted row is encountered.

Not allowed:

- Let Application Service scatter `if reservation_id else if queue_ticket_id else if walk_in_id` checks.
- Treat CheckIn as a seating source.

### SeatingResource Target

Database shape:

```text
resource_type text
table_id uuid null
table_group_id uuid null
constraint: exactly one table or table_group target
```

Domain shape:

```text
SeatingResourceTarget
```

Not allowed:

- Put table availability checks into Entity.
- Treat TableGroup as a replacement for DiningTable definition.

### Cleaning Target

Database shape:

```text
resource_type text
table_id uuid null
table_group_id uuid null
constraint: exactly one table or table_group target
```

Domain shape:

```text
CleaningResourceTarget
```

Not allowed:

- Put turnover calculation into CleaningEntity.
- Release resources without explicit cleaning policy and audit boundary.

### Customer Phone Nullable

`CustomerEntity.phoneE164` must be nullable.

Mapping rules:

- Null phone remains null in Domain Object.
- Present phone maps through an E.164 value boundary.
- Customer identity must not be phone-only.
- Tenant-scoped customer uniqueness must remain Tenant-scoped.

### Generic TargetRef

Tables using generic targets:

- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`

Mapping target:

```text
TargetRef(targetType, targetId)
```

Rules:

- Do not force a generic target into a wrong JPA foreign key.
- Scope validation belongs to Repository Implementation plus Scope policies.
- Target display/lookup belongs to later query/read-model design.

### MetadataPayload and SnapshotPayload

JSONB fields:

- `audit_logs.before_state`
- `audit_logs.after_state`
- `audit_logs.metadata`
- `business_events.before_state`
- `business_events.after_state`
- `business_events.metadata`
- `state_transition_logs.before_state`
- `state_transition_logs.after_state`
- `state_transition_logs.metadata`
- `idempotency_records.response_snapshot`

Recommended mapping:

- `MetadataPayload` for general metadata.
- `SnapshotPayload` for before/after state and response snapshot.

Not allowed:

- Raw map usage across Domain Object APIs.
- Business decisions based on untyped JSON instead of domain fields.

## Entity Rules Not To Implement

No Persistence Entity should implement:

- Reservation availability or duplicate active reservation policy.
- Queue ordering, calling, skip, rejoin, expiry decisions.
- WalkIn direct seating decision.
- Seating source validation beyond structural mapping.
- Table assignment and capacity matching decisions.
- TableGroup semantic validity.
- Cleaning completion and resource release policy.
- Turnover calculation policy.
- Customer identity matching.
- E.164 validation as business identity policy.
- Store timezone/locale formatting.
- Audit requirement decision.
- Idempotency replay decision.

## Not Created In This Round

- No Java Entity.
- No Mapper class.
- No Repository.
- No Application Service.
- No Controller.
- No API DTO.
- No API.
- No UI.
- No Migration.
- No SQL.
- No database connection.
