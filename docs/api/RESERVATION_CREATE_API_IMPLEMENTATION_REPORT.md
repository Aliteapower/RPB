# Reservation Create API Implementation Report

## 1. Read Documents

- docs/api/RESERVATION_CREATE_API_CONTRACT.md
- docs/api/RESERVATION_API_ERROR_CONTRACT.md
- docs/api/RESERVATION_API_IDEMPOTENCY_CONTRACT.md
- docs/api/RESERVATION_API_CONTRACT_CHECKLIST.md
- docs/backend/RESERVATION_CREATE_APPLICATION_IMPLEMENTATION_REPORT.md
- docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md
- docs/backend/RESERVATION_CREATE_APPLICATION_CONTRACT.md
- docs/backend/RESERVATION_VERTICAL_SLICE_CHECKLIST.md
- docs/governance/BUSINESS_RULES.md
- docs/governance/DATA_STANDARD.md
- docs/architecture/ARCHITECTURE.md
- docs/skills/reservation-system/SKILL.md
- docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md
- docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md

## 2. Created Controller / API DTO / Mapper

Created Reservation Create API files under src/main/java/com/rpb/reservation/reservation/api:

- ReservationController
- CreateReservationRequest
- CreateReservationResponse
- ReservationApiMapper
- ReservationApiErrorMapper
- ReservationApiErrorCode
- ReservationApiErrorResponse

Created API contract tests under src/test/java/com/rpb/reservation/reservation/api:

- ReservationControllerTest

Updated existing boundary tests to allow the approved Reservation Create API while continuing to reject unapproved vertical slices:

- WalkInDirectSeatingControllerTest
- CleaningControllerTest
- CleaningCompleteApiIntegrationTest

## 3. Endpoint Implemented

- Method: POST
- Path: /api/v1/stores/{storeId}/reservations
- Permission: reservation.create

The endpoint calls ReservationCreateApplicationService only. It does not implement CheckIn, Queue, Seating, No-show, Cancellation, or table assignment behavior.

## 4. Auth / Scope Approach

The endpoint reuses the existing CurrentActorProvider boundary.

- tenantId comes from the server-side actor context.
- storeId comes from the path.
- store scope is validated with actor.canAccessStore(storeId).
- Allowed roles: tenant_admin, store_manager, store_staff.
- Required permission: reservation.create.
- Forbidden roles or missing permission return FORBIDDEN.
- Store scope mismatch returns STORE_SCOPE_MISMATCH.

This remains a minimal backend security boundary. A full production JWT/login system was not implemented in this round.

## 5. Request Validation

Implemented CreateReservationRequest fields:

- partySize
- reservedStartAt
- reservedEndAt
- customerId
- customerName
- customerNickname
- phoneE164
- note

Validation:

- Idempotency-Key header is required.
- partySize is required and must be greater than 0.
- reservedStartAt is required.
- reservedEndAt is optional for API input, but when present must be after reservedStartAt.
- phoneE164 is optional, but when present must match E.164.
- tenantId is not accepted from the request body.
- queueTicketId, seatingId, tableId, tableGroupId, checkInAt, noShowAt, and cancellation fields are not accepted.

## 6. Response Mapping

Implemented CreateReservationResponse as an API DTO:

- success
- reservationId
- reservationCode
- status
- partySize
- reservedStartAt
- reservedEndAt
- holdUntilAt
- businessDate
- customer
- events
- idempotency

The API response does not expose JPA entities, repository details, mapper internals, or full audit metadata.

Events returned:

- reservation.created
- reservation.confirmed

Customer response is a safe projection only. customer.displayName is mapped from request hints and is not a persisted Customer domain field in V1.

## 7. Error Mapping

Implemented stable API error envelope:

- success=false
- error.code
- error.messageKey
- error.details
- idempotency.status

Implemented Reservation Create error codes:

- STORE_NOT_FOUND
- STORE_SCOPE_MISMATCH
- FORBIDDEN
- MISSING_IDEMPOTENCY_KEY
- IDEMPOTENCY_CONFLICT
- IDEMPOTENCY_IN_PROGRESS
- IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY
- INVALID_PARTY_SIZE
- INVALID_TIME_RANGE
- RESERVATION_START_IN_PAST
- INVALID_PHONE_E164
- CUSTOMER_NOT_FOUND
- INVALID_CUSTOMER_IDENTITY
- RESERVATION_DUPLICATE_ACTIVE
- RESERVATION_CAPACITY_INSUFFICIENT
- RESERVATION_CODE_CONFLICT
- RESERVATION_POLICY_NOT_FOUND
- ILLEGAL_STATE_TRANSITION
- AUDIT_WRITE_FAILED
- EVENT_WRITE_FAILED
- STATE_TRANSITION_WRITE_FAILED
- PERSISTENCE_ERROR

HTTP status mapping follows the API contract:

- Success: 201 Created
- Completed replay: 200 OK
- Validation errors: 400 Bad Request
- Forbidden or scope mismatch: 403 Forbidden
- Store not found / customer not found: 404 Not Found
- Duplicate, capacity conflict, idempotency conflict, in-progress, failed-key reuse, code conflict, illegal state: 409 Conflict
- Persistence, audit, event, and state transition failures: 500 Internal Server Error

## 8. Idempotency Header Behavior

Implemented API-level Idempotency-Key handling:

- Missing Idempotency-Key returns MISSING_IDEMPOTENCY_KEY.
- Header value is mapped into CreateReservationCommand.
- Application replay result maps to 200 OK with idempotency.replayed=true.
- Fresh success maps to 201 Created with idempotency.replayed=false.
- Idempotency conflicts and failed-key reuse map to stable API errors.

The API layer does not create duplicate reservations for replayed commands; replay behavior is delegated to the application service and idempotency port.

## 9. Replay Response Behavior

Completed replay returns the previous application result as an API DTO:

- HTTP 200 OK
- idempotency.status=completed
- idempotency.replayed=true

Fresh creation returns:

- HTTP 201 Created
- idempotency.status=completed
- idempotency.replayed=false

## 10. Tests Executed

TDD red:

- mvn -q '-Dtest=ReservationControllerTest' test failed before implementation because Reservation API classes did not exist.

Targeted green:

- mvn -q '-Dtest=ReservationControllerTest' test
- Result: Passed

Compatibility boundary check:

- mvn -q '-Dtest=CleaningCompleteApiIntegrationTest#boundaryArtifactsRemainLimitedToWalkInAndCleaning' test
- Result: Passed

Full regression:

- mvn test
- Result: Passed, 207 tests, 0 failures, 0 errors, 0 skipped

## 11. Boundary Check

CheckIn API implemented: No

Queue API implemented: No

Seating API implemented: No

No-show API implemented: No

Cancellation API implemented: No

Table assignment API implemented: No

Reservation UI implemented: No

Migration changed: No

Production database touched: No

Seed data inserted: No

Vue UI changed: No

SQL created: No

## 12. Open Questions

- When production authentication is introduced, should Reservation Create reuse the same actor claims as WalkIn and Cleaning or move to a shared reservation-specific permission evaluator?
- Should Reservation Create integration validation be DB-backed in the next round, mirroring WalkIn and Cleaning API integration validation?

## 13. Open Conflicts

None.

## 14. Next Step Recommendation

Proceed to Reservation Create API Integration Validation. The next round should validate:

- API to controller to application service to persistence adapter to PostgreSQL schema.
- Reservation status confirmed.
- Reservation events, state transition, audit log, and idempotency record.
- No QueueTicket, Seating, TableLock, CheckIn, No-show, Cancellation, or UI side effects.
