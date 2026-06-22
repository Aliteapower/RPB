# Business Glossary

## Purpose

This document defines the shared business language for the Reservation Platform V1 governance round.

Scope status:

- Rule source: Product Owner startup task.
- Rule status for stated requirements: Confirmed.
- This document does not define database tables, API paths, UI pages, migrations, or implementation code.

## Business Object Hierarchy

```text
Platform
  -> Tenant
      -> Store
          -> Area
              -> Table
```

Operational flow:

```text
Reservation / WalkIn
  -> CheckIn when applicable
  -> QueueTicket when no table resource is available
  -> Seating
  -> Cleaning
  -> Turnover
```

## Core Business Objects

| Object | Definition | Responsibilities | Level and Ownership | Upstream / Downstream | Lifecycle | Boundaries and Non-Responsibilities | Key Constraints |
|---|---|---|---|---|---|---|---|
| Platform | The platform-level system owner and operating boundary. | Tenant management, platform configuration, platform roles, platform audit, open capability boundary, industry templates. | Platform level. No tenant_id or store_id ownership by default. | Upstream: none. Downstream: Tenant. | planned -> active -> suspended -> retired. | Does not operate a store's reservation, queue, seating, cleaning, or turnover flow. | Platform data must not be mixed with tenant or store operational data. |
| Tenant | A brand, company, or operating entity using the platform. | Brand configuration, multi-store management, tenant users, tenant permissions, tenant business rules, cross-store customer profile boundary. | Tenant level. tenant_id is the main data isolation boundary. | Upstream: Platform. Downstream: Store, Customer, tenant rules. | created -> active -> suspended -> closed. | Does not represent one physical store; does not directly own table resources. | Cross-tenant customer identity sharing is forbidden. |
| Store | A physical or operational branch under one Tenant. | Reservation operations, queue operations, table operations, area management, business hours, timezone, locale, date format, currency configuration. | Store level. tenant_id and store_id are required for store operational data. | Upstream: Tenant. Downstream: Area, Table, Reservation, QueueTicket, Seating, Cleaning. | created -> active -> inactive -> archived. | Does not own platform roles or cross-tenant identity. | Most operational business data must resolve to one Store. |
| Area | A zone inside a Store, such as hall, private room, outdoor, bar, second floor, or VIP area. | Table zoning, table assignment priority, operational grouping. | Store level. Belongs to one Store and one Tenant through Store. | Upstream: Store. Downstream: Table. | created -> active -> inactive -> archived. | Does not hold customer identity, queue numbers, or reservation capacity alone. | Area must not reference a Store from another Tenant. |
| Table | A physical table resource in a Store and Area. | Capacity, status, reservation use, queue use, WalkIn use, seating, cleaning, turnover support, fixed and temporary combination participation. | Store level resource. Belongs to one Store and one Area. | Upstream: Area. Downstream: Seating, Cleaning, TableGroup, Turnover calculation. | created -> available -> locked -> reserved -> occupied -> cleaning -> available; inactive may remove it from operations. | Not just static configuration; not responsible for customer identity or queue ordering. | One effective table resource cannot be occupied by multiple active flows at the same time. |
| TableGroup | A combined table resource. Fixed TableGroup is long-term configuration; Temporary TableGroup is a single-service resource. | Combine tables, validate compatible resources, protect combined occupancy, release temporary combinations. | Store level resource. All member Tables must belong to the same Store and Tenant. | Upstream: Table. Downstream: Seating, Cleaning, Turnover. | Fixed: created -> active -> inactive -> deleted. Temporary: created -> locked -> occupied -> released -> ended. | Does not replace the base Table definition; does not carry customer identity. | Must prevent circular references, duplicate occupancy, overlapping active groups, and unreleased temporary groups. |
| Customer | A guest identity within a Tenant. | Customer identification, cross-store customer profile boundary, anonymous and temporary customer support. | Tenant level by default; may be linked to Store operational records. | Upstream: Tenant. Downstream: Reservation, QueueTicket, WalkIn, Seating. | created -> active -> merged -> archived; anonymous or temporary customers may be short-lived. | Customer is not Member. Membership, loyalty points, and marketing are outside V1 scope. | Customer uniqueness scope is Tenant. Phone number is optional. No cross-tenant identity sharing. |
| Reservation | A booking for future capacity at a Store, date, time slot, and party size. | Advance resource intent, reservation status, arrival flow, optional table pre-assignment boundary. | Store operational data. tenant_id and store_id apply. | Upstream: Customer, Store. Downstream: CheckIn, optional QueueTicket, Seating, Turnover. | draft -> confirmed -> arrived -> seated -> completed; cancelled and no_show are terminal states. | Does not automatically create a QueueTicket; does not lock a specific Table by default. | V1 locks Store + date + time slot + party-size capacity by default. |
| QueueTicket | A queue number or waiting record after arrival when no resource is available. | Waiting, calling, skipped handling, rejoin handling, queue ordering, eventual seating or cancellation. | Store operational data. tenant_id and store_id apply. | Upstream: WalkIn or arrived Reservation. Downstream: Seating. | waiting -> called -> skipped -> rejoined -> waiting/called -> seated; cancelled and expired are terminal states. | QueueTicket is not Reservation and is not required for every WalkIn. | Queue number uniqueness is within the relevant queue group. Rejoined tickets keep the original number but do not cut the queue by default. |
| WalkIn | A customer who arrives without an advance Reservation. | Represent on-site arrival, support direct seating when a table is available, create QueueTicket only when waiting is needed. | Store operational scenario. tenant_id and store_id apply through Store. | Upstream: Customer or temporary guest context. Downstream: Seating or QueueTicket. | arrived -> seated; arrived -> queued -> seated; cancelled or abandoned when not served. | WalkIn is not QueueTicket; it can exist without a queue number. | If a table is available, WalkIn can be seated directly without taking a number. |
| CheckIn | Arrival confirmation event. | Confirm customer arrival, trigger Reservation status change, start table availability decision. | Store operational event. Related to tenant_id and store_id through Reservation or Store. | Upstream: Reservation. Downstream: arrived Reservation, Seating, or QueueTicket. | event recorded -> business state changed. | In V1 it is not a primary business entity and does not create table occupancy. | Reservation customer must CheckIn before entering Queue or Seating flow after arrival. |
| Seating | The formal seating event and table occupancy record. | Assign a customer flow to Table or TableGroup, create occupancy, update Reservation or QueueTicket state, affect locks, turnover statistics, and audit. | Store operational data. tenant_id and store_id apply. | Upstream: Reservation, QueueTicket, or WalkIn. Downstream: Cleaning, Turnover. | planned/locked -> occupied -> completed -> cleaning-triggered. | Does not create a Reservation or queue number. | Must prevent duplicate active occupancy of the same Table or TableGroup. |
| Cleaning | The table cleaning status flow after guests leave. | Move table resource from occupied toward available, protect resource while cleaning is unfinished. | Store operational process. tenant_id and store_id apply. | Upstream: Seating completion. Downstream: Table availability, Turnover result. | pending -> cleaning -> completed -> released. | Not a marketing, payment, or turnover metric by itself. | Table cannot become available until cleaning is completed. |
| Turnover | A business result, metric, or recorded outcome for one table-use cycle. | Measure table cycle from seating through completion and cleaning, support operational reporting. | Store operational result. tenant_id and store_id apply. | Upstream: Seating, completion event, Cleaning. Downstream: operational reporting. | derived/calculated -> recorded -> archived. | Does not perform seating or cleaning actions. | Turnover comes from Seating + Completed + Cleaning, not from Reservation alone. |

## Confirmed Concept Separations

| Do Not Mix | Confirmed Separation |
|---|---|
| Reservation != QueueTicket | Reservation is advance capacity intent; QueueTicket is waiting after arrival when no resource is available. |
| WalkIn != QueueTicket | WalkIn is an arrival scenario; QueueTicket is only needed when the WalkIn must wait. |
| CheckIn != Seating | CheckIn confirms arrival; Seating creates table occupancy. |
| Seating != Reservation | Seating assigns and occupies table resources; Reservation reserves future capacity. |
| Cleaning != Turnover | Cleaning is the resource status flow; Turnover is the resulting operational metric or event result. |
| Table != TableGroup | Table is a base resource; TableGroup is a fixed or temporary combination of tables. |
| Customer != Member | Customer is identity within Tenant; membership, points, and loyalty are future integration boundaries. |

## Examples

- A reservation for four people tomorrow at 19:00 creates a Reservation, not a QueueTicket.
- A reserved customer arriving at the Store first triggers CheckIn; only if no table is available does the flow create a QueueTicket.
- A WalkIn party can be seated directly when a suitable table is available.
- A temporary group of A3 + A4 for one large party is a Temporary TableGroup and must release both tables after the service ends.
- A no-phone guest can be tracked by temporary customer number, arrival time, party size, notes, table number, QueueTicket number, or Reservation code.

## Out-of-Scope Concepts for V1

The following are future boundaries and must not be treated as part of the V1 core object model:

- POS
- Payment
- Membership points
- Marketing
- Accounting
- Inventory
- Delivery
- Supply chain
- Invoice
- Complex BI
- Native app
- AI recommendation
