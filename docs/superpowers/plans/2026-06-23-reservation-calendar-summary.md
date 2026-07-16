# Reservation Calendar Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only monthly reservation calendar summary API and use it to show reservation dots/counts on the staff reservation calendar.

**Architecture:** Reuse the existing reservation today-view boundary: `reservation_queue` App Gate, `reservation.today_view` permission, store scope checks, and read-only application service flow. Add a small repository projection for daily counts, a frontend API/type pair, and keep calendar rendering inside `ReservationMonthCalendar.vue`.

**Tech Stack:** Spring Boot MVC, Spring Data JPA, PostgreSQL, Vue 3, TypeScript, Vite, JUnit, MockMvc.

---

### Task 1: Backend Calendar Summary API

**Files:**
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationTodayViewControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/integration/ReservationTodayViewApiIntegrationTest.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationCalendarSummaryResponse.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/ReservationCalendarSummaryDay.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/ReservationCalendarSummaryResult.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/query/ReservationCalendarSummaryQuery.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationTodayViewApplicationService.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationCalendarSummaryRow.java`
- Create: `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationCalendarSummaryProjection.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`

- [ ] **Step 1: Write failing controller and integration tests**

Add tests that call:

```text
GET /api/v1/stores/{storeId}/reservations/calendar-summary?month=2030-06
```

Expected success response:

```json
{
  "success": true,
  "storeId": "20000000-0000-0000-0000-000000000991",
  "month": "2030-06",
  "storeTimezone": "Asia/Singapore",
  "days": [
    { "businessDate": "2030-06-20", "reservationCount": 6 },
    { "businessDate": "2030-06-21", "reservationCount": 1 }
  ]
}
```

The endpoint must require `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")`, reject invalid month with `INVALID_BUSINESS_DATE`, and stay read-only.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn -q "-Dtest=ReservationTodayViewControllerTest,ReservationTodayViewApiIntegrationTest" test
```

Expected: FAIL because `calendarSummary` endpoint/classes do not exist yet.

- [ ] **Step 3: Implement minimal backend**

Implement `ReservationCalendarSummaryQuery`, `ReservationCalendarSummaryDay`, `ReservationCalendarSummaryResult`, API response record, repository port method, projection, JPA grouped query, adapter mapper, service method, and controller method.

Rules:
- Use `YearMonth.parse(month)`.
- Default missing `month` to the store-local current month.
- Count non-deleted reservations in the month by `business_date`.
- Exclude `draft`; include operational and historical statuses visible in today-view: `confirmed`, `arrived`, `seated`, `cancelled`, `no_show`, `completed`.
- Do not write idempotency, audit, business events, state transitions, queue tickets, seatings, table locks, or migrations.

- [ ] **Step 4: Run backend tests and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=ReservationTodayViewControllerTest,ReservationTodayViewApiIntegrationTest" test
```

Expected: PASS.

### Task 2: Frontend Calendar Summary Client And UI

**Files:**
- Create: `src/types/reservationCalendarSummary.ts`
- Create: `src/api/reservationCalendarSummaryApi.ts`
- Modify: `src/components/reservation-workbench/ReservationMonthCalendar.vue`
- Modify: `src/pages/ReservationTodayViewPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`

- [ ] **Step 1: Write failing UI boundary tests**

Assert the frontend has:

```text
getReservationCalendarSummary
/api/v1/stores/${storeId}/reservations/calendar-summary
reservationCounts
reservation-calendar__reservation-count
aria-label
```

Also assert the reservation page uses the selected month to refresh summary and passes counts into `ReservationMonthCalendar`.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn -q "-Dtest=StaffUiV12TableSelectionValidationTest,ReservationCreateApiIntegrationTest" test
```

Expected: FAIL because frontend API/types and calendar count rendering do not exist yet.

- [ ] **Step 3: Implement minimal frontend**

Create a typed API client. In `ReservationTodayViewPage.vue`, load calendar summary for the visible/selected month and reload it after create/cancel. In `ReservationMonthCalendar.vue`, replace simple `markedDates` usage with `reservationCounts: Record<string, number>` and render a small count badge/dot under days with reservations.

- [ ] **Step 4: Run frontend build and UI tests**

Run:

```powershell
cmd /c npm run build
mvn -q "-Dtest=StaffUiV12TableSelectionValidationTest,ReservationCreateApiIntegrationTest" test
```

Expected: PASS.

### Task 3: API Contract And Final Verification

**Files:**
- Create: `docs/api/RESERVATION_CALENDAR_SUMMARY_API_CONTRACT.md`

- [ ] **Step 1: Add contract doc**

Document endpoint, permission, query params, response shape, error envelope, read-only boundary, no migration, and frontend usage.

- [ ] **Step 2: Run full targeted verification**

Run:

```powershell
git diff --check
cmd /c npm run build
mvn -q "-Dtest=ReservationTodayViewControllerTest,ReservationTodayViewApiIntegrationTest,StaffUiV12TableSelectionValidationTest,ReservationCreateApiIntegrationTest,AppGateRequiredPermissionTest" test
git status --short --untracked-files=all
```

Expected: all commands pass; status only shows files from this calendar summary task.
