# Table Group Management Design

Date: 2026-06-24

## Purpose

Make table grouping an operational capability instead of a read-only display.

The current system can read and use existing `table_group` resources. Staff can
select an existing group such as `VIP-1` for reservation seating, queue seating,
walk-in seating, cleaning, and table display. The missing product capability is
staff-controlled creation and maintenance:

- Phase 1: temporary table groups for same-day service.
- Phase 2: fixed table group management for long-term store configuration.

This design keeps Table separate from TableGroup, keeps Reservation separate
from Seating, and preserves the existing rule that Reservation creation does
not lock a specific table by default.

## Current State

The local runtime currently has one real database-backed fixed group:

```text
VIP-1
type: fixed
status: active
members: A01 + A02
capacity: 6-10
```

This is not frontend fake data. It comes from `table_groups` and
`table_group_members`.

Existing behavior:

- `GET /api/v1/stores/{storeId}/tables?includeGroups=true` returns dining
  tables and table groups.
- The table page shows table groups in the `桌台分组` section.
- The reservation create dialog can select an existing table group as an
  optional preassignment.
- Seating flows accept a `tableGroupId` and validate group status, capacity,
  members, locks, occupancy, and member table availability.
- Cleaning supports a table group target and transitions member tables together.

Missing behavior:

- Staff cannot create a temporary group from multiple available tables.
- Staff cannot edit, deactivate, or restore fixed table groups.
- The table page does not explain whether a group is fixed configuration or a
  temporary same-service group.

## Chosen Approach

Use a two-phase rollout.

Phase 1 uses an atomic "combine and seat" design for temporary groups. Staff
selects multiple available tables and completes a seating action in the same
business transaction. The backend creates the temporary TableGroup, assigns the
SeatingResource to that group, and occupies all member tables together.

This avoids a fragile state where a staff member creates a temporary group but
does not seat it, leaving an unowned group or an ambiguous lock.

Phase 2 adds fixed group management for store configuration. Fixed groups are
reusable recipes such as `VIP-1 = A01 + A02`; they are not one service session
by themselves.

## Alternatives Considered

### A. Standalone Temporary Group Create First

Staff creates a temporary group, then later selects it for seating.

Trade-off: this matches the literal "create group" wording but requires lock
ownership, expiry, cleanup, and recovery UI before the group is useful. It also
risks dangling temporary groups.

Decision: not first release.

### B. Atomic Combine And Seat

Staff selects multiple tables and seats the party as a temporary group in one
transaction.

Trade-off: less reusable as a standalone resource before seating, but safer and
closer to the real floor workflow.

Decision: chosen for Phase 1.

### C. Fixed Group Management First

Build CRUD for fixed groups before temporary grouping.

Trade-off: useful for managers, but it does not solve the immediate staff need
for same-day large parties and ad hoc combinations.

Decision: Phase 2.

## Phase 1: Temporary Table Groups

### Product Behavior

The table page gets a temporary grouping mode:

```text
Select available tables -> combine and seat -> occupied temporary group appears
```

Staff can select two or more available dining tables. The UI shows combined
capacity and member codes. The primary action is phrased as an operational
action, for example `组合入桌`, not just `创建分组`.

Supported sources in Phase 1:

- Walk-in direct seating.
- Arrived reservation direct seating.
- Called queue ticket seating.

Reservation creation for a future time does not create temporary groups.
Reservation creation may still preassign an existing fixed group.

### Temporary Group Lifecycle

The backend treats temporary groups as single-service resources.

Logical transition:

```text
none -> created -> locked -> occupied -> released -> ended
```

The implementation may perform `created -> locked -> occupied` within one
transaction for the combine-and-seat command. Audit and transition evidence must
still record the temporary group creation and use.

After guests leave:

```text
occupied -> cleaning -> available member tables
temporary group -> released -> ended
```

Fixed groups are not mutated just because their member tables were used.

### Backend Boundary

Phase 1 extends seating command behavior instead of adding a free-floating
temporary group create endpoint.

Existing seating APIs should accept exactly one resource target:

- `tableId`
- `tableGroupId`
- `temporaryTableIds`

`temporaryTableIds` is valid only when `tableId` and `tableGroupId` are absent.
It must contain at least two distinct dining table IDs.

The three seating flows should share the same temporary group creation service
or policy:

- Reservation arrived direct seating.
- Seating from called queue.
- Walk-in direct seating.

The shared service validates members, creates the temporary group and member
rows, creates the seating resource, occupies member tables, writes events,
writes state transitions, and writes audit records within the same transaction.

### Validation Rules

Temporary group creation must reject:

- Missing or duplicate member table IDs.
- Tables from another tenant or store.
- Inactive, occupied, cleaning, reserved, or locked tables.
- Tables that are not combinable.
- Existing active table locks on any member table.
- Active seating occupancy on any member table.
- Active cleaning on any member table.
- Capacity mismatch for the party size.
- Conflicting active reservation preassignment for the same business date.

Capacity rule:

- Combined group `capacityMin` should be the sum of member `capacityMin`.
- Combined group `capacityMax` should be the sum of member `capacityMax`.
- The party size must fit inside that combined range.

Group code rule:

Temporary group code should be deterministic enough for staff and audit, for
example:

```text
TMP-A01+A02-1530
```

The exact generated code must remain store-scoped and unique among active,
non-deleted table groups.

### API Shape

Each affected seating request should add:

```json
{
  "temporaryTableIds": [
    "70000000-0000-0000-0000-000000000981",
    "70000000-0000-0000-0000-000000000982"
  ]
}
```

Response should continue to return the actual seating resource:

```json
{
  "resourceType": "table_group",
  "resourceId": "generated-temporary-group-id",
  "occupiedTableIds": [
    "70000000-0000-0000-0000-000000000981",
    "70000000-0000-0000-0000-000000000982"
  ]
}
```

Stable error codes should include:

```text
TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED
TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE
TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE
TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT
TEMPORARY_TABLE_GROUP_LOCK_CONFLICT
TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT
RESOURCE_SELECTION_CONFLICT
```

Existing table group errors continue to apply to existing `tableGroupId`.

### Table Page UI

The table page should support a compact multi-select mode:

- A toolbar action enters grouping mode.
- Only currently selectable dining tables can be selected.
- Selected cards show a clear selected state.
- The footer shows member codes and combined capacity.
- Staff chooses the seating source:
  - walk-in direct seating;
  - arrived reservation if launched from a reservation context;
  - called queue if launched from a queue context.

For the first implementation, the table page can support walk-in combine and
seat directly, while reservation and queue flows can use the same multi-select
picker from their existing seating dialogs. This keeps the first UI slice small
without creating a fake group before seating.

### Table Resource List

`GET /api/v1/stores/{storeId}/tables` should continue to return both fixed and
temporary groups.

Table group response should make the group type visible to the frontend:

```json
{
  "resourceType": "table_group",
  "groupType": "temporary",
  "status": "occupied",
  "memberTableCodes": ["A01", "A02"]
}
```

If `groupType` is omitted for backward compatibility, the UI may treat the group
as fixed, but new backend responses should include it.

### Permissions

Phase 1 does not need a new standalone table group permission if temporary group
creation is embedded in existing seating actions.

Existing permissions continue to guard the source action:

- `walkin.direct_seating.create`
- `reservation.seat`
- `queue.seat`
- `table.view`
- `cleaning.start`
- `cleaning.complete`

If a separate temporary-group-only endpoint is later added, it must use a
separate permission.

### Audit And Events

Required evidence for successful temporary grouping:

- `table_group.temporary.created`
- `table_group.temporary.locked`
- `table_group.temporary.occupied`
- Existing source event such as `reservation.seated`, `queue.seated`, or
  `walkin.seated`.
- `seating.created`
- Member table occupancy transitions.

Cleaning/release evidence:

- Existing `cleaning.started` and `cleaning.completed`.
- `table_group.temporary.released`
- `table_group.temporary.ended`
- Member table transitions to `cleaning` and back to `available`.

Failure audit must capture the attempted member table IDs and failure reason.

## Phase 2: Fixed Table Group Management

### Product Behavior

Store managers can manage long-term reusable groups:

- Create fixed group.
- Edit members and capacity.
- Activate or deactivate group.
- Soft delete inactive group when no active references exist.

Fixed groups are configuration. They do not occupy or reserve tables by
themselves.

### UI Placement

Add fixed group management from the table page, but keep it visually separate
from same-day operations:

- `桌台分组` section shows fixed and temporary badges.
- A manager-only action opens `管理固定分组`.
- The management panel lists fixed groups with members, capacity, status, and
  actions.

Staff without manage permission can view groups but cannot change fixed
configuration.

### Fixed Group Rules

Create or edit must validate:

- At least two member tables.
- All member tables belong to the same tenant and store.
- No duplicate members in the same group.
- Member tables are combinable.
- Member tables are not soft-deleted or inactive.
- Capacity range is valid.
- Group code is unique within the store among non-deleted groups.

Deactivate or delete must reject:

- Active seating using the group.
- Active cleaning using the group.
- Active table lock using the group.
- Active reservation preassignment for the group.

Fixed groups may share member tables with temporary groups only when there is no
active operational conflict. A fixed group is a selectable recipe, not an active
occupancy by itself.

### API Shape

Recommended endpoints:

```text
GET    /api/v1/stores/{storeId}/table-groups
POST   /api/v1/stores/{storeId}/table-groups
PATCH  /api/v1/stores/{storeId}/table-groups/{tableGroupId}
POST   /api/v1/stores/{storeId}/table-groups/{tableGroupId}/activate
POST   /api/v1/stores/{storeId}/table-groups/{tableGroupId}/deactivate
DELETE /api/v1/stores/{storeId}/table-groups/{tableGroupId}
```

All mutation requests require idempotency keys.

Suggested permission:

```text
table.group.manage
```

Read access may use `table.view`.

## Data And Migration

The current schema already contains the required core tables:

- `table_groups`
- `table_group_members`
- `dining_tables`
- `table_locks`
- `seating_resources`
- `cleanings`
- audit and transition tables

No schema migration is expected for Phase 1.

Phase 2 may require App Gate permission metadata changes for
`table.group.manage`. If permission metadata is database-seeded in the target
environment, that implementation must go through database review before adding
a migration.

## Testing Strategy

Phase 1 backend tests:

- Reservation arrived direct seating with `temporaryTableIds` succeeds.
- Queue called seating with `temporaryTableIds` succeeds.
- Walk-in direct seating with `temporaryTableIds` succeeds.
- Duplicate member IDs fail.
- Occupied, cleaning, inactive, locked, or non-combinable member fails.
- Capacity mismatch fails.
- Active preassignment conflict fails.
- Successful seating creates a temporary table group and active seating
  resource with `resourceType = table_group`.
- Member tables become `occupied`.
- Cleaning a temporary group returns member tables to `available` and ends the
  temporary group.

Phase 1 frontend tests:

- Table page can enter and exit grouping mode.
- Only selectable dining tables can be selected.
- Combined capacity and member codes render.
- Multi-select request sends `temporaryTableIds`, not `tableId` or
  `tableGroupId`.
- Existing single-table and existing fixed-group paths still work.

Phase 2 backend tests:

- Fixed group create, edit, activate, deactivate, and delete.
- Store scope and permission denial.
- Duplicate group code rejection.
- Invalid member rejection.
- Deactivate/delete blocked by active seating, cleaning, lock, or preassignment.

Phase 2 frontend tests:

- Manage action visible only with manage permission.
- Fixed group form validates members and capacity.
- Fixed and temporary badges are distinct.
- Read-only staff can view but not mutate fixed groups.

## Out Of Scope

- Drag-and-drop floor plan.
- POS-specific table format in Vue.
- Auto table recommendation.
- Future reservation temporary group pre-creation.
- Payment, membership, marketing, delivery, inventory, or POS deep integration.
- AI recommendation.

## Acceptance Criteria

Phase 1 is acceptable when:

- Staff can combine multiple available tables for a same-day seating action.
- The backend creates a temporary TableGroup atomically with seating.
- Member tables become occupied together.
- Cleaning releases member tables together.
- Fixed group behavior remains unchanged.
- Existing `tableId` and existing `tableGroupId` seating flows continue to pass.
- No fake table data is introduced.

Phase 2 is acceptable when:

- Store managers can create and maintain fixed groups.
- Fixed groups can be selected in reservation creation and seating flows.
- Unsafe fixed group changes are blocked while resources are in active use.
- Permissions distinguish view from management.

