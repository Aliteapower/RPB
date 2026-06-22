# Architecture

## System Positioning

The platform is a multi-tenant Reservation + Queue + Table Management + Seating Flow system for restaurants, cafes, tea houses, hotpot stores, chain dining, and other store scenarios that require reservation, queueing, table assignment, calling, check-in, seating, cleaning, and turnover.

This architecture document defines boundaries only. It does not define database fields, DDL, migrations, API paths, controller design, service design, repository design, UI component structure, or implementation code.

## Fixed Business Hierarchy

```text
Platform
  -> Tenant
      -> Store
          -> Area
              -> Table
```

Core operational loop:

```text
Reservation / WalkIn
  -> CheckIn when applicable
  -> QueueTicket when waiting is needed
  -> Seating
  -> Cleaning
  -> Turnover
```

## Technical Baseline

Frontend:

```text
Vue 3
TypeScript
Vite
Pinia
Vue Router
```

Backend:

```text
Java 21
Spring Boot 3
Spring Security
Validation
Flyway
```

Database:

```text
PostgreSQL
```

Cache:

```text
Redis
```

API:

```text
REST API
OpenAPI
Webhook
```

Auth:

```text
JWT
RBAC
```

## Module Boundaries

| Module | Owns | Upstream | Downstream | Boundary |
|---|---|---|---|---|
| Identity & RBAC | Users, roles, permission boundary, JWT/RBAC policy. | Platform, Tenant, Store context. | All protected modules. | Does not own reservation or queue business rules. |
| Tenant Management | Tenant lifecycle, tenant configuration, tenant-level rules. | Platform. | Store Management, Customer Management, Identity. | Does not operate physical tables. |
| Store Management | Store profile, locale, timezone, business hours, currency. | Tenant Management. | Area & Table, Reservation, Queue, Seating. | Does not own cross-tenant identity. |
| Area & Table Management | Areas, table resources, table statuses, fixed and temporary TableGroup governance. | Store Management. | Reservation, Queue, Seating, Cleaning. | Does not own Customer identity or queue ordering. |
| Reservation Management | Reservation lifecycle, capacity intent, arrival path. | Store, Customer. | CheckIn, Queue when needed, Seating. | Does not own queue number generation or table occupancy by itself. |
| Queue Management | QueueTicket lifecycle, queue groups, calling, skipped, rejoin, cancellation, expiry. | Store, Reservation arrival, WalkIn. | Seating. | Does not own advance reservation capacity. |
| CheckIn Management | Arrival confirmation event and Reservation confirmed -> arrived transition. | Reservation. | Seating or Queue decision. | Not a primary entity in V1 and does not create occupancy. |
| Seating Management | Table/TableGroup assignment, occupancy creation, manual override boundary. | Reservation, QueueTicket, WalkIn, Table resources. | Cleaning, Turnover. | Does not create Reservations or QueueTickets. |
| Cleaning & Turnover | Cleaning status flow and turnover result boundary. | Seating. | Table availability, dashboard boundary. | Does not implement payment, marketing, or BI. |
| Customer Management | Tenant-scoped customer identity, temporary and no-phone customer support. | Tenant. | Reservation, Queue, WalkIn, Seating. | Does not implement membership points or marketing. |
| Notification Boundary | Future outbound notifications for reservation, queue call, reminder, and webhook events. | Business event modules. | External channels. | Boundary only in V1 governance; no channel implementation. |
| Integration Boundary | Future integration contracts for POS, membership, mini program, WhatsApp, and third-party reservation platforms. | Core modules. | External systems. | No deep integration in V1. |
| Webhook Boundary | Future event delivery, retry, signing, and idempotency boundary. | Audit and event-producing modules. | External subscribers. | Boundary only; no concrete endpoint design in this round. |
| Audit Log | Critical action, state transition, integration call, and override audit. | All modules. | Compliance, operations, debugging. | Must not be optional for critical flows. |
| I18n & Locale | Store timezone, locale, date/time/currency display, i18n key boundary. | Store Management. | UI/API representation in later rounds. | Does not decide business status. |
| Operational Dashboard Boundary | Future operational visibility for queue, seating, cleaning, and turnover. | Core operational data. | Store managers. | V1 forbids complex BI. |

## Multi-Tenant Architecture Boundary

- Platform data is global and must not be forced into Tenant or Store scope.
- Tenant is the main data isolation boundary.
- Store is the operational boundary for Reservation, QueueTicket, Seating, Cleaning, Area, and Table.
- Cross-store Customer is allowed only inside the same Tenant.
- Cross-Tenant references are forbidden.
- Store operational resources cannot be shared across Stores unless a later confirmed rule explicitly allows a controlled cross-store scenario.
- Audit must preserve the scope where the action occurred.

## Internationalization Architecture Boundary

- Time storage uses UTC.
- System time exchange uses ISO8601.
- Display uses Store timezone, locale, date format, time format, and currency.
- Singapore default is Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD.
- Phone numbers follow E.164 when present.
- Phone number is optional for Customer.
- User-facing text must use i18n keys in later UI and API design.

## Auth and RBAC Boundary

Fixed auth baseline:

```text
JWT
RBAC
```

Fixed roles:

```text
platform_admin
tenant_admin
store_manager
store_staff
customer
integration_app
```

Role boundary:

- platform_admin can manage platform scope and tenant onboarding boundaries.
- tenant_admin can manage Tenant scope and Stores under the Tenant.
- store_manager can manage Store operations and Store configuration.
- store_staff can operate reservation, queue, seating, and cleaning within assigned Store scope.
- customer can access customer-facing reservation or queue interactions where later product design allows.
- integration_app can access explicitly granted integration boundaries.

## Redis Usage Boundary

Redis is fixed for:

- Table locks.
- Queue status.
- Idempotency control.
- Hot cache.
- Temporary state protection.
- Calling status synchronization.

This round does not define Redis key structure, expiry values, lock algorithms, or implementation code.

## PostgreSQL Usage Boundary

PostgreSQL is fixed as the primary database for later design.

PostgreSQL will be expected to preserve:

- Tenant and Store scope.
- Durable business records.
- State transition history where later design requires it.
- Audit history.
- Soft deletion and archival semantics.

This round does not create schema, DDL, migrations, entities, repositories, or fields.

## REST, OpenAPI, and Webhook Boundary

Fixed API baseline:

```text
REST API
OpenAPI
Webhook
```

Future open capability boundaries:

- Customer API boundary.
- Reservation API boundary.
- Queue API boundary.
- Table API boundary.
- Seating API boundary.
- Webhook boundary.

This round does not define concrete API paths, request/response schemas, controllers, services, or endpoint implementation.

## Third-Party Integration Boundary

The following systems are future integration boundaries and are not part of V1 development:

- POS
- Payment
- Membership
- Marketing
- WhatsApp
- WeChat Mini Program
- Google Maps
- Accounting
- Loyalty Points
- Delivery Platform

## State Machine Boundary

Reservation, QueueTicket, and Table status changes must be governed by explicit state machines.

Future implementation must centralize:

- Transition validation.
- Trigger actor validation.
- Preconditions.
- Business impact.
- Audit.
- Idempotency.
- Concurrency protection.

State transition logic must not be scattered across controllers, services, pages, or integration handlers.

## Concurrency and Lock Boundary

Concurrency-sensitive areas:

- Reservation capacity.
- Queue calling and rejoin.
- Table lock.
- Table occupancy.
- Temporary TableGroup creation and release.
- Cleaning completion.
- Idempotent third-party calls.
- Webhook retry in later integration design.

Future technical design may combine PostgreSQL transactions, Redis locks, optimistic versioning, and idempotency records. This governance round only defines the boundary.

## Audit Boundary

Audit is required for critical business state changes, manual overrides, configuration changes, permission changes, and third-party calls.

Audit must capture:

- Actor and role.
- Tenant and Store scope.
- Operation time.
- Source.
- Before and after state.
- Related business object.
- Idempotency key.
- Failure reason where applicable.

Audit must not be optional for reservation, queue, seating, cleaning, table release, override, or integration flows.

## OOD and Reuse Boundary

The project follows Object-Oriented Domain Design governance:

- Business objects exist before code.
- Future development must start from Platform, Tenant, Store, Area, Table, TableGroup, Customer, Reservation, QueueTicket, WalkIn, CheckIn, Seating, Cleaning, and Turnover.
- Rules, policies, validators, state machines, tenant scope checks, store scope checks, locale conversion, audit, idempotency, and table assignment must be reusable capabilities.
- Business rules must not be duplicated across controllers, services, pages, or integration handlers.

Reusable capability boundaries for later design:

- State machine transition policy.
- Tenant and Store scope guard.
- Store locale resolver.
- Timezone and date/time formatting.
- E.164 phone validation.
- Idempotency guard.
- Audit logger boundary.
- Soft-delete filter.
- Table lock policy.
- Queue ordering policy.
- Reservation availability rule.
- Table availability rule.
- TableGroup validity rule.

## Mobile-First Product Boundary

Store operations are mobile-first:

```text
3 seconds to create an order-like operational record
5 seconds to assign a table
1 second to find a customer
```

Store-side page boundary for later UI design:

```text
Home
Reservation
Queue number
List
Table
Detail
```

V1 forbids complex drag-and-drop floor plans.

## Not Doing in V1

V1 does not include:

- Complex drag-and-drop table map.
- Native app.
- AI recommendation.
- Payment.
- Marketing.
- Membership points.
- Accounting.
- Microservices.
- Kubernetes.
- Delivery.
- Inventory.
- Supply chain.
- Complex BI.
- Multi-channel ad campaign.
- Coupons.
- Prepaid card.
- Deep POS integration.

## Next Phase Recommendation

After governance review, the next allowed phase is Database Design. That phase must read and follow:

- docs/governance/BUSINESS_GLOSSARY.md
- docs/governance/BUSINESS_RULES.md
- docs/governance/DATA_STANDARD.md
- docs/governance/DATA_CHECKLIST.md
- docs/architecture/ARCHITECTURE.md
- docs/skills/reservation-system/SKILL_OVERVIEW.md
- docs/skills/reservation-system/SKILL.md
