# App Gate Operational Handoff Report

## App Gate Operational Handoff Result

### 1. Read Documents

- `docs/backend/APP_GATE_FOUNDATION_V1_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_RUNTIME_VALIDATION_V1_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `src/main/java/com/rpb/reservation/appgate/**`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingController.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningController.java`
- `src/api/meAppsApi.ts`
- `src/types/meApps.ts`
- `src/pages/StoreStaffHomePage.vue`
- `src/router/index.ts`

Confirmed from the prior runtime report and current verification:

- App Gate Runtime Validation V1 passed.
- 8 runtime acceptance points passed.
- WalkIn, Cleaning, and Reservation regression coverage passed.
- `mvn test` passed with 256 tests, 0 failures, 0 errors.
- `npm run build` passed.
- `V003` migration was not created.
- Business state machines were not changed.
- Existing API paths were not changed.
- Production database was not touched.

### 2. Created / Updated Files

Created:

- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_REJECTION_CODES.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF_REPORT.md`

No existing business code, SQL, frontend behavior, API path, or migration file was modified.

No `docs/backend/README.md` existed, so no backend index link was added.

### 3. App Key Rules

- The Reservation Platform operational app key is `reservation_queue`.
- It covers Reservation, WalkIn, Queue, Seating, Cleaning, and Turnover.
- The current protected runtime capabilities are Reservation Create, WalkIn Direct Seating, Cleaning Start, and Cleaning Complete.
- Future CheckIn, Queue, Seating, No-show, and Cancellation endpoints in this business line default to `reservation_queue`.
- Do not create duplicate app keys such as `reservation_app`, `queue_app`, `walkin_app`, `cleaning_app`, or `seating_app`.

### 4. Permission Rules

Documented current permission keys:

- `walkin.direct_seating.create`
- `cleaning.start`
- `cleaning.complete`
- `reservation.create`

Documented future naming examples:

- `reservation.check_in`
- `reservation.cancel`
- `reservation.no_show`
- `reservation.seat`
- `queue.ticket.create`
- `queue.call`
- `queue.skip`
- `queue.rejoin`
- `seating.create`
- `seating.change_table`

Rules: lowercase, dot hierarchy, business object first, stable action second, no page names, no controller names, no URL fragments, no temporary pinyin.

### 5. Annotation / Guard Rules

- Current project annotation is `@RequireAppGate(appKey = "reservation_queue", permission = "...")`.
- Every protected endpoint must declare app key and permission.
- Store scope must come from path, query, or trusted server context.
- Tenant scope must come from server-side actor/security context.
- Request-body `tenantId` must not be trusted for App Gate scope.
- App Gate checks must stay centralized in guard/service logic, not scattered across business services.
- Denial happens before the business handler and must not trigger business state changes.

### 6. Rejection Codes

Created `docs/backend/APP_GATE_REJECTION_CODES.md` with canonical codes:

- `APP_NOT_FOUND`
- `APP_INACTIVE`
- `TENANT_APP_NOT_ENTITLED`
- `TENANT_APP_SUSPENDED`
- `STORE_APP_DISABLED`
- `STORE_APP_NOT_CONFIGURED`
- `APP_PERMISSION_DENIED`
- `APP_GATE_SCOPE_MISMATCH`
- `APP_GATE_ACTOR_MISSING`
- `APP_GATE_STORE_MISSING`
- `APP_GATE_UNKNOWN_DENIAL`

Also documented current V1 runtime code mapping:

- `APP_DISABLED`
- `TENANT_APP_NOT_ENABLED`
- `TENANT_APP_EXPIRED`
- `STORE_APP_NOT_ENABLED`
- `STORE_ACCESS_DENIED`
- `PERMISSION_DENIED`

### 7. Audit Rules

- Denied requests must write `app_gate_audit_logs`.
- Denial action is `APP_GATE_DENIED`.
- Denial audit must include tenant/store/actor/app/permission/reject evidence where known.
- Denial metadata must not contain sensitive request body content.
- Allow-request audit is optional in V1.
- If denial audit writing fails, the business handler must not run.
- Current V1 stores denial snapshot in `after_json` with `decision`, `denyReason`, `messageKey`, and `requiredPermission`.

### 8. /api/me/apps Rules

- `GET /api/me/apps?storeId=xxx` returns app entries visible and usable for the current actor/store.
- It returns only active platform apps that are tenant-entitled, store-enabled, entry-visible, and actor-permitted.
- Current response fields are `appKey`, `appName`, `status`, `entryRoute`, `entryVisible`, and `permissions`.
- `StoreStaffHomePage.vue` must render operational app entries from this endpoint, not from hardcoded entitlement assumptions.

### 9. New Slice Checklist

Created `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md` with the required 20 checks:

- app ownership.
- duplicate app prevention.
- permission naming.
- endpoint annotation.
- tenant/store/actor scope source.
- denial tests.
- success regression.
- audit evidence.
- staff-home entry and `/api/me/apps` coverage.
- state-machine/API-path/migration boundaries.
- handoff/report update.

### 10. New Slice Template

Created `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md` with:

- App section.
- Permission section.
- Protected endpoints table.
- Scope source section.
- Deny cases.
- Audit fields.
- Tests.
- Boundary check.
- Reservation CheckIn example.

### 11. Future CheckIn Guidance

Future Reservation CheckIn must use:

```text
app_key = reservation_queue
permission = reservation.check_in
```

Business behavior remains:

```text
confirmed -> arrived
```

CheckIn must not create QueueTicket, Seating, or table occupancy merely because it is App Gate protected.

### 12. Commands

- `mvn test`: Passed. Tests run: 256, Failures: 0, Errors: 0, Skipped: 0.
- `npm run build`: Passed. `vue-tsc --noEmit` and Vite build completed. 49 modules transformed.

### 13. Boundary Check

New business module created: No

Reservation state machine changed: No

WalkIn/Cleaning/Reservation API path changed: No

V003 migration created: No

Production database touched: No

New app_key created: No

New permission model implemented: No

Java business logic modified: No

Controller behavior modified: No

Repository/entity modified: No

Vue business feature modified: No

SQL file modified: No

### 14. Open Questions

- None blocking for this handoff.
- Future approved code-change round may decide whether to rename or map current V1 runtime rejection codes to the canonical catalog.
- Future approved schema/runtime round may decide whether `app_gate_audit_logs` needs explicit request method/path/handler metadata columns instead of storing available denial evidence in JSON.

### 15. Next Step Recommendation

- Use `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md` before any CheckIn, Queue, Seating, No-show, or Cancellation endpoint work.
- For Reservation CheckIn, start from `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md` and keep the business transition limited to `confirmed -> arrived`.
- Do not add a new app key for the reservation/queue/seating/cleaning loop.

