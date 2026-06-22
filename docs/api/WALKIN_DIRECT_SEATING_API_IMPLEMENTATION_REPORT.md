# WalkIn Direct Seating API Implementation Report

## 1. Read Documents

- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/api/API_CONTRACT_CHECKLIST.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/PERSISTENCE_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

## 2. Created Controller / API DTO / Mapper

Created API layer files under `src/main/java/com/rpb/reservation/walkin/api/`:

- `WalkInDirectSeatingController`
- `SeatWalkInDirectlyRequest`
- `SeatWalkInDirectlyResponse`
- `WalkInDirectSeatingApiMapper`
- `WalkInDirectSeatingApiErrorMapper`
- `ApiErrorCode`
- `ApiErrorResponse`
- `ApiIdempotencyResponse`
- `CurrentActor`
- `CurrentActorProvider`
- `SecurityContextCurrentActorProvider`

Created API tests under `src/test/java/com/rpb/reservation/walkin/api/`:

- `WalkInDirectSeatingControllerTest`

## 3. Endpoint Implemented

- Method: `POST`
- Path: `/api/v1/stores/{storeId}/walk-ins/direct-seating`
- Permission key: `walkin.direct_seating.create`
- Scope: WalkIn Direct Seating only.

No other API endpoint was implemented.

## 4. Auth / Scope Approach

This round adds a minimal API-layer auth boundary, not a full authentication system.

- `CurrentActorProvider` is the API boundary for server-side actor context.
- `SecurityContextCurrentActorProvider` reads `CurrentActor` from Spring Security `Authentication.principal` or `Authentication.details` when present.
- The controller enforces allowed roles:
  - `tenant_admin`
  - `store_manager`
  - `store_staff`
- The controller rejects other roles with `FORBIDDEN`.
- The controller requires permission `walkin.direct_seating.create`.
- The controller uses `tenantId` from `CurrentActor`.
- The controller uses `storeId` from the path.
- The controller rejects path stores outside the actor store scope with `STORE_SCOPE_MISMATCH`.

Not implemented in this round:

- JWT login flow
- User management
- Auth API
- Production user hardcoding
- Full RBAC administration

## 5. Request Validation

Implemented API request DTO:

- `partySize`
- `customerId`
- `customerName`
- `customerNickname`
- `phoneE164`
- `tableId`
- `tableGroupId`
- `overrideReasonCode`
- `overrideNote`

Implemented request validation:

- Missing or non-positive `partySize` returns `INVALID_PARTY_SIZE`.
- Invalid E.164 `phoneE164` returns `INVALID_PHONE_E164`.
- Both `tableId` and `tableGroupId` present returns `RESOURCE_CONFLICT`.
- Missing `Idempotency-Key` returns `MISSING_IDEMPOTENCY_KEY`.

The request body does not accept trusted `tenantId`.

## 6. Response Mapping

Implemented success response DTO:

- `success`
- `walkInId`
- `seatingId`
- `resource.type`
- `resource.id`
- `resource.label`
- `partySize`
- `status`
- `events`
- `idempotency.status`
- `idempotency.replayed`

The API response is a DTO and does not expose:

- JPA Entity
- Repository internals
- Mapper internals
- Full audit metadata

Returned event codes:

- `walk_in.created`
- `seating.created`
- `table.occupied`

## 7. Error Mapping

Implemented unified error envelope:

```json
{
  "success": false,
  "error": {
    "code": "TABLE_NOT_AVAILABLE",
    "messageKey": "walkin.direct_seating.table_not_available",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Implemented stable error code mapping:

- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `FORBIDDEN`
- `INVALID_PARTY_SIZE`
- `INVALID_PHONE_E164`
- `INVALID_CUSTOMER_IDENTITY`
- `RESOURCE_CONFLICT`
- `TABLE_NOT_AVAILABLE`
- `TABLE_CAPACITY_INSUFFICIENT`
- `TABLE_LOCK_CONFLICT`
- `TABLE_INACTIVE`
- `TABLE_GROUP_INVALID`
- `SEATING_SOURCE_INVALID`
- `SEATING_RESOURCE_INVALID`
- `OVERRIDE_REASON_REQUIRED`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `MISSING_IDEMPOTENCY_KEY`
- `ILLEGAL_STATE_TRANSITION`
- `AUDIT_WRITE_FAILED`
- `PERSISTENCE_ERROR`

All error responses use stable `messageKey` values. No display copy is hardcoded.

## 8. Idempotency Header Behavior

Implemented API behavior:

- Missing `Idempotency-Key` returns `400 MISSING_IDEMPOTENCY_KEY`.
- Completed replay from application result returns `200 OK` with `replayed=true`.
- New success returns `201 Created` with `replayed=false`.
- In-progress application result returns `409 IDEMPOTENCY_IN_PROGRESS`.
- Failed idempotency application result returns `409 IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Hash conflict application result returns `409 IDEMPOTENCY_CONFLICT`.

Request hash calculation remains in the application service.

## 9. Tests Executed

Red:

- `mvn -q '-Dtest=WalkInDirectSeatingControllerTest' test`
- Result before implementation: failed at test compile because API layer classes did not exist.

Green:

- `mvn -q '-Dtest=WalkInDirectSeatingControllerTest' test`
- Result: passed.

Full:

- `mvn test`
- Result: `70 tests, 0 failures, 0 errors, 0 skipped`.

## 10. Test Result

API tests cover:

- Success with no-phone customer
- Success with specified table
- Success with existing table group
- Success with auto-selected table
- Completed idempotency replay
- Missing `Idempotency-Key`
- Invalid party size
- Invalid phone
- Both `tableId` and `tableGroupId`
- Table lock conflict
- Table unavailable
- Capacity insufficient
- Invalid table group
- Override missing
- Idempotency in progress
- Idempotency failed requires new key
- Idempotency hash conflict
- Forbidden role
- Store scope mismatch
- No Reservation / Queue / Cleaning / Turnover API artifacts
- No Vue UI artifact

## 11. Boundary Check

- Reservation API implemented: No
- Queue API implemented: No
- Cleaning API implemented: No
- Turnover API implemented: No
- Vue UI implemented: No
- Migration changed: No
- SQL created: No
- Seed data inserted: No
- Mock runtime data inserted: No
- Production database touched: No
- Java changes limited to WalkIn Direct Seating API layer and tests: Yes

## 12. Open Questions

- Should the next round introduce a real authenticated principal type shared across API slices?
- Should table labels be hydrated by application result in a future response contract, or remain hidden until a dedicated table read API exists?

## 13. Open Conflicts

None.

## 14. Next Step Recommendation

Next round can enter `WalkIn Direct Seating API Integration Validation`, still without Vue UI and without expanding to Reservation / Queue / Cleaning / Turnover APIs.
