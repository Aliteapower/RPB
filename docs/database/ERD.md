# ERD V1

## Purpose

This document provides a text-only ERD for the Reservation Platform V1 database design round. It is not an image and not executable SQL.

## Scope

The ERD preserves these confirmed boundaries:

- Reservation != QueueTicket.
- WalkIn != QueueTicket.
- CheckIn != Seating.
- Seating != Reservation.
- Cleaning != Turnover.
- Table != TableGroup.
- Customer != Member.
- Cross-Tenant relationships are forbidden.

## Text ERD Overview

```text
Platform
  1 -> many Tenants

Tenant
  1 -> many Stores
  1 -> many Customers
  1 -> many Tenant Users / Roles
  1 -> many Tenant-level Reason Codes

Store
  1 -> many Store Operation Policies
  1 -> many Areas
  1 -> many Dining Tables
  1 -> many Queue Groups
  1 -> many Reservations
  1 -> many WalkIns
  1 -> many QueueTickets
  1 -> many Seatings
  1 -> many Cleanings
  1 -> many Turnovers

Area
  1 -> many Dining Tables

Dining Table
  many -> many TableGroups through TableGroupMembers
  1 -> many TableLocks
  1 -> many SeatingResources
  1 -> many Cleanings

TableGroup
  1 -> many TableGroupMembers
  1 -> many TableLocks
  1 -> many SeatingResources
  1 -> many Cleanings

Customer
  1 -> many Reservations
  1 -> many WalkIns
  1 -> many QueueTickets

Reservation
  0 -> many ReservationPreassignments
  0 -> 1 active QueueTicket when checked-in customer must wait
  0 -> 1 active Seating directly after CheckIn when table is available
  1 -> many BusinessEvents / StateTransitionLogs / AuditLogs

WalkIn
  0 -> 1 active QueueTicket when no table is available
  0 -> 1 active Seating directly when table is available
  1 -> many BusinessEvents / AuditLogs

QueueGroup
  1 -> many QueueTickets

QueueTicket
  optional source -> Reservation
  optional source -> WalkIn
  0 -> 1 active Seating
  1 -> many BusinessEvents / StateTransitionLogs / AuditLogs

Seating
  exactly one source -> Reservation or QueueTicket or WalkIn
  1 -> many SeatingResources
  0 -> many Cleanings
  0 -> 1 Turnover result
  1 -> many BusinessEvents / StateTransitionLogs / AuditLogs

SeatingResource
  exactly one resource -> DiningTable or TableGroup

Cleaning
  1 -> Seating
  exactly one cleaned resource -> DiningTable or TableGroup
  0 -> 1 Turnover input/result
  1 -> many BusinessEvents / StateTransitionLogs / AuditLogs

Turnover
  derived from -> Seating + completion event + Cleaning

IdempotencyRecord
  optional related target -> Reservation / QueueTicket / WalkIn / Seating / Cleaning / TableLock / Integration Call

AuditLog
  related target -> any audited business object or event

BusinessEvent
  related target -> any event-producing business object

StateTransitionLog
  related target -> any stateful business object
```

## Core Entity Relationships

| Parent | Relationship | Child | Cardinality | Notes |
|---|---|---|---|---|
| Tenant | owns | Store | 1 -> many | Store cannot exist outside Tenant. |
| Tenant | owns | Customer | 1 -> many | Customer uniqueness is Tenant-scoped and shared across Stores. |
| Store | owns | Area | 1 -> many | Area belongs to one Store. |
| Area | contains | DiningTable | 1 -> many | DiningTable belongs to one Store through Area and direct Store scope. |
| Store | owns | DiningTable | 1 -> many | Store is the operational table-resource boundary. |
| Store | owns | TableGroup | 1 -> many | Fixed and temporary groups are Store-scoped. |
| TableGroup | contains | DiningTable | many -> many | Implemented conceptually through TableGroupMembers. |
| Store | owns | Reservation | 1 -> many | Reservation locks time-slot capacity, not a specific Table by default. |
| Customer | makes | Reservation | 1 -> many | Customer can be normal, temporary, anonymous, or no-phone. |
| Store | owns | WalkIn | 1 -> many | WalkIn can go direct to Seating or QueueTicket. |
| Customer | appears as | WalkIn | 1 -> many | Customer can be temporary or anonymous. |
| Store | owns | QueueGroup | 1 -> many | V1 default groups are party-size bands. |
| QueueGroup | groups | QueueTicket | 1 -> many | Queue number uniqueness is scoped by QueueGroup and queue operating window. |
| Reservation | may produce | QueueTicket | 0 -> 1 active | Only after CheckIn and only when no resource is available. |
| WalkIn | may produce | QueueTicket | 0 -> 1 active | Only when no resource is available. |
| Reservation | may produce | Seating | 0 -> 1 active | Direct Seating after CheckIn when resource is available. |
| QueueTicket | may produce | Seating | 0 -> 1 active | Seating after waiting/called flow. |
| WalkIn | may produce | Seating | 0 -> 1 active | Direct Seating without queue when resource is available. |
| Seating | uses | DiningTable or TableGroup | 1 -> many resources | Represented by SeatingResources. |
| Seating | starts | Cleaning | 0 -> many | Cleaning follows occupancy completion or guest departure. |
| Cleaning | releases | DiningTable or TableGroup | exactly 1 resource | Resource becomes available after cleaning completion unless inactive or blocked. |
| Seating + Cleaning | produces | Turnover | 0 -> 1 | Turnover is result/metric, not live action. |

## One-to-Many Relationships

- Tenant -> Store.
- Tenant -> Customer.
- Tenant -> Tenant-level ReasonCode.
- Store -> Area.
- Store -> DiningTable.
- Store -> StoreOperationPolicy.
- Store -> QueueGroup.
- Store -> Reservation.
- Store -> WalkIn.
- Store -> QueueTicket.
- Store -> Seating.
- Store -> Cleaning.
- Store -> Turnover.
- Reservation -> BusinessEvent.
- QueueTicket -> BusinessEvent.
- Seating -> SeatingResource.
- Seating -> Cleaning.
- Any stateful object -> StateTransitionLog.
- Any audited object -> AuditLog.

## Many-to-Many Relationships

| Relationship | Join Boundary | Rule |
|---|---|---|
| TableGroup <-> DiningTable | TableGroupMembers | All member Tables must belong to the same Tenant and Store as the TableGroup. |
| Seating <-> DiningTable/TableGroup | SeatingResources | One Seating can occupy one or more resources, but active overlap is forbidden. |
| User <-> Role | UserRoleAssignments in later identity design | Must respect Platform, Tenant, or Store role scope. |

## Optional Relationships

- Reservation -> QueueTicket is optional and only exists when checked-in customer must wait.
- Reservation -> ReservationPreassignment is optional and must not equal final Seating.
- WalkIn -> QueueTicket is optional because WalkIn can be seated directly.
- Seating -> Reservation is optional because Seating can come from QueueTicket or WalkIn.
- Seating -> QueueTicket is optional because Seating can come from Reservation or WalkIn.
- Seating -> WalkIn is optional because Seating can come from Reservation or QueueTicket.
- Cleaning -> TableGroup is optional because Cleaning may target a single DiningTable.
- Cleaning -> DiningTable is optional when the cleaned resource is a TableGroup.
- store_id on Customer is not required because Customer is Tenant-scoped.
- store_id on audit/event records is nullable when the audited action is Tenant-level or Platform-level.

## Cross-Store Shared Relationships

- Customer is shared across Stores inside the same Tenant.
- Tenant-level reason codes may be shared across Stores with optional Store override.
- Tenant-level policy templates may be shared across Stores, while store_operation_policies hold the Store-specific applied values.
- Platform i18n message catalog may be shared globally; Tenant overrides remain inside the Tenant.

Cross-Store sharing does not permit cross-Tenant sharing.

## Forbidden Cross-Tenant Relationships

The following are forbidden:

- Store referencing another Tenant.
- Customer shared between Tenants.
- Reservation referencing Customer from another Tenant.
- QueueTicket referencing Reservation, WalkIn, QueueGroup, or Customer from another Tenant.
- Seating referencing source flow or table resource from another Tenant.
- TableGroup containing Tables from another Tenant.
- Audit or event record hiding the true Tenant scope of a Tenant-owned action.

## TableGroup and Table Relationship

```text
Store
  1 -> many DiningTables
  1 -> many TableGroups

TableGroup
  1 -> many TableGroupMembers

TableGroupMember
  many -> 1 TableGroup
  many -> 1 DiningTable
```

Rules:

- Fixed TableGroup is long-term configuration.
- Temporary TableGroup is a single-service resource.
- All members must be in the same Store.
- Circular TableGroup membership is forbidden.
- A DiningTable cannot be in multiple effective active temporary groups at the same time.
- Temporary TableGroup must release its member Tables when released or ended.

## Reservation and QueueTicket Boundary

```text
Reservation
  0 -> 1 active QueueTicket
```

Rules:

- Reservation does not automatically create QueueTicket.
- Reservation is advance time-slot capacity.
- QueueTicket is waiting after arrival.
- QueueTicket for Reservation can exist only after CheckIn and when no suitable table is available.
- Reservation must not depend on QueueTicket to be valid.
- QueueTicket must not be used as the source of advance reservation capacity.

## Seating Source Boundary

```text
Seating
  exactly one source:
    Reservation
    QueueTicket
    WalkIn
```

Rules:

- Reservation source means checked-in customer was seated directly.
- QueueTicket source means customer waited before being seated.
- WalkIn source means WalkIn customer was seated directly without queue.
- Seating is not Reservation creation.
- Seating is not QueueTicket creation.
- Seating creates occupancy of DiningTable or TableGroup resources.

## Cleaning Resource Boundary

```text
Cleaning
  1 -> Seating
  exactly one cleaned resource:
    DiningTable
    TableGroup
```

Rules:

- Cleaning begins after occupancy ends or moves toward completion.
- Cleaning blocks resource availability until completed.
- Cleaning is not Turnover by itself.
- Cleaning completion feeds Turnover calculation or recorded result.

## Turnover Source Relationship

```text
Turnover
  derived from:
    Seating
    Seating completion
    Cleaning completion
```

Rules:

- Turnover is a result or metric.
- Turnover is not a live seating or cleaning action.
- Turnover must not be calculated from Reservation alone.
- Turnover remains Store-scoped and Tenant-scoped.

## Queue Group Relationship

```text
Store
  1 -> many QueueGroups

QueueGroup
  1 -> many QueueTickets
```

V1 default groups:

- 1-2 people.
- 3-4 people.
- 5-6 people.
- 7+ people.

Queue number uniqueness:

```text
Tenant + Store + queue operating window + QueueGroup + ticket number
```

This is a logical uniqueness boundary, not executable SQL.
