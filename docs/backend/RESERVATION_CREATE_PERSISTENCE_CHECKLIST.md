# Reservation Create Persistence Checklist V1

## 1. Scope Checklist

| Check | Result | Notes |
| --- | --- | --- |
| Only covers Create Reservation | Yes | Persistence boundary is limited to creating a confirmed Reservation. |
| CheckIn omitted | Yes | No CheckIn table, event flow, command, port, or persistence method is introduced. |
| Queue omitted | Yes | No QueueTicket creation or queue fallback is included. |
| Seating omitted | Yes | No Seating or SeatingResource persistence is included. |
| No-show omitted | Yes | No no-show transition, policy, event, or persistence method is included. |
| Cancellation omitted | Yes | No cancellation command, reason flow, or status mutation is included. |
| Table assignment omitted | Yes | No DiningTable/TableGroup assignment is included. |
| `reservation_preassignments` omitted | Yes | Preassignment remains a future boundary and is not used by Create Reservation V1. |
| `table_locks` omitted | Yes | Reservation Create V1 does not lock concrete table resources. |
| API/UI omitted | Yes | No Controller, REST API, API DTO, Vue page, or Vue component is designed or implemented here. |
| Migration unchanged | Yes | No migration or SQL file is created or modified. |

## 2. Port Checklist

| Port | Required For Create Reservation | Mechanical CRUD Avoided | Notes |
| --- | --- | --- | --- |
| `StoreRepositoryPort` | Yes | Yes | `findByScope(StoreScope)` or equivalent scoped lookup only. |
| `StorePolicyRepositoryPort` | Yes | Yes | Current Store policy lookup for hold and expected dining duration. |
| `CustomerRepositoryPort` | Yes | Yes | Tenant-scoped `findById`, `findByPhone`, and `save`; phone nullable. |
| `ReservationRepositoryPort` | Yes | Yes | `save`, duplicate check, capacity usage projection, code lookup. |
| `BusinessEventRepositoryPort` | Yes | Yes | Append Reservation event evidence only. |
| `StateTransitionLogRepositoryPort` | Yes | Yes | Append `null/draft -> confirmed` evidence only. |
| `AuditLogRepositoryPort` | Yes | Yes | Append `reservation.create` audit evidence only. |
| `IdempotencyRepositoryPort` | Yes | Yes | `create_reservation` guard, replay, conflict, fail, complete. |
| `QueueTicketRepositoryPort` | No | Yes | Forbidden for this slice. |
| `SeatingRepositoryPort` | No | Yes | Forbidden for this slice. |
| `TableLockRepositoryPort` | No | Yes | Forbidden for this slice. |
| `CleaningRepositoryPort` | No | Yes | Forbidden for this slice. |
| `TurnoverRepositoryPort` | No | Yes | Forbidden for this slice. |

## 3. Mapper Checklist

| Mapper | Required | Boundary |
| --- | --- | --- |
| `ReservationMapper` | Yes | Maps `reservations` row shape to Reservation domain shape. Does not decide availability, duplicate rules, or status transitions. |
| `CustomerMapper` | Yes | Preserves nullable phone and Tenant scope. Does not decide Customer identity sufficiency. |
| `StorePolicyMapper` | Yes | Maps policy fields. Does not derive time range or execute policy. |
| `BusinessEventMapper` | Yes | Maps event type, target, actor, source, timestamps, and metadata. Does not decide event necessity. |
| `StateTransitionLogMapper` | Yes | Maps transition evidence. Does not validate state machine legality. |
| `AuditLogMapper` | Yes | Maps audit operation and metadata. Does not decide audit requirement. |
| `IdempotencyMapper` | Yes | Maps status and response snapshot. Does not decide replay semantics. |

## 4. Capacity Usage Checklist

| Check | Result | Notes |
| --- | --- | --- |
| Capacity boundary clear | Yes | V1 uses Store + BusinessDate + TimeRange + PartySize. |
| Concrete table lock avoided | Yes | No `table_locks` or resource target is used. |
| Table assignment avoided | Yes | No `tableId`, `tableGroupId`, or preassignment boundary is used. |
| Active statuses defined | Yes | `confirmed`, `arrived`, `seated`. |
| Soft-deleted rows excluded | Yes | `deleted_at is not null` is excluded. |
| Cancelled/no-show/completed excluded | Yes | `cancelled`, `no_show`, `completed` do not hold active capacity. |
| Arbitrary overlap supported | Yes | Query must use overlap logic, not only identical start/end slot uniqueness. |
| Repository return boundary clear | Yes | Usage projection or aggregate total, not a business decision. |
| Rule layer responsibility clear | Yes | `ReservationAvailabilityRule` compares usage plus requested party size against Store capacity/policy. |

## 5. Duplicate Rule Checklist

| Check | Result | Notes |
| --- | --- | --- |
| Duplicate boundary clear | Yes | Same Tenant + Store + Customer + overlapping active time range. |
| Active statuses defined | Yes | `confirmed`, `arrived`, `seated`. |
| Soft-deleted rows excluded | Yes | Deleted Reservations do not block duplicates. |
| Phone not used as identity | Yes | Phone may help resolve Customer, but CustomerId is the duplicate key. |
| Nullable phone supported | Yes | No-phone Customers and temporary Customers remain valid. |
| Anonymous edge documented | Yes | If no CustomerId exists, customer duplicate check cannot apply; capacity check still applies. |

## 6. Time / UTC Checklist

| Check | Result | Notes |
| --- | --- | --- |
| `reservedStartAt` persisted explicitly | Yes | Maps to `reserved_start_at timestamptz`. |
| `reservedEndAt` persisted explicitly | Yes | Maps to `reserved_end_at timestamptz`; must exist before save. |
| Future API omission of end handled outside persistence | Yes | Application derives from `StorePolicy.expectedDiningDurationMinutes`. |
| UTC instant storage preserved | Yes | `timestamptz` stores fact instants. |
| Local wall time not persisted as fact | Yes | Store timezone is used for conversion and business date derivation. |
| `businessDate` source clear | Yes | Derived from `reservedStartAt` using Store timezone. |
| Store locale display separated | Yes | Formatting remains outside persistence. |

## 7. Idempotency Checklist

| Check | Result | Notes |
| --- | --- | --- |
| Action defined | Yes | `create_reservation`. |
| Store scope defined | Yes | Uses Tenant + Store scope. |
| Completed replay clear | Yes | Same hash returns previous result; no duplicate Reservation. |
| In-progress behavior clear | Yes | Same hash returns retry-later/conflict. |
| Failed behavior clear | Yes | Same hash requires new key. |
| Hash conflict clear | Yes | Same key with different hash returns `IDEMPOTENCY_CONFLICT`. |
| Missing key clear | Yes | `MISSING_IDEMPOTENCY_KEY`; no record exists. |
| Response snapshot boundary clear | Yes | Stores API/application result snapshot, not entity internals. |

## 8. Boundary Confirmation

CheckIn implemented: No  
Queue implemented: No  
Seating implemented: No  
No-show implemented: No  
Cancellation implemented: No  
Table assignment implemented: No  
Reservation preassignment used: No  
Table lock used: No  
API implemented: No  
UI implemented: No  
Migration changed: No  
Database touched: No  
Java Repository implementation created: No  
Mapper implementation created: No  
Application Service code created: No  
Controller created: No

## 9. Next Step Gate

This checklist allows the next round to enter:

```text
Reservation Create Persistence Implementation
```

The next round must still remain limited to Create Reservation persistence and must not jump to API, UI, CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, Migration, or SQL changes unless Product Owner scope changes explicitly.
