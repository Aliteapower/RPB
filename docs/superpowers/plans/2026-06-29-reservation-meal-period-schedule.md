# Reservation Meal Period Schedule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build platform seed meal periods, store-effective meal period overrides, and slot-based reservation time selection.

**Architecture:** Add reservation schedule configuration as platform/store settings, expose query/mutation APIs, and make reservation creation validate against generated store-effective slots. Keep Reservation, Queue, Seating, and Table state machines unchanged.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, JdbcTemplate, Vue 3, TypeScript, Vite.

---

### Task 1: Persistence And Seed Data

**Files:**
- Create: `src/main/resources/db/migration/V019__reservation_meal_period_schedule.sql`
- Create: `src/test/java/com/rpb/reservation/reservation/persistence/ReservationMealPeriodMigrationTest.java`

- [ ] Write a failing migration test that expects platform/store meal period tables, lunch/dinner seed rows, store scope constraints, and `platform.reservation_meal_period.manage`.
- [ ] Run `mvn -q "-Dtest=ReservationMealPeriodMigrationTest" test` and confirm it fails because the migration does not exist.
- [ ] Add the migration with platform seed rows and store-scoped override tables.
- [ ] Run the migration test and confirm it passes.

### Task 2: Backend Schedule Domain And Slots

**Files:**
- Create backend reservation schedule records/services/repository under `src/main/java/com/rpb/reservation/reservation/application/**` and `src/main/java/com/rpb/reservation/reservation/persistence/**`.
- Create tests under `src/test/java/com/rpb/reservation/reservation/application/**`.

- [ ] Write failing tests for lunch slot generation, cross-day dinner slot generation, past-slot disabling, and arbitrary time rejection.
- [ ] Run the focused tests and confirm they fail.
- [ ] Implement slot generation and validation against effective store periods.
- [ ] Run focused tests and confirm they pass.

### Task 3: API Surface

**Files:**
- Create: `docs/api/RESERVATION_MEAL_PERIOD_API_CONTRACT.md`
- Create/update platform reservation meal-period controller DTOs.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- Modify reservation create request/command mapping for optional `businessDate`.

- [ ] Write failing controller tests for platform seed, tenant store periods, time-slot query, and reservation create businessDate mapping.
- [ ] Run focused controller tests and confirm failures.
- [ ] Implement controllers, DTOs, error mapping, and route wiring.
- [ ] Run focused controller tests and confirm they pass.

### Task 4: Frontend Staff And Admin UI

**Files:**
- Create API/types files for meal period seed and reservation slots.
- Modify platform nav/router and create platform seed page.
- Modify tenant settings page for store meal periods.
- Modify reservation create dialog to use grouped slots.

- [ ] Write static UI validation tests for slot-based reservation creation and new admin routes.
- [ ] Run focused UI validation tests and confirm failures.
- [ ] Implement Vue/TypeScript changes.
- [ ] Run `npm run build` and focused UI validation tests.

### Task 5: Review And Release Notes

**Files:**
- Create: `docs/frontend/RESERVATION_MEAL_PERIOD_SCHEDULE_IMPLEMENTATION_REPORT.md`

- [ ] Run backend focused tests.
- [ ] Run frontend build.
- [ ] Apply API, database, TDD, UI, code, and release-note review checklists.
- [ ] Document changed behavior, migration, permission, risk, and rollback notes.
