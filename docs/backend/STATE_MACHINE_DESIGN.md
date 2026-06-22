# State Machine Design V1

## Purpose

This document defines backend state machine boundaries for the Reservation Platform. It is design-only and does not create code, migrations, SQL, API, UI, tests, seed data, or database connections.

Migration validation confirmed that status check constraints exist, but database constraints only protect allowed values. Legal transitions must be centralized in backend StateMachine / TransitionPolicy design.

## Shared Transition Contract

Every state transition should be modeled as a domain transition decision with:

- Target object type and id.
- Tenant scope and Store scope where applicable.
- From status and requested to status.
- Trigger actor type, actor id, actor role, and source.
- Transition code.
- Preconditions and domain validation results.
- Before and after snapshots where audit requires them.
- Optional reason code.
- Optional idempotency key.
- AuditLog requirement.
- StateTransitionLog requirement.

Transition logic must not be scattered across Controller, API DTO, page logic, or ad hoc Service methods.

## Reservation State Machine

Initial state: `draft`.

Terminal states: `completed`, `cancelled`, `no_show`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | draft | staff, customer, integration | TenantScope and StoreScope valid; required reservation intent present; party size positive; reserved range valid. | Draft Reservation exists; no confirmed capacity until confirm. | Yes | Yes | Yes for create |
| draft | confirmed | staff, customer, integration | ReservationAvailabilityRule passes; ReservationDuplicateRule passes; StorePolicy available. | Time-slot capacity becomes held; `hold_until_at` set by ReservationHoldPolicy. | Yes | Yes | Yes |
| draft | cancelled | staff, customer, integration | Cancellation reason captured when required. | Draft no longer active. | Yes | Yes | Recommended |
| confirmed | arrived | staff CheckIn or equivalent audited arrival | Reservation still active; customer has arrived; CheckIn idempotency accepted. | CheckIn BusinessEvent recorded; seating or queue decision may follow. | Yes | Yes | Yes |
| confirmed | cancelled | staff, customer, integration | Cancellation allowed by policy; reason captured. | Capacity released where applicable. | Yes | Yes | Yes |
| confirmed | no_show | staff or system no-show policy | No-show window reached; reason/source captured. | Capacity released; failure reason recorded. | Yes | Yes | Yes |
| arrived | seated | staff Seating | SeatingSourceValidator passes; TableAssignmentRule and TableLockRule pass. | Seating created/occupied; Reservation status moves to seated. | Yes | Yes | Yes |
| arrived | cancelled | staff or customer policy | Cancellation reason captured; no active Seating. | Arrival flow ends without occupancy. | Yes | Yes | Yes |
| arrived | no_show | staff or system policy | Allowed only if Store policy permits after arrived state and no Seating exists. | Capacity released; reason captured. | Yes | Yes | Yes |
| seated | completed | staff or system completion | Related Seating completed; cleaning path started or completed by policy. | Reservation terminal success. | Yes | Yes | Yes |

Illegal transitions:

- `draft -> arrived`, `draft -> seated`, `draft -> completed`, `draft -> no_show`.
- `confirmed -> seated` without CheckIn/arrived.
- Any terminal state to any non-terminal state.
- `arrived -> confirmed` unless a later reversal policy is explicitly approved.
- `seated -> arrived`, `seated -> cancelled`, or `seated -> no_show`.

## QueueTicket State Machine

Initial state: `waiting`.

Terminal states: `seated`, `cancelled`, `expired`.

Queue call hold duration: Store configurable, V1 default 3 minutes from `called_at`.

Rejoin rule: skipped tickets keep original number and do not cut the queue by default. V1 default placement is tail of same QueueGroup unless Store policy later changes it.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | waiting | staff, customer, integration, system | QueueGroupPolicy matches party size; source is arrived Reservation or WalkIn when present; QueueOrderingPolicy assigns number/position. | Active waiting ticket exists. | Yes | Yes | Yes for create |
| waiting | called | staff or system | Ticket active; no stale call; QueueCallingRule passes. | `called_at` set; call hold window starts. | Yes | Yes | Yes |
| waiting | cancelled | staff or customer | Cancellation reason captured where required. | Queue flow ends. | Yes | Yes | Yes |
| waiting | expired | system policy | Expiry condition reached. | Queue flow ends as timeout. | Yes | Yes | Yes |
| called | seated | staff Seating | Seating flow and TableLockRule pass before call window expires or override reason exists. | Ticket terminal success; Seating owns occupancy. | Yes | Yes | Yes |
| called | skipped | staff or system timeout | Current time exceeds call hold window or manual skip reason captured. | `skipped_at` set; ticket can rejoin. | Yes | Yes | Yes |
| called | cancelled | staff or customer | Cancellation reason captured. | Queue flow ends. | Yes | Yes | Yes |
| called | expired | system policy | Expiry condition reached. | Queue flow ends. | Yes | Yes | Yes |
| skipped | rejoined | staff or customer policy | Ticket not expired/cancelled; QueueRejoinRule passes. | `rejoined_at` set; original ticket number retained. | Yes | Yes | Yes |
| skipped | cancelled | staff or customer | Cancellation reason captured. | Queue flow ends. | Yes | Yes | Yes |
| skipped | expired | system policy | Expiry condition reached. | Queue flow ends. | Yes | Yes | Yes |
| rejoined | waiting | staff or system placement | QueueOrderingPolicy places at tail of same group by default. | Ticket becomes eligible for future call. | Yes | Yes | Yes |
| rejoined | called | staff or system | Policy allows direct call and QueueCallingRule passes. | Call hold window starts again. | Yes | Yes | Yes |
| rejoined | cancelled | staff or customer | Cancellation reason captured. | Queue flow ends. | Yes | Yes | Yes |
| rejoined | expired | system policy | Expiry condition reached. | Queue flow ends. | Yes | Yes | Yes |

Illegal transitions:

- `waiting -> skipped` without call.
- `called -> rejoined` without skipped.
- `skipped -> seated` without rejoin or explicit audited override.
- Any terminal state to non-terminal state.
- Creating a new number by default for rejoin.
- Rejoin placement that cuts queue unless a future Store policy explicitly allows it.

## DiningTable State Machine

Initial operational state: `available` after table setup and activation.

Terminal-like state: `inactive` for operations. It may return to `available` only by a configuration reactivation policy after all active blockers are cleared.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| available | locked | TableLockRule | Table active, clean, not occupied, not blocked by TableGroup, lock expiry set. | Temporary lock prevents duplicate assignment. | Yes | Yes | Yes |
| available | reserved | ReservationPreassignment or reservation policy | Table active; optional preassignment valid; not occupied. | Resource protected for future Reservation but not occupied. | Yes | Yes | Yes |
| available | inactive | staff configuration | No active seating, cleaning, lock, or active temporary TableGroup. | Removed from assignment and recommendation. | Yes | Yes | Recommended |
| locked | available | lock release or expiry | Lock expired or released; no SeatingResource active. | Table can be assigned. | Yes | Yes | Yes |
| locked | reserved | staff or reservation policy | Lock owned by current workflow; ReservationPreassignment valid. | Table reserved, still not occupied. | Yes | Yes | Yes |
| locked | occupied | Seating | SeatingSourceValidator, SeatingResourceValidator, and TableAssignmentRule pass. | Table occupied through active SeatingResource. | Yes | Yes | Yes |
| reserved | available | release preassignment | No active occupancy; reservation preassignment released/cancelled. | Table can be assigned again. | Yes | Yes | Yes |
| reserved | locked | staff or system assignment flow | Lock needed for final seating decision. | Table protected for seating decision. | Yes | Yes | Yes |
| reserved | occupied | Seating | Valid Seating and lock/preassignment ownership. | Table occupied. | Yes | Yes | Yes |
| reserved | inactive | staff configuration | No active occupancy and policy allows deactivation. | Removed from operations. | Yes | Yes | Recommended |
| occupied | cleaning | Seating completion or guest departure | Active SeatingResource completed/released into cleaning path. | Table unavailable until cleaning complete. | Yes | Yes | Yes |
| cleaning | available | Cleaning completion | Cleaning completed and resource released; table not inactive. | Table can be assigned. | Yes | Yes | Yes |
| cleaning | inactive | staff configuration | Cleaning completed or policy allows inactive after cleaning. | Table removed from operations. | Yes | Yes | Recommended |
| inactive | available | staff reactivation | No active blockers; StoreScope valid. | Table becomes assignable. | Yes | Yes | Recommended |

Key distinctions:

- `reserved` means planned capacity or optional preassignment. It is not physical occupancy.
- `occupied` requires a valid Seating and active SeatingResource.
- `locked` must expire or be released.
- `cleaning` must complete before `available`.
- `inactive` cannot be used for Reservation, Queue, Seating, or recommendation.
- Temporary TableGroup release must release or recompute member Table status through TableGroupValidationRule and TableAssignmentRule.

Illegal transitions:

- `available -> occupied` without lock/assignment policy and valid Seating.
- `occupied -> available` directly without Cleaning policy.
- `cleaning -> occupied` without completing and creating a new Seating.
- `inactive -> locked`, `inactive -> reserved`, or `inactive -> occupied`.

## Seating State Machine

Initial state: `planned`.

Terminal states: `completed`, `cleaning_triggered`, `cancelled`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | planned | staff Seating command | Exactly one source selected; party size snapshot positive; StoreScope valid. | Planned Seating exists but no occupancy yet. | Yes | Yes | Yes |
| planned | locked | TableLockRule | Resource candidates valid; locks acquired. | Seating protected from race. | Yes | Yes | Yes |
| planned | cancelled | staff | No active resource occupancy. | Seating plan stops. | Yes | Yes | Yes |
| locked | occupied | staff Seating | SeatingResourceValidator passes; active resource assigned. | Occupancy begins; `seated_at` set. | Yes | Yes | Yes |
| locked | cancelled | staff/system | Lock released or cancelled. | No occupancy. | Yes | Yes | Yes |
| occupied | completed | staff/system completion | Active occupancy exists; completion time captured. | Occupancy ends; cleaning can start. | Yes | Yes | Yes |
| completed | cleaning_triggered | staff/system | Cleaning record created or linked. | Cleaning owns resource release path. | Yes | Yes | Yes |

Illegal transitions:

- Any state to `occupied` without source XOR, table availability, lock, and resource validation.
- `completed -> occupied`.
- `cancelled -> any active state`.
- `planned -> completed` without occupancy.

## Cleaning State Machine

Initial state: `pending`.

Terminal states: `released`, `cancelled`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | pending | Seating completion | Seating completed; resource target is exactly one Table or TableGroup. | Resource blocked for cleaning. | Yes | Yes | Yes |
| pending | cleaning | staff | Cleaner starts work; resource still blocked. | `started_at` set. | Yes | Yes | Recommended |
| pending | cancelled | staff/system | Cleaning no longer needed and resource release policy allows it. | Cleaning flow stops with reason. | Yes | Yes | Recommended |
| cleaning | completed | staff/system | Cleaning task completed; `completed_at` set. | Resource can be released. | Yes | Yes | Yes |
| completed | released | staff/system | Resource release succeeds; DiningTable/TableGroup status updated. | Resource available or inactive according to table policy. | Yes | Yes | Yes |
| cleaning | cancelled | staff/system | Cancellation reason captured; resource release policy decides next state. | Cleaning flow stops with audit. | Yes | Yes | Recommended |

Illegal transitions:

- `pending -> released` without policy-approved completion.
- `cleaning -> available table` without `completed`.
- `released -> cleaning`.
- `cancelled -> completed`.

## Turnover State Machine

Initial state: `pending`.

Terminal-like states: `recorded`, `archived`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | pending | Seating completion or Cleaning start | Seating exists; StoreScope valid. | Turnover candidate created for the service cycle. | Recommended | Yes | Recommended |
| pending | recorded | Cleaning completion or turnover calculation | Seating completion and cleaning completion data available; duration non-negative. | Turnover metric recorded. | Recommended | Yes | Yes |
| recorded | archived | retention/archive policy | Record is no longer active for operations. | Historical metric remains readable. | Recommended | Yes | Recommended |

Illegal transitions:

- Turnover from Reservation alone.
- `archived -> pending`.
- `pending -> archived` without recording or approved archive policy.

## TableGroup State Machine

TableGroup has type-specific status groups.

Fixed TableGroup:

| From | To | Trigger | Preconditions | Post-Effects |
|---|---|---|---|---|
| none | created | store manager | StoreScope valid; members not yet active or validated. | Fixed group draft exists. |
| created | active | store manager | TableGroupValidationRule passes. | Group can be recommended. |
| active | inactive | store manager | No active occupancy or unresolved lock. | Group is not recommended. |
| inactive | active | store manager | Members valid and active. | Group can be recommended again. |
| inactive | deleted | store manager | No active references except history. | Group removed from future use. |

Temporary TableGroup:

| From | To | Trigger | Preconditions | Post-Effects |
|---|---|---|---|---|
| none | created | seating/assignment flow | Candidate member tables identified. | Temporary group candidate exists. |
| created | locked | TableLockRule | All member tables lockable and same Store. | Members protected from other assignments. |
| locked | occupied | Seating | SeatingResource active and group valid. | Group occupied as one resource. |
| occupied | released | Seating/Cleaning release | Occupancy completed and release allowed. | Member tables can move toward cleaning/available. |
| released | ended | system/staff | Member release finalized. | Temporary group closed. |

AuditLog and StateTransitionLog are required for TableGroup create, activate, lock, occupy, release, end, and delete actions. Idempotency is required for temporary group creation/release and recommended for fixed group configuration changes.

## TableLock State Machine

Initial state: `active`.

Terminal states: `released`, `expired`, `cancelled`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | active | TableLockRule | Resource exists and is available; no active conflicting lock; `locked_until_at > locked_at`. | Lock prevents duplicate assignment. | Yes | Recommended | Yes |
| active | released | owner/system | Lock owner or source workflow releases. | Resource can proceed to reservation/occupancy or become available. | Yes | Recommended | Yes |
| active | expired | system timeout | Current time past `locked_until_at`. | Lock no longer blocks assignment. | Yes | Recommended | Yes |
| active | cancelled | owner/system | Source workflow cancelled. | Lock removed from active contention. | Yes | Recommended | Yes |

Illegal transitions:

- Reopening terminal lock without creating a new lock.
- Active lock without resource scope.
- Active lock past expiry without timeout handling.

## IdempotencyRecord State Machine

Initial state: `started`.

Terminal states: `completed`, `failed`, `expired`.

| From | To | Trigger | Preconditions | Post-Effects | AuditLog | StateTransitionLog | Idempotency |
|---|---|---|---|---|---|---|---|
| none | started | command intake | Unique scope/source/action/key accepted; request hash stored. | Command can execute once. | Recommended | No | Core object |
| started | completed | command success | Business operation completed; response snapshot or result pointer available. | Identical replay returns same outcome. | Recommended | No | Core object |
| started | failed | command failure | Failure captured. | Replay policy decides retry vs same failure. | Recommended | No | Core object |
| started | expired | expiry job | Expiry boundary reached. | Key no longer protects future replay. | Recommended | No | Core object |
| failed | expired | expiry job | Expiry boundary reached. | Failed key retired. | Recommended | No | Core object |
| completed | expired | expiry job | Retention/expiry reached. | Completed key retired by policy. | Recommended | No | Core object |

Illegal transitions:

- `completed -> started`.
- `expired -> started`.
- Reusing same key with different request hash.

## Centralized Illegal Transition Handling

Future implementation should expose one reusable transition decision result:

```text
TransitionDecision
  accepted: true/false
  target_type
  from_status
  to_status
  violation_code
  audit_required
  state_transition_log_required
  idempotency_required
```

Failure must return stable violation codes, not display text. User-facing text must be resolved through i18n in later API/UI rounds.

## Not Created In This Round

- No Java state machine classes.
- No enum classes.
- No services, controllers, entities, repositories, DTOs, or tests.
- No migration, SQL, seed data, or database connection.
