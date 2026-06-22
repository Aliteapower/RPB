# Reservation API Contract Checklist V1

## Purpose

This checklist verifies that the Reservation Create API Contract Design round stays inside its approved scope.

This is documentation only. It does not implement Controller, REST endpoint, API DTO Java class, Vue UI, migration, SQL, test code, seed data, or runtime mock data.

## Scope Checklist

- [x] Only Create Reservation API is designed.
- [x] Endpoint is limited to `POST /api/v1/stores/{storeId}/reservations`.
- [x] Permission key is `reservation.create`.
- [x] Request creates a confirmed Reservation only.
- [x] Response exposes API DTO shape only.
- [x] Response does not expose Domain Object, Entity, Repository, Mapper, or persistence internals.

## Non-Scope Checklist

- [x] CheckIn API designed: No.
- [x] Queue API designed: No.
- [x] Seating API designed: No.
- [x] No-show API designed: No.
- [x] Cancellation API designed: No.
- [x] Table assignment API designed: No.
- [x] Reservation list/search API designed: No.
- [x] Reservation calendar API designed: No.
- [x] Reservation UI designed: No.
- [x] Queue UI designed: No.
- [x] Table selector designed: No.

## Implementation Boundary Checklist

- [x] Controller created: No.
- [x] REST endpoint implemented: No.
- [x] API DTO Java class created: No.
- [x] Request / Response Java class created: No.
- [x] Application Service changed: No.
- [x] Repository changed: No.
- [x] Mapper changed: No.
- [x] Entity changed: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Migration changed: No.
- [x] SQL file created: No.
- [x] Seed data inserted: No.
- [x] Production config changed: No.

## Request Contract Checklist

- [x] `Idempotency-Key` header required.
- [x] `storeId` comes from path.
- [x] `tenantId` is not accepted from request body as trusted source.
- [x] actor identity comes from JWT/server context.
- [x] `partySize` is required and must be > 0.
- [x] `reservedStartAt` is required and ISO8601.
- [x] `reservedEndAt` is optional and ISO8601 if present.
- [x] If `reservedEndAt` is present, it must be after `reservedStartAt`.
- [x] If `reservedEndAt` is missing, backend derives it from `StorePolicy.expectedDiningMinutes`.
- [x] `customerId` is optional.
- [x] `customerName` is optional.
- [x] `customerNickname` is optional.
- [x] `phoneE164` is optional and must be E.164 if present.
- [x] `note` is optional.
- [x] Request body excludes `queueTicketId`.
- [x] Request body excludes `seatingId`.
- [x] Request body excludes `tableId`.
- [x] Request body excludes `tableGroupId`.
- [x] Request body excludes `checkInAt`.
- [x] Request body excludes `noShowAt`.
- [x] Request body excludes `cancelledAt`.

## Response Contract Checklist

- [x] Success returns `201 Created`.
- [x] Completed replay returns `200 OK`.
- [x] Response includes `reservationId`.
- [x] Response includes `reservationCode`.
- [x] Response includes `status = confirmed`.
- [x] Response includes `partySize`.
- [x] Response includes final `reservedStartAt`.
- [x] Response includes final `reservedEndAt`.
- [x] Response includes `holdUntilAt`.
- [x] Response includes `businessDate`.
- [x] Response includes a safe customer projection.
- [x] Response includes short event codes.
- [x] Response includes idempotency status and replay flag.
- [x] Response does not expose full AuditLog metadata.
- [x] Response does not expose internal capacity calculation details.

## Error / I18n Checklist

- [x] Uses common error envelope.
- [x] Uses stable public error codes.
- [x] Uses i18n `messageKey`.
- [x] Does not hardcode display text.
- [x] Uses safe `details` object.
- [x] Maps validation errors to `400 Bad Request`.
- [x] Maps authorization/scope errors to `401` / `403`.
- [x] Maps Store / Customer not found to `404`.
- [x] Maps duplicate/capacity/code/idempotency conflicts to `409`.
- [x] Maps persistence/audit/event/transition failures to `500`.

## Required Error Code Checklist

- [x] `STORE_NOT_FOUND`
- [x] `STORE_SCOPE_MISMATCH`
- [x] `FORBIDDEN`
- [x] `MISSING_IDEMPOTENCY_KEY`
- [x] `IDEMPOTENCY_CONFLICT`
- [x] `IDEMPOTENCY_IN_PROGRESS`
- [x] `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- [x] `INVALID_PARTY_SIZE`
- [x] `INVALID_TIME_RANGE`
- [x] `RESERVATION_START_IN_PAST`
- [x] `INVALID_PHONE_E164`
- [x] `CUSTOMER_NOT_FOUND`
- [x] `INVALID_CUSTOMER_IDENTITY`
- [x] `RESERVATION_DUPLICATE_ACTIVE`
- [x] `RESERVATION_CAPACITY_INSUFFICIENT`
- [x] `RESERVATION_CODE_CONFLICT`
- [x] `RESERVATION_POLICY_NOT_FOUND`
- [x] `AUDIT_WRITE_FAILED`
- [x] `EVENT_WRITE_FAILED`
- [x] `STATE_TRANSITION_WRITE_FAILED`
- [x] `PERSISTENCE_ERROR`

## Idempotency Checklist

- [x] `Idempotency-Key` header is required.
- [x] Action is `create_reservation`.
- [x] Store-scoped idempotency identity is clear.
- [x] Request hash rule is clear.
- [x] Completed same hash replays previous response with `replayed=true`.
- [x] In-progress same hash returns `IDEMPOTENCY_IN_PROGRESS`.
- [x] Failed same hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- [x] Same key with different hash returns `IDEMPOTENCY_CONFLICT`.
- [x] Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- [x] Replay must not create duplicate Reservation.

## Auth / RBAC / StoreScope Checklist

- [x] Authentication boundary is defined.
- [x] Tenant scope comes from JWT/server context.
- [x] Store scope comes from path and must be validated.
- [x] Allowed roles are `tenant_admin`, `store_manager`, and `store_staff`.
- [x] Forbidden roles are `customer` and `integration_app`.
- [x] Permission key is `reservation.create`.
- [x] Cross-Tenant and cross-Store references are rejected.

## Reservation Business Boundary Checklist

- [x] Reservation does not automatically generate QueueTicket.
- [x] Reservation does not create Seating.
- [x] Reservation does not create CheckIn.
- [x] Reservation does not create No-show.
- [x] Reservation does not cancel itself in this API.
- [x] Reservation does not assign a Table.
- [x] Reservation does not assign a TableGroup.
- [x] Reservation does not create TableLock.
- [x] Reservation does not create ReservationPreassignment.
- [x] Reservation locks Store + business date + time range + party-size capacity by default.
- [x] Customer phone remains nullable.
- [x] Customer uniqueness remains Tenant-scoped.
- [x] Time exchange uses ISO8601.
- [x] Display localization remains Store locale responsibility.

## Test Contract Checklist

- [x] Success with existing Customer is covered.
- [x] Success with phone Customer is covered.
- [x] Success with no-phone temporary Customer is covered.
- [x] Omitted `reservedEndAt` derivation is covered.
- [x] Returned `holdUntilAt` is covered.
- [x] Returned `reservationCode` is covered.
- [x] Completed idempotency replay is covered.
- [x] Missing idempotency key is covered.
- [x] Invalid party size is covered.
- [x] Invalid time range is covered.
- [x] Start in past is covered.
- [x] Invalid phone is covered.
- [x] Customer not found is covered.
- [x] Duplicate active Reservation is covered.
- [x] Capacity insufficient is covered.
- [x] Reservation code conflict is covered.
- [x] Forbidden role is covered.
- [x] Store scope mismatch is covered.
- [x] Boundary checks are covered.

## Next Gate

Recommended next gate:

```text
Reservation Create API Implementation
```

The implementation round must still not implement CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, Reservation UI, Reservation list/search, Reservation calendar, or migration changes.
