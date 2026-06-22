# Cleaning Complete API Implementation Report V1

## 1. Read Documents

- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`
- `docs/api/CLEANING_API_ERROR_CONTRACT.md`
- `docs/api/CLEANING_API_IDEMPOTENCY_CONTRACT.md`
- `docs/api/CLEANING_API_CONTRACT_CHECKLIST.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Previous round confirmation:

- Cleaning API contract completed.
- `CleaningApplicationService` implemented.
- `StartCleaningCommand` implemented.
- `CompleteCleaningCommand` implemented.
- Controller created before this round: No.
- API DTO Java class created before this round: No.
- API implemented before this round: No.
- Vue UI implemented: No.
- Reservation implemented: No.
- Queue implemented: No.
- Turnover BI implemented: No.
- Migration changed: No.

## 2. Created Controller / API DTO / Mapper

Created Cleaning API files:

- `src/main/java/com/rpb/reservation/cleaning/api/CleaningController.java`
- `src/main/java/com/rpb/reservation/cleaning/api/StartCleaningRequest.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CompleteCleaningRequest.java`
- `src/main/java/com/rpb/reservation/cleaning/api/StartCleaningResponse.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CompleteCleaningResponse.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningApiMapper.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningApiErrorCode.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningApiErrorResponse.java`

Created API tests:

- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`

Updated an existing WalkIn API boundary test so the previously valid WalkIn test no longer rejects the newly approved Cleaning API controller:

- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`

No production WalkIn API behavior was changed.

## 3. Endpoint Implemented

Start Cleaning:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
```

Complete Cleaning:

```text
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

Permission keys:

- Start Cleaning: `cleaning.start`
- Complete Cleaning: `cleaning.complete`

Implemented controller:

- `CleaningController`

## 4. Auth / Scope Approach

The implementation reuses the existing WalkIn API actor boundary:

- `CurrentActorProvider`
- `CurrentActor`

This remains a minimal API security boundary and does not implement a full JWT login system.

The controller validates:

- Current actor is present.
- Actor role is one of `tenant_admin`, `store_manager`, or `store_staff`.
- Actor has the required permission key.
- Actor can access the path `storeId`.
- `tenantId`, `actorId`, and `actorType` come from server-side actor context.
- `storeId`, `seatingId`, and `cleaningId` come from path parameters.

The request body does not accept trusted Tenant scope, actor scope, `tableId`, or `tableGroupId`.

## 5. Request Validation

Implemented request DTOs:

- `StartCleaningRequest`
- `CompleteCleaningRequest`

Both request DTOs only accept:

- `reasonCode`
- `note`

Validation and boundary behavior:

- Missing or blank `Idempotency-Key` returns `MISSING_IDEMPOTENCY_KEY`.
- Caller cannot pass `tableId`.
- Caller cannot pass `tableGroupId`.
- Caller cannot pass trusted `tenantId`, `actorId`, `actorType`, role, permission, or Store scope in the body.

## 6. Response Mapping

Implemented response DTOs:

- `StartCleaningResponse`
- `CompleteCleaningResponse`

Start response includes:

- `success`
- `cleaningId`
- `seatingId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency`

Complete response includes:

- `success`
- `cleaningId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency`

Response rules:

- API response is an API DTO, not a Domain Object.
- JPA Entity fields are not exposed.
- Repository, Mapper, persistence, and transaction internals are not exposed.
- Full AuditLog and StateTransitionLog metadata are not exposed.

Event codes exposed:

- Start Cleaning: `cleaning.started`, `table.cleaning`
- Complete Cleaning: `cleaning.completed`, `table.available`

## 7. Error Mapping

Implemented stable error envelope:

```json
{
  "success": false,
  "error": {
    "code": "CLEANING_NOT_FOUND",
    "messageKey": "cleaning.not_found",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Implemented required public API error codes:

- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `FORBIDDEN`
- `MISSING_IDEMPOTENCY_KEY`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `SEATING_NOT_FOUND`
- `SEATING_RESOURCE_NOT_FOUND`
- `CLEANING_NOT_FOUND`
- `CLEANING_ALREADY_ACTIVE`
- `CLEANING_ALREADY_COMPLETED`
- `CLEANING_TARGET_INVALID`
- `TABLE_NOT_FOUND`
- `TABLE_GROUP_INVALID`
- `TABLE_NOT_OCCUPIED`
- `TABLE_NOT_CLEANING`
- `ILLEGAL_STATE_TRANSITION`
- `AUDIT_WRITE_FAILED`
- `PERSISTENCE_ERROR`

HTTP status behavior:

- Start Cleaning success: `201 Created`
- Start Cleaning completed replay: `200 OK`
- Complete Cleaning success: `200 OK`
- Complete Cleaning completed replay: `200 OK`
- Missing idempotency key: `400 Bad Request`
- Forbidden / scope mismatch: `403 Forbidden`
- Not found: `404 Not Found`
- Conflict / idempotency conflict / in progress: `409 Conflict`
- Audit or persistence error: `500 Internal Server Error`

Confirmed product decision:

- `IDEMPOTENCY_IN_PROGRESS` maps to `409 Conflict`.

## 8. Idempotency Header Behavior

Header:

```text
Idempotency-Key: <key>
```

Implemented behavior:

- Missing or blank key returns `MISSING_IDEMPOTENCY_KEY`.
- Completed replay returns success response with `idempotency.replayed = true`.
- In-progress result maps to `IDEMPOTENCY_IN_PROGRESS` with `409 Conflict`.
- Failed idempotency maps to `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Hash conflict maps to `IDEMPOTENCY_CONFLICT`.

The API layer passes the idempotency key to the application command and does not duplicate business idempotency decisions.

## 9. Tests Executed

Red:

```text
mvn -q '-Dtest=CleaningControllerTest' test
```

Initial result:

- Failed as expected because Cleaning API controller, DTOs, mapper, and error mapper did not exist yet.

Green:

```text
mvn -q '-Dtest=CleaningControllerTest' test
```

Result:

- Passed.

Compatibility check:

```text
mvn -q '-Dtest=CleaningControllerTest,WalkInDirectSeatingControllerTest' test
```

Result:

- Passed.

Full verification:

```text
mvn test
```

Result:

- `BUILD SUCCESS`
- `Tests run: 136, Failures: 0, Errors: 0, Skipped: 0`

## 10. Test Result

The Cleaning API tests cover:

- Start Cleaning success with table resource.
- Start Cleaning success with table group resource.
- Start Cleaning completed replay.
- Complete Cleaning success with table resource.
- Complete Cleaning success with table group resource.
- Complete Cleaning completed replay.
- Missing `Idempotency-Key`.
- Seating not found.
- Seating resource not found.
- Cleaning not found.
- Cleaning already active.
- Cleaning already completed.
- Table not found.
- TableGroup invalid.
- Table not occupied.
- Table not cleaning.
- Illegal state transition.
- Audit write failure.
- Persistence failure.
- Idempotency in progress.
- Idempotency failed requires new key.
- Idempotency hash conflict.
- Forbidden role.
- Missing permission.
- Store scope mismatch.
- Boundary: no Reservation API, Queue API, Turnover API, forbidden Vue UI, migration, SQL, or seed data.

## 11. Boundary Check

Reservation API implemented: No  
Queue API implemented: No  
Turnover API implemented: No  
Vue UI implemented: No  
Migration changed: No  
SQL created: No  
Seed data inserted: No  
Production database touched: No  
Full Auth system implemented: No  
Reservation / Queue / Turnover business behavior changed: No  

## 12. Open Questions

- Should Cleaning API be added to a later OpenAPI document after integration validation?
- Should local runtime auth headers for Cleaning reuse the same documented local placeholder pattern as WalkIn?

## 13. Open Conflicts

None.

## 14. Next Step Recommendation

Next round recommendation:

```text
Cleaning Complete API Integration Validation
```

The next round should validate:

- API -> Controller -> Application Service -> Repository Port -> Persistence Adapter -> JPA Repository -> PostgreSQL schema.
- Start Cleaning and Complete Cleaning database effects.
- Idempotency replay, in-progress, failed-key, and hash-conflict behavior.
- No Reservation, Queue, Turnover BI, Vue UI, migration, SQL, seed data, or production database changes.
