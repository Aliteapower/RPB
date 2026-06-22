# App Gate Runtime Validation V1 Report

Date: 2026-06-19

## Scope

This validation only checks App Gate runtime behavior on existing business interfaces.

Guardrails honored:

- No new business module was added.
- Reservation, WalkIn, and Cleaning business state machines were not refactored.
- Existing API paths were not changed.
- No `V003` migration was added.
- The existing `V002__app_gate_foundation.sql` migration was kept as the App Gate foundation migration.

## Runtime Gap Found And Fixed

Validation found one App Gate foundation gap before the runtime checks could pass:

- App Gate denied requests returned the correct 403 response, but did not write a rejection audit row.
- `app_gate_audit_logs.action` in `V002__app_gate_foundation.sql` also did not allow a denial action.

Fix applied inside the existing App Gate foundation boundary:

- Added `AppGateDenialAuditService`.
- Updated the App Gate interceptor to record denied decisions before returning 403.
- Extended the V002 action check constraint to allow `APP_GATE_DENIED`.

No V003 migration was introduced because this was a V002 foundation constraint gap found during validation.

## Validation Matrix

| # | Requirement | Evidence | Result |
|---|-------------|----------|--------|
| 1 | Authorized tenant + enabled store + permitted actor allows access | `ReservationCreateApiIntegrationTest.appGateRuntimeAllowsEnabledTenantStoreAndPermittedActor` posts to `POST /api/v1/stores/{storeId}/reservations` and receives `201` | Passed |
| 2 | Tenant without app entitlement is rejected | `ReservationCreateApiIntegrationTest.appGateRuntimeRejectsTenantWithoutReservationQueueEntitlementAndAuditsDenial` deletes the entitlement and receives `403 TENANT_APP_NOT_ENABLED` | Passed |
| 3 | Store with app disabled is rejected | `ReservationCreateApiIntegrationTest.appGateRuntimeRejectsStoreWithoutEnabledReservationQueueAndAuditsDenial` disables the store setting and receives `403 STORE_APP_NOT_ENABLED` | Passed |
| 4 | Actor without permission is rejected | `ReservationCreateApiIntegrationTest.appGateRuntimeRejectsActorWithoutReservationPermissionAndAuditsDenial` removes actor permissions and receives `403 PERMISSION_DENIED` | Passed |
| 5 | `/api/me/apps?storeId=xxx` returns the correct entry | `ReservationCreateApiIntegrationTest.meAppsRuntimeReturnsOnlyVisibleEnabledReservationQueueEntryForStore` validates visible/enabled response and hidden/disabled filtering | Passed |
| 6 | Staff home only displays enabled app entry | `StoreStaffHomePageAppGateRuntimeValidationTest.staffHomeUsesMeAppsAndGatesReservationQueueEntry` validates the page calls `fetchMeApps`, uses `reservation_queue`, and gates the entry on `hasReservationQueue` | Passed |
| 7 | `app_gate_audit_logs` records denials | App Gate runtime rejection tests assert `APP_GATE_DENIED` rows with `denyReason` and `requiredPermission` in `after_json`; migration test inserts and reads the same action | Passed |
| 8 | Existing WalkIn / Cleaning / Reservation flows are not broken | Focused runtime regression ran WalkIn direct seating, Cleaning complete, and Reservation create integration tests together | Passed |

## Tests Added Or Updated

- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`
  - Added runtime App Gate success and rejection coverage on the real reservation create endpoint.
  - Added `/api/me/apps` runtime response coverage.
  - Added denial audit assertions.

- `src/test/java/com/rpb/reservation/appgate/ui/StoreStaffHomePageAppGateRuntimeValidationTest.java`
  - Added static validation for the staff home dynamic App Gate entry behavior.

- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`
  - Added guard-level assertion that App Gate denials are audited before returning the existing error response shape.

- `src/test/java/com/rpb/reservation/appgate/AppGateMigrationTest.java`
  - Added migration-level coverage proving `APP_GATE_DENIED` is accepted by the V002 audit-log action constraint.

- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeWalkInDirectSeatingSecurityTest.java`
  - Updated the WebMvcTest slice fixture for the App Gate interceptor dependency.

## Verification Commands

Red check before the audit fix:

```text
mvn -Dtest=com.rpb.reservation.reservation.integration.ReservationCreateApiIntegrationTest,com.rpb.reservation.appgate.ui.StoreStaffHomePageAppGateRuntimeValidationTest test
```

Result:

```text
Failed as expected before implementation:
- denial audit assertions expected 1 row but found 0 rows
```

Focused App Gate runtime and guard verification:

```text
mvn -Dtest=com.rpb.reservation.reservation.integration.ReservationCreateApiIntegrationTest,com.rpb.reservation.appgate.ui.StoreStaffHomePageAppGateRuntimeValidationTest,com.rpb.reservation.appgate.guard.AppGateGuardIntegrationTest test
```

Result:

```text
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Migration and App Gate service verification:

```text
mvn -Dtest=com.rpb.reservation.appgate.AppGateMigrationTest,com.rpb.reservation.appgate.application.AppGateCommandServiceTest,com.rpb.reservation.appgate.application.AppGateServiceTest test
```

Result:

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Focused real business-flow smoke:

```text
mvn -Dtest=com.rpb.reservation.walkin.integration.WalkInDirectSeatingApiIntegrationTest,com.rpb.reservation.cleaning.integration.CleaningCompleteApiIntegrationTest,com.rpb.reservation.reservation.integration.ReservationCreateApiIntegrationTest test
```

Result:

```text
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Full backend verification:

```text
mvn test
```

Result:

```text
Tests run: 256, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend verification:

```text
npm run build
```

Result:

```text
vue-tsc --noEmit passed
vite build passed
49 modules transformed
```

## Runtime Notes

- App Gate rejection responses keep the existing shape:
  `{ success:false, error:{code,messageKey,details} }`.
- The tested denial codes are `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, and `PERMISSION_DENIED`.
- Denial audit rows use action `APP_GATE_DENIED`.
- Denial audit snapshots include `decision`, `denyReason`, `messageKey`, and `requiredPermission`.
- The smoke tests run against the test profile with SpringBootTest, MockMvc, and a local temporary PostgreSQL database.
- Production data was not touched.
- Maven still prints non-blocking Spring Data Redis repository-assignment info logs and Mockito dynamic-agent deprecation warnings.
