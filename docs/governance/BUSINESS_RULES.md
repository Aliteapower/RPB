# Business Rules

## Purpose

This document captures the confirmed business rules for the Reservation Platform V1 governance round. It is a rule document, not a database design, API design, UI design, or implementation plan.

## Rule Status

| Status | Meaning |
|---|---|
| Confirmed | Explicitly confirmed by the Product Owner startup task. |
| Defaulted | Temporarily adopted as a project default until a later design round refines it. |
| Open | Still needs Product Owner confirmation before later database, API, UI, or implementation work. |
| Open Conflict | Existing project material conflicts with the startup task or another confirmed rule. |

## Confirmed Core Rules

| ID | Rule | Status |
|---|---|---|
| BR-001 | Reservation does not necessarily generate QueueTicket. | Confirmed |
| BR-002 | Reservation and QueueTicket are independent business objects. | Confirmed |
| BR-003 | A reserved customer must CheckIn after arrival before the system decides Seating or Queue. | Confirmed |
| BR-004 | WalkIn can be seated directly without taking a queue number when a suitable table is available. | Confirmed |
| BR-005 | CheckIn is a business event in V1, not a primary business entity. | Confirmed |
| BR-006 | Seating is a business event plus table occupancy record. | Confirmed |
| BR-007 | Cleaning is the table status flow from occupied toward available. | Confirmed |
| BR-008 | Turnover is a business result or metric derived from Seating + Completed + Cleaning. | Confirmed |
| BR-009 | Reservation locks time-slot capacity by default: Store + date + time slot + party size. | Confirmed |
| BR-010 | Reservation may support optional table pre-assignment but does not lock a specific table by default. | Confirmed |
| BR-011 | Customer uniqueness scope is Tenant. | Confirmed |
| BR-012 | Phone number is not required for Customer. | Confirmed |
| BR-013 | No-phone customers must be supported through temporary customer number and scenario information. | Confirmed |
| BR-014 | Fixed TableGroup is configuration; Temporary TableGroup is a single-service resource. | Confirmed |
| BR-015 | Rejoined skipped queue tickets keep the original number but do not cut the queue by default. | Confirmed |
| BR-016 | tenant_id and store_id requirements depend on data level and must not be mechanically required on all data. | Confirmed |
| BR-017 | Business flow is Reservation / WalkIn -> CheckIn when applicable -> QueueTicket when needed -> Seating -> Cleaning -> Turnover. | Confirmed |
| BR-018 | V1 must be mobile-first for store operations. | Confirmed |
| BR-019 | V1 must not include complex drag-and-drop floor plans, native app, AI recommendation, payment, marketing, points, microservices, Kubernetes, delivery, inventory, supply chain, complex BI, ads, coupons, prepaid cards, or deep POS integration. | Confirmed |
| BR-020 | Reservation hold duration is Store configurable; V1 default is 15 minutes after reservation time. | Confirmed |
| BR-021 | Queue call-hold duration is Store configurable; V1 default is 3 minutes after call. | Confirmed |
| BR-022 | Expected dining duration is Store configurable; V1 default is 90 minutes. Future extension may vary by business type, party size, table type, and time slot. | Confirmed |
| BR-023 | Same Tenant + Store + Customer + time slot cannot have multiple active confirmed / arrived / seated Reservations. | Confirmed |
| BR-024 | V1 QueueGroup is Store + Party Size Group by default: 1-2, 3-4, 5-6, and 7+. | Confirmed |

## Defaulted Rules

| ID | Rule | Status |
|---|---|---|
| DR-001 | No additional Codex-invented default business rule is introduced in this governance round. Startup-task defaults with explicit conclusions are treated as Confirmed unless the Product Owner later changes them. | Defaulted |

## Reservation Rules

- Reservation represents advance capacity intent for one Store, date, time slot, and party size.
- Reservation does not automatically create QueueTicket.
- Reservation status changes must pass through the Reservation state machine.
- A confirmed Reservation changes to arrived only through CheckIn or an equivalent audited arrival event.
- A Reservation can reach seated only after a valid Seating assigns a Table or TableGroup.
- cancelled, no_show, and completed are terminal states.
- Optional table pre-assignment must not bypass table locks, occupancy checks, or store scope checks.
- Same-customer duplicate Reservation in the same Store and same time slot is blocked for active confirmed, arrived, and seated Reservations.

## Queue Rules

- QueueTicket is created only when the customer has arrived and must wait for a table resource.
- QueueTicket may originate from an arrived Reservation or from a WalkIn.
- QueueTicket numbers are unique within the relevant queue group.
- called can become skipped when the customer does not respond within the configured call-hold duration.
- skipped can become rejoined.
- Rejoined tickets keep the original number and are placed according to Store queue policy; default is the tail of the same group.
- Queue ordering must be auditable when manually overridden.

## WalkIn Rules

- WalkIn means a customer arrives without advance Reservation.
- WalkIn can go directly to Seating when a suitable table resource is available.
- WalkIn creates QueueTicket only when no suitable table resource is available.
- WalkIn must support anonymous, temporary, no-phone, and special-note guests.
- WalkIn is a scenario and must not be treated as the same object as QueueTicket.

## CheckIn Rules

- CheckIn is the arrival-confirmation event.
- In V1, CheckIn is not a primary business entity.
- For Reservation flow, CheckIn changes confirmed to arrived.
- CheckIn does not create table occupancy.
- CheckIn must be audited with actor, role, tenant scope, store scope, source, time, before/after state, idempotency key, related object, and failure reason when applicable.

## Seating Rules

- Seating is the formal event that assigns a customer flow to a Table or TableGroup.
- Seating creates table occupancy.
- Seating may originate from Reservation, QueueTicket, or WalkIn.
- Seating updates Reservation or QueueTicket status when applicable.
- Seating must respect table locks, table availability, TableGroup validity, Store scope, Tenant scope, and idempotency.
- Manual table assignment override is allowed but must require an audited reason.
- Seating cannot occupy inactive, cleaning, already occupied, or invalidly grouped tables.

## Cleaning Rules

- Cleaning starts after the guest leaves or Seating is completed.
- Cleaning protects the Table or TableGroup from new occupancy until completed.
- Cleaning completion releases the resource back to available unless the resource is inactive or otherwise blocked.
- Cleaning is not payment, marketing, or turnover reporting by itself.
- Cleaning start, completion, and manual override must be audited.

## Turnover Rules

- Turnover is an operational result or metric.
- Turnover is derived from Seating + Completed + Cleaning.
- Turnover must not be calculated from Reservation alone.
- Turnover is useful for table-cycle analysis, operational dashboard boundaries, and future reporting.
- V1 governance may define Turnover meaning; it must not implement BI or complex analytics.

## Table Rules

- Table belongs to one Store and one Area.
- Table status must include at least available, locked, reserved, occupied, cleaning, and inactive.
- available means usable for assignment.
- locked means temporarily protected for an in-progress decision and must expire.
- reserved means allocated to reservation capacity or optional pre-assignment, but not physically occupied.
- occupied means a Seating is active.
- cleaning means the table is not available until cleaning completes.
- inactive cannot be reserved, queued, seated, or assigned.
- The same table cannot be actively occupied by more than one valid flow at the same time.

## TableGroup Rules

- Fixed TableGroup is long-term Store configuration.
- Temporary TableGroup is a single-service resource.
- All tables in a TableGroup must belong to the same Store and Tenant.
- TableGroup must prevent circular reference.
- One table cannot appear in multiple effective active groups at the same time.
- Temporary TableGroup must release all member Tables when released or ended.
- Fixed TableGroup cannot be recommended after it is inactive.

## Customer Rules

- Customer uniqueness scope is Tenant.
- The same Tenant can share Customer across Stores.
- Different Tenants must not share Customer identity.
- Phone number is optional.
- Phone number, when present, must follow E.164.
- No-phone Customer lookup must support name, nickname, arrival time, party size, note, table number, temporary number, QueueTicket number, or Reservation code.
- Customer is not Member; membership and loyalty are integration boundaries outside V1.

## Multi-Tenant Rules

- Platform-level data does not require tenant_id or store_id by default.
- Tenant-level data requires tenant_id and generally does not require store_id.
- Store-level operational data requires tenant_id and store_id.
- Cross-store shared data requires tenant_id and may have nullable store_id depending on ownership.
- Audit data requires tenant scope when tenant-related and may have nullable store scope.
- Cross-Tenant references are forbidden.
- Store operational data must not reference resources from another Store.

## Internationalization Rules

- Internationalization is P0 and must be established from day one.
- Time must be stored in UTC and ISO8601.
- Time display is resolved by Store configuration.
- Store must define timezone, locale, date_format, time_format, and currency.
- Singapore default is timezone Asia/Singapore, locale en-SG, date format DD-MM-YYYY, 24H time, and SGD.
- Phone number format is E.164 when phone exists.
- UI and business messages must use i18n keys and must not hardcode display text in code.

## State Machine Rules

### Reservation State Machine

| State | Meaning | Allowed Transitions | Illegal Transitions | Trigger | Preconditions | Business Impact | Audit / Idempotency / Concurrency |
|---|---|---|---|---|---|---|---|
| draft | Draft reservation. | confirmed, cancelled | seated, completed, no_show | staff, customer, integration | Required reservation context is present. | No confirmed capacity until confirmed. | Audit create/update; idempotent create by request key. |
| confirmed | Confirmed reservation. | arrived, cancelled, no_show | seated without CheckIn, completed | staff, customer, integration, system no-show policy | Capacity rule accepted; not cancelled. | Holds time-slot capacity. | Audit; protect duplicate confirmation. |
| arrived | Customer checked in. | seated, cancelled, no_show where policy allows | draft, confirmed without reversal policy, completed | staff CheckIn | Customer has arrived; Store scope valid. | Starts seating or queue decision. | Audit CheckIn; idempotent arrival event. |
| seated | Customer seated. | completed | draft, confirmed, arrived without reversal policy, no_show | staff Seating | Valid Table or TableGroup lock and assignment. | Creates occupancy and affects queue/reservation state. | Audit; lock resources; prevent duplicate seating. |
| completed | Service completed. | none | any non-terminal state | staff or system completion | Seating completed and cleaning path started or completed by policy. | Normal terminal state. | Audit terminal transition. |
| cancelled | Cancelled reservation. | none | any non-terminal state | customer, staff, integration, system | Cancellation reason captured. | Releases reservation capacity where applicable. | Audit reason and actor. |
| no_show | Customer did not arrive. | none | any non-terminal state | staff or system no-show policy | No-show window reached. | Releases capacity and records failure reason. | Audit reason and source. |

### QueueTicket State Machine

| State | Meaning | Allowed Transitions | Illegal Transitions | Trigger | Preconditions | Business Impact | Audit / Idempotency / Concurrency |
|---|---|---|---|---|---|---|---|
| waiting | Waiting in queue. | called, cancelled, expired | skipped, seated without call or assignment policy | staff, system, customer cancellation | Queue group exists; ticket active. | Eligible for calling. | Audit create and reorder; queue number idempotency. |
| called | Number has been called. | seated, skipped, cancelled, expired | rejoined directly | staff or system call | Ticket waiting; call channel available. | Starts call-hold timer. | Audit call; idempotent call key. |
| skipped | Customer missed call. | rejoined, cancelled, expired | seated without rejoin or override | staff or system timeout | call-hold duration elapsed. | Customer loses current active call. | Audit skip reason. |
| rejoined | Skipped customer rejoined. | waiting, called, cancelled, expired | new number by default | staff or customer policy | Original ticket skipped and still valid. | Keeps original number but does not cut the queue by default. | Audit rejoin and placement. |
| seated | Customer seated. | none | any active queue state | staff Seating | Valid Seating and resource lock. | Terminal success state. | Audit; prevent duplicate seating. |
| cancelled | Queue cancelled. | none | any active queue state | staff or customer | Cancellation reason captured. | Terminal cancellation. | Audit reason. |
| expired | Queue expired. | none | any active queue state | system policy | Expiry condition reached. | Terminal timeout. | Audit source and rule. |

### Table State Machine

| State | Meaning | Allowed Transitions | Illegal Transitions | Trigger | Preconditions | Business Impact | Audit / Idempotency / Concurrency |
|---|---|---|---|---|---|---|---|
| available | Ready for use. | locked, reserved, inactive | occupied without lock/assignment policy | staff, system, reservation policy | Table active and clean. | Can be recommended. | Audit operational assignment; lock atomically. |
| locked | Temporarily protected. | available, reserved, occupied | cleaning, inactive without release policy | staff or system assignment flow | Lock owner and expiry exist. | Prevents duplicate assignment during decision. | Lock expires; idempotent lock key; concurrency protected. |
| reserved | Reserved/pre-assigned but not occupied. | available, locked, occupied, inactive by policy | cleaning | reservation policy, staff | Reservation capacity or pre-assignment valid. | Protects future use where configured. | Audit pre-assignment and release. |
| occupied | Active seating. | cleaning | available directly, reserved, inactive without completion | Seating | Valid Seating active. | Table is unavailable to other flows. | Audit; prevent duplicate occupancy. |
| cleaning | Cleaning in progress. | available, inactive | occupied without new Seating, reserved | staff cleaning flow | Seating completed or guest left. | Table remains unavailable. | Audit start/finish; idempotent completion. |
| inactive | Not usable. | available by reactivation policy | locked, reserved, occupied | staff configuration | No active occupancy or blocking flow. | Removed from assignment and recommendation. | Audit configuration change. |

## Risk Control Rules

- Reservation hold duration must be governed by Store policy. V1 default is 15 minutes after reservation time.
- Call hold duration must be governed by Store policy. V1 default is 3 minutes after call.
- Expected dining duration must be governed by Store policy. V1 default is 90 minutes.
- Table locks must have owner, scope, expiry, and release path.
- Idempotency key is required for external calls and repeatable critical actions.
- Concurrency control is required for seating, table lock, queue call, rejoin, temporary TableGroup, and cleaning release.
- Cancellation, no_show, and manual override must capture reasons.
- Third-party calls must be idempotent and auditable.
- Webhook retry rules are a future integration boundary and must not be implemented in this round.

## OOD and Reuse Governance

Future development must prefer object-oriented domain design around business objects, state machines, rules, and reusable policies.

Rules that must become reusable Rule / Policy / Validator candidates in later technical design:

- Reservation availability.
- Queue calling and rejoin.
- Table assignment.
- Table lock.
- TableGroup validation.
- Customer identity.
- Store locale.
- Tenant and Store scope.
- Idempotency.
- Audit.

Forbidden future patterns:

- One service handling reservation, queue, seating, cleaning, and turnover together.
- Controllers or pages containing business state machines.
- Repeating tenant_id or store_id checks in every flow.
- Repeating time conversion, table assignment, audit, and status transition logic across modules.

## Open Questions

| ID | Question | Why It Matters | Status |
|---|---|---|---|
| OQ-001 | Reservation hold duration per Store. | Store configurable; V1 default is 15 minutes after reservation time. | Confirmed |
| OQ-002 | Queue call-hold duration. | Store configurable; V1 default is 3 minutes after call. | Confirmed |
| OQ-003 | Expected dining duration. | Store configurable; V1 default is 90 minutes. Future extension may vary by business type, party size, table type, and time slot. | Confirmed |
| OQ-004 | Same-customer duplicate Reservation policy. | Same Tenant + Store + Customer + time slot cannot have multiple active confirmed / arrived / seated Reservations. | Confirmed |
| OQ-005 | V1 QueueGroup definition. | Store + Party Size Group by default: 1-2, 3-4, 5-6, and 7+. | Confirmed |

## Open Conflicts

| ID | Conflict | Impact | Status |
|---|---|---|---|
| OC-001 | The attachment version lists five governance tasks and omits OOD & Reuse Governance; the local bootstrap file in the workspace includes OOD & Reuse Governance as an additional governance task. | No change to allowed files and no entry into implementation, but later planning should confirm whether OOD governance remains mandatory. | Open Conflict |
