# Reservation Arrived To Queue API Implementation Report V1

## 1. Read Documents

- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_VALIDATION_REPORT.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/skills/reservation-system/SKILL.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

## 2. API Contract

Created `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`.

The implemented endpoint is:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
```

It maps an arrived Reservation into a waiting QueueTicket through the existing `ReservationArrivedToQueueApplicationService`.

## 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/reservation/api/QueueArrivedReservationRequest.java`
- `src/main/java/com/rpb/reservation/reservation/api/QueueArrivedReservationResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedToQueueApiMapper.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedToQueueApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueGroupMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/repository/QueueGroupJpaRepository.java`
- `src/main/java/com/rpb/reservation/queue/persistence/repository/QueueTicketJpaRepository.java`
- `src/main/java/com/rpb/reservation/queue/persistence/adapter/QueueGroupPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/queue/persistence/adapter/QueueTicketPersistenceAdapter.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedToQueueControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationArrivedToQueueApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationArrivedToQueueSecurityTest.java`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueGroupEntity.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueTicketEntity.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationCheckInControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationCheckInSecurityTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationArrivedDirectSeatingSecurityTest.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

## 4. Endpoint and App Gate

Implemented on existing `ReservationController`:

```java
@PostMapping("/reservations/{reservationId}/queue")
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

Added `reservation.queue` to `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.

## 5. Request / Response

Request DTO contains only:

- `partySizeGroup`
- `reasonCode`
- `note`

Success response contains:

- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = waiting`
- `queueGroupId`
- `queueGroupCode`
- `partySize`
- `partySizeGroup`
- `businessDate`
- `queuePosition`
- `alreadyQueued`
- `events`
- `idempotency`

## 6. Error Mapping

Added queue-specific API errors:

- `RESERVATION_CANNOT_QUEUE_SEATED`
- `RESERVATION_CANNOT_QUEUE_CANCELLED`
- `RESERVATION_CANNOT_QUEUE_NO_SHOW`
- `RESERVATION_CANNOT_QUEUE_COMPLETED`
- `QUEUE_GROUP_NOT_FOUND`
- `QUEUE_GROUP_CANNOT_BE_DERIVED`
- `QUEUE_GROUP_PARTY_SIZE_MISMATCH`
- `QUEUE_TICKET_NUMBER_CONFLICT`
- `ACTIVE_QUEUE_TICKET_CONFLICT`

The existing Reservation API error envelope still exposes `error.code`, `error.messageKey`, `error.details`, and `idempotency.status`.

## 7. Persistence

Connected the existing Queue ports to PostgreSQL without migration changes:

- `QueueGroupRepositoryPort` -> `queue_groups`
- `QueueTicketRepositoryPort` -> `queue_tickets`

The API integration test verifies that fresh success creates a `queue_tickets` row with `reservation_id`, `walk_in_id is null`, `status = waiting`, `ticket_number`, `queue_position`, and matching `queue_group_id`.

## 8. Idempotency

The endpoint requires `Idempotency-Key`.

Covered behavior:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success completes idempotency with action `queue_arrived_reservation`.
- Completed same-key replay returns `idempotency.replayed = true` and writes no duplicate QueueTicket/evidence.
- Same-hash `started` returns `IDEMPOTENCY_IN_PROGRESS`.
- Same-hash `failed` returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Different-hash `completed` returns `IDEMPOTENCY_CONFLICT`.

## 9. App Gate Tests

Coverage includes:

- Endpoint annotation uses `appKey = reservation_queue`.
- Endpoint annotation uses `permission = reservation.queue`.
- `/api/me/apps` app-entry metadata recognizes `reservation.queue`.
- Tenant entitlement denial returns `TENANT_APP_NOT_ENABLED` and audits.
- Store disabled denial returns `STORE_APP_NOT_ENABLED` and audits.
- Missing actor permission returns `PERMISSION_DENIED` and audits.
- App Gate denial writes no business data.

## 10. Local Runtime Security

Updated local runtime security to allow:

```http
POST /api/v1/stores/*/reservations/*/queue
```

`LocalRuntimeReservationArrivedToQueueSecurityTest` verifies the local profile reaches the controller with a configured local actor holding `reservation.queue`.

## 11. Commands

Initial TDD red command:

```text
mvn -q "-Dtest=ReservationArrivedToQueueControllerTest,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest,LocalRuntimeReservationArrivedToQueueSecurityTest" test
```

Initial result:

- Failed at test compilation because the queue API DTO/mapper/error mapper and `AppGateRequiredPermission.RESERVATION_QUEUE` did not exist yet.

Executed after implementation:

```text
mvn -q "-Dtest=ReservationArrivedToQueueControllerTest,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest,LocalRuntimeReservationArrivedToQueueSecurityTest" test
mvn -q "-Dtest=ReservationArrivedToQueueApiIntegrationTest" test
mvn -q "-Dtest=ReservationControllerTest,ReservationCheckInControllerTest,ReservationArrivedDirectSeatingControllerTest,LocalRuntimeReservationCheckInSecurityTest,LocalRuntimeReservationArrivedDirectSeatingSecurityTest" test
```

Result:

- All three commands passed.

Full verification:

```text
mvn test
```

Result:

```text
Tests run: 388, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
npm run build
```

Result:

```text
vue-tsc --noEmit passed
vite build passed
61 modules transformed
```

## 12. Boundary Check

Backend API changed: Yes, approved endpoint only.  
Business state machine changed: No.  
App Gate changed: Permission metadata only, adding `reservation.queue`.  
Migration changed: No.  
SQL files changed: No.  
Reservation status changed by queue success: No, remains `arrived`.  
QueueTicket created: Yes, approved V1 API behavior.  
Queue call/skip/rejoin created: No.  
Queue UI created: No.  
Seating from Queue created: No.  
Table assignment/status change created: No.  
No-show created: No.  
Cancellation created: No.  
Reservation list/calendar created: No.  
Table map created: No.  
Production database touched: No.  
Seed data inserted: No.  

## 13. Open Questions

- Future Queue call/skip/rejoin should be a separate contract and should decide whether `reservation.queue` is sufficient or a narrower permission key is required.
- Future Staff Home UI should decide separately whether to expose a Queue entry or Today View action shortcut.

## 14. Next Step Recommendation

- Continue with the next separately approved Queue UI or Queue operations contract only after this API slice remains green in full backend and frontend verification.
