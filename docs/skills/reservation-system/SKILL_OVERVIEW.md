# Reservation System Skill Overview

## Skill Name

reservation-system

## Why This Skill Exists

Reservation, queue, table management, seating, cleaning, and turnover are one business loop. Treating them as separate isolated skills would create duplicated rules, inconsistent state transitions, and unclear ownership.

This Skill exists to keep all reservation-platform work aligned with the same business vocabulary, governance rules, data standards, architecture boundaries, and OOD reuse principles.

## Problems It Solves

- Prevents Reservation and QueueTicket from being mixed.
- Keeps WalkIn separate from QueueTicket.
- Keeps CheckIn separate from Seating.
- Preserves Tenant and Store data boundaries.
- Protects table locks, occupancy, temporary TableGroup release, and state machines.
- Ensures internationalization is considered from day one.
- Prevents direct jumps into database, API, UI, or code without reading governance.
- Prevents skill explosion around one connected business loop.

## Problems It Does Not Solve

- POS implementation.
- Payment.
- Marketing.
- Membership points.
- Accounting.
- Inventory.
- Delivery.
- Supply chain.
- Invoice.
- Native app.
- Complex BI.
- AI recommendation.
- Concrete database schema.
- Concrete API paths.
- UI component design.
- Business implementation code.

## Core Business Loop

```text
Reservation / WalkIn
  -> CheckIn when applicable
  -> QueueTicket when waiting is needed
  -> Seating
  -> Cleaning
  -> Turnover
```

## Core Objects

- Platform
- Tenant
- Store
- Area
- Table
- TableGroup
- Customer
- Reservation
- QueueTicket
- WalkIn
- CheckIn
- Seating
- Cleaning
- Turnover

## Applicable Scenarios

Use this Skill when work mentions:

- Reservation or booking.
- Queue or queue ticket.
- Walk-in guest.
- Table management.
- Table assignment.
- Check-in.
- Seating.
- Cleaning.
- Turnover.
- TableGroup, joined tables, combined tables, or 拼桌.
- Store operations around arrival, waiting, seating, or table release.
- Future database, API, UI, or implementation work for the reservation-platform loop.

## Not Applicable Scenarios

Do not use this Skill as the owner for:

- Payment.
- POS deep integration.
- Marketing campaign.
- Membership points.
- Accounting.
- Inventory.
- Delivery.
- Supply chain.
- Ad campaign.
- Coupons.
- Native app shell.
- Kubernetes or microservice platform work.

These can be integration or future boundaries, but not core V1 scope.

## Boundaries With Other Systems

Future integration boundaries:

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

Open capability boundaries:

- Customer API
- Reservation API
- Queue API
- Table API
- Seating API
- Webhook

This overview does not define concrete API paths, database fields, or code structure.

## Anti-Explosion Principle

Do not create these separate Skills:

- reservation-api-skill
- reservation-backend-skill
- reservation-frontend-skill
- queue-skill
- mobile-skill
- table-skill
- seating-skill

All related work must enter through:

```text
reservation-system
```

Reason:

Reservation, Queue, Table, Seating, Cleaning, and Turnover belong to the same business loop and must share vocabulary, state machines, audit rules, tenant boundaries, and reusable rule/policy/validator capabilities.
