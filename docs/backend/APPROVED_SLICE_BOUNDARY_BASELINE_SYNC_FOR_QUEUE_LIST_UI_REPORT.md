# Approved Slice Boundary Baseline Sync for Queue List UI Report

## 1. Read Documents

- `docs/frontend/QUEUE_LIST_UI_CONTRACT.md`
- `docs/frontend/QUEUE_LIST_UI_CHECKLIST.md`
- `docs/frontend/QUEUE_LIST_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_LIST_UI_VALIDATION_REPORT.md`
- `src/pages/QueueTicketListPage.vue`
- `src/api/queueTicketListApi.ts`
- `src/types/queueTicketList.ts`
- `docs/backend/QUEUE_SKIP_APPLICATION_IMPLEMENTATION_REPORT.md`
- `src/test/java/com/rpb/reservation/queue/application/QueueSkipApplicationServiceTest.java`
- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`

Confirmed baseline:

- `QueueTicketListPage.vue` is an approved Queue List UI artifact.
- `queueTicketListApi.ts` is an approved read-only frontend API client artifact.
- `queueTicketList.ts` is an approved frontend type artifact.
- Queue List UI completed real browser validation.
- Queue Skip Application remains application-layer only.
- Queue Skip API and Queue Skip UI are not implemented.

## 2. Failed Tests Investigated

Test 1:

- `CleaningControllerTest.noOtherVerticalSliceApiOrUiArtifactsAreCreated`
- Failing assertion: `assertThat(vueFiles).containsExactlyInAnyOrder(...)` at line 364 before this sync.
- Unexpected artifact: `src/pages/QueueTicketListPage.vue`
- Follow-up guard affected by the same stale page baseline: `noneMatch(CleaningControllerTest::isForbiddenUiFile)` because the file name contains `queue`.

Test 2:

- `ReservationControllerTest.noForbiddenReservationApiOrUiArtifactsAreCreated`
- Failing assertion: `assertThat(vueFiles).containsExactlyInAnyOrder(...)` at line 392 before this sync.
- Unexpected artifact: `src/pages/QueueTicketListPage.vue`

Test 3:

- `WalkInDirectSeatingControllerTest.noForbiddenVerticalSliceApiOrUiArtifactsAreCreated`
- Failing assertion: `assertThat(vueFiles).containsExactlyInAnyOrder(...)` at line 292 before this sync.
- Unexpected artifact: `src/pages/QueueTicketListPage.vue`
- Follow-up guard affected by the same stale page baseline: `noneMatch(WalkInDirectSeatingControllerTest::isForbiddenUiFile)` because the file name contains `queue`.

## 3. Root Cause

The three boundary tests scan approved Vue files and compare the full set against a static whitelist. Queue List UI V1 later approved and validated `src/pages/QueueTicketListPage.vue`, but these older boundary whitelist tests were not synced. The failures were stale baseline false positives, not new Queue Skip API/UI or business behavior.

## 4. Approved Artifacts Synced

Synced artifact:

- `src/pages/QueueTicketListPage.vue`

Sync method:

- Added `src/pages/QueueTicketListPage.vue` to the exact Vue file whitelist in all three failing tests.
- Added `src/pages/QueueTicketListPage.vue` to the queue-name forbidden UI exclusion sets only where needed:
  - `CleaningControllerTest`
  - `WalkInDirectSeatingControllerTest`

Not synced because the failures did not report them:

- `src/api/queueTicketListApi.ts`
- `src/types/queueTicketList.ts`

## 5. Forbidden Artifacts Still Blocked

- Queue Skip API: still blocked by queue API whitelist and no `QueueSkipController`, `QueueSkipRequest`, or `QueueSkipResponse` entry was added.
- Queue Skip UI: still blocked; no `QueueSkipPage.vue` or `queueSkipApi.ts` entry was added.
- Queue Rejoin: still blocked by filename/pattern boundary checks.
- Queue Display: still blocked by filename/pattern boundary checks.
- Queue Workbench: still blocked by filename/pattern boundary checks.
- Queue Call from list: not added; existing Queue Call remains separate.
- Queue Seat from list: not added; existing Seating From Called Queue remains separate.
- Table map: still blocked by `tablemap` / table map filename checks.
- Auto assignment: still blocked by filename/pattern boundary checks.
- No-show: still blocked by reservation no-show controller checks.
- Cancellation: still blocked by reservation cancellation controller checks.
- Cleaning: existing approved cleaning artifacts remain; no new cleaning mutation was added.
- Turnover: still blocked by turnover API checks.
- Migration: no migration whitelist or migration file changed.

## 6. Files Changed

- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
- `docs/backend/APPROVED_SLICE_BOUNDARY_BASELINE_SYNC_FOR_QUEUE_LIST_UI_REPORT.md`

## 7. Commands Executed

Initial failed reproduction:

```bash
mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test
```

Result:

- Failed as expected.
- Tests run: 43, failures: 3, errors: 0, skipped: 0.
- All three failures reported unexpected `src/pages/QueueTicketListPage.vue`.

Post-sync failed test group:

```bash
mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test
```

Result:

- Passed.

Queue Skip Application regression:

```bash
mvn -q "-Dtest=QueueSkipApplicationServiceTest" test
```

Result:

- Passed.

Full Maven regression:

```bash
mvn test
```

Result:

- Passed.
- Tests run: 504, failures: 0, errors: 0, skipped: 0.

Frontend build:

```bash
npm run build
```

Result:

- Passed.
- `vue-tsc --noEmit` passed.
- `vite build` passed with 77 modules transformed.

## 8. Test Result

- Three stale boundary tests: passed after page-only whitelist sync.
- `QueueSkipApplicationServiceTest`: passed.
- Full `mvn test`: passed, 504 tests.
- `npm run build`: passed.

## 9. Boundary Check

Production code changed: No

Backend API changed: No

Frontend business UI changed: No

Business state machine changed: No

App Gate metadata changed: No

Migration changed: No

Queue Skip API implemented: No

Queue Skip UI implemented: No

Queue Rejoin implemented: No

Queue Display implemented: No

Queue Workbench implemented: No

Table map implemented: No

Auto assignment implemented: No

No-show implemented: No

Cancellation implemented: No

Cleaning changed: No

Turnover changed: No

Production database touched: No

Seed data inserted: No

## 10. Open Questions

- None.

## 11. Next Step Recommendation

Proceed to the next approved slice only after this baseline sync is accepted. If Queue Skip API is next, keep it as a separate contract / implementation round and do not infer Queue Skip UI, Rejoin, Display, Workbench, table map, auto assignment, No-show, Cancellation, Cleaning, or Turnover scope from this test baseline sync.
