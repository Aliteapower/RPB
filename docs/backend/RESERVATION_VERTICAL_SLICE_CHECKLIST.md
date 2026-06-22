# Reservation Vertical Slice Checklist V1

## 1. Slice Selection

- [x] Only Create Reservation is selected.
- [x] Create Reservation creates a future capacity intent.
- [x] Create Reservation sets Reservation status to `confirmed`.
- [x] Create Reservation writes event, transition, audit, and idempotency boundaries.
- [x] Create Reservation does not continue into arrival, waiting, seating, cleaning, or turnover.

## 2. Scope Boundary

- [x] CheckIn designed: No.
- [x] Queue designed: No.
- [x] Seating designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] Reservation update designed: No.
- [x] Reservation list/calendar designed: No.
- [x] Table assignment designed: No.
- [x] ReservationPreassignment workflow designed: No.
- [x] API implementation designed: No.
- [x] UI implementation designed: No.

## 3. Business Boundary

- [x] Reservation remains separate from QueueTicket.
- [x] Reservation does not automatically generate QueueTicket.
- [x] Reservation remains separate from Seating.
- [x] CheckIn remains a future business event, not part of Create Reservation.
- [x] Reservation does not create TableLock by default.
- [x] Reservation does not lock a specific DiningTable by default.
- [x] Reservation does not lock a specific TableGroup by default.
- [x] Reservation locks Store + date + time range + party-size capacity by default.

## 4. Customer Boundary

- [x] Customer uniqueness remains Tenant-scoped.
- [x] Customer phone remains nullable.
- [x] E.164 validation applies only when `phoneE164` is present.
- [x] Existing Customer is supported.
- [x] Temporary/no-phone Customer is supported.
- [x] Anonymous or minimal customer identity remains a Customer boundary, not Member.
- [x] Member, loyalty, marketing, and payment are out of scope.

## 5. Time and Locale Boundary

- [x] Command time input uses ISO8601.
- [x] Stored instants remain UTC / `timestamptz`.
- [x] Store timezone derives `businessDate`.
- [x] Store decides timezone, locale, date format, time format, and currency.
- [x] Singapore display defaults remain Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD.
- [x] UI formatting is not implemented in this round.

## 6. Command Boundary

- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `partySize`.
- [x] Command includes `reservedStartAt`.
- [x] Command includes `reservedEndAt`.
- [x] Command includes optional `customerId`.
- [x] Command includes optional `customerName`.
- [x] Command includes optional `customerNickname`.
- [x] Command includes optional `phoneE164`.
- [x] Command includes optional `note`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command excludes `queueTicketId`.
- [x] Command excludes `seatingId`.
- [x] Command excludes `tableId`.
- [x] Command excludes `tableGroupId`.
- [x] Command excludes `checkInAt`.
- [x] Command excludes `noShowAt`.

## 7. Application Service Boundary

- [x] Service boundary is `ReservationCreateApplicationService`.
- [x] Service validates command.
- [x] Service builds StoreScope.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service resolves Customer identity.
- [x] Service validates party size.
- [x] Service validates time range.
- [x] Service applies StorePolicy.
- [x] Service checks duplicate reservation.
- [x] Service checks capacity availability.
- [x] Service creates Reservation.
- [x] Service sets status `confirmed`.
- [x] Service writes BusinessEvent.
- [x] Service writes StateTransitionLog.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service does not parse API requests.
- [x] Service does not choose a table.
- [x] Service does not implement SQL.

## 8. Repository Port Boundary

- [x] StoreRepositoryPort is required.
- [x] StorePolicyRepositoryPort is required.
- [x] CustomerRepositoryPort is required.
- [x] ReservationRepositoryPort is required.
- [x] BusinessEventRepositoryPort is required.
- [x] StateTransitionLogRepositoryPort is required.
- [x] AuditLogRepositoryPort is required.
- [x] IdempotencyRepositoryPort is required.
- [x] QueueTicketRepositoryPort is not required.
- [x] SeatingRepositoryPort is not required.
- [x] TableLockRepositoryPort is not required.
- [x] CleaningRepositoryPort is not required.
- [x] TurnoverRepositoryPort is not required.
- [x] Ports are scoped and not mechanical CRUD.
- [x] Ports do not return Persistence Entity.

## 9. Rule / Policy / Validator Boundary

- [x] StoreAccessPolicy is required.
- [x] CustomerIdentityRule is required.
- [x] CustomerPhoneRule is required.
- [x] ReservationAvailabilityRule is required.
- [x] ReservationDuplicateRule is required.
- [x] ReservationHoldPolicy is required.
- [x] ReservationTimeRangeRule is required.
- [x] ReservationCodePolicy is required.
- [x] StoreTimeZoneRule is required.
- [x] AuditRule is required.
- [x] BusinessEventRule is required.
- [x] StateTransitionRule is required.
- [x] IdempotencyRule is required.
- [x] QueueCallingRule is not designed.
- [x] QueueRejoinRule is not designed.
- [x] TableAssignmentRule is not designed.
- [x] SeatingSourceValidator is not designed.
- [x] CleaningResourceValidator is not designed.
- [x] NoShowPolicy is not designed.

## 10. State / Event / Audit Boundary

- [x] Reservation create result status is `confirmed`.
- [x] State transition is `none -> confirmed` or internal `draft -> confirmed`.
- [x] Application result exposes `confirmed`.
- [x] Events include `reservation.created` and `reservation.confirmed`, or a documented single atomic event.
- [x] StateTransitionLog records Reservation transition.
- [x] AuditLog records `reservation.create`.
- [x] Audit metadata includes actor, scope, target, idempotency key, party size, time range, and customer context.

## 11. Idempotency Boundary

- [x] Action is `create_reservation`.
- [x] Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- [x] Completed same hash replays result.
- [x] In-progress same hash returns retry-later/conflict.
- [x] Failed same hash requires a new key.
- [x] Same key different hash returns conflict.
- [x] Same command retry must not create duplicate Reservation.

## 12. Failure Coverage

- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Invalid party size covered.
- [x] Invalid time range covered.
- [x] End before or equal start covered.
- [x] Start in the past covered.
- [x] Customer not found covered.
- [x] Invalid E.164 phone covered.
- [x] Duplicate active reservation covered.
- [x] Capacity unavailable covered.
- [x] Store policy missing covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed key requires new key covered.
- [x] BusinessEvent write failure covered.
- [x] Audit write failure covered.
- [x] StateTransitionLog write failure covered.
- [x] Persistence failure covered.

## 13. Test Contract Boundary

- [x] Success with existing Customer covered.
- [x] Success with no-phone temporary Customer covered.
- [x] Success with phone Customer covered.
- [x] UTC storage covered.
- [x] Store-local business date derivation covered.
- [x] Confirmed status covered.
- [x] Event writing covered.
- [x] StateTransitionLog writing covered.
- [x] AuditLog writing covered.
- [x] Idempotency completed covered.
- [x] Idempotency replay covered.
- [x] Idempotency in-progress covered.
- [x] Idempotency failed-key covered.
- [x] Idempotency hash conflict covered.
- [x] Boundary assertions cover no QueueTicket, Seating, TableLock, CheckIn, No-show, API, UI, or migration.

## 14. Implementation Boundary

- [x] Java Application Service created: No.
- [x] Repository implementation created: No.
- [x] Controller created: No.
- [x] REST API implemented: No.
- [x] API DTO created: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Migration changed: No.
- [x] SQL file created: No.
- [x] Database touched: No.
- [x] Seed data inserted: No.
- [x] Mock runtime data inserted: No.
- [x] Production config changed: No.

## 15. Next Gate

Recommended next gate:

```text
Reservation Create Persistence Contract / Implementation
```

Do not enter Reservation API or UI until application and persistence boundaries for this slice are implemented and verified.
