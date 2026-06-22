# Reservation Queue App Gate Foundation V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable App Gate foundation around the existing reservation queue flows without changing the current business state machines or API paths.

**Architecture:** Create a dedicated `appgate` backend package that owns app metadata, tenant entitlements, store settings, decisions, command operations, and audit logs. API access is enforced through a controller annotation and Spring MVC interceptor that delegates to `AppGateService`; the staff homepage uses `/api/me/apps` for dynamic entry rendering.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC, Spring Data JPA, PostgreSQL/Flyway, JUnit/MockMvc, Vue 3, TypeScript, Vite.

---

## Source Inputs

- `D:\RPB\docs\backend\APP_GATE_FOUNDATION_V1_BRIEF.md`
- `C:\Users\ZhangXianLi\.codex\attachments\c51609a1-dbb0-47de-9e72-62fb4e5849d0\pasted-text.txt`

## Baseline Notes

- `npm run build` passed before implementation.
- `mvn test` had pre-existing failures in Controller boundary tests because `src/components/DateTimeWheelPicker.vue` exists but is not included in old fixed Vue file allowlists.
- `D:\RPB` is not a git repository, so no branch, worktree, or commit steps are available.

## File Structure

Create:

- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `src/main/java/com/rpb/reservation/appgate/package-info.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateDecision.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateDenyReason.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/appgate/api/AppGateApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/appgate/api/AppGateApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/appgate/api/MeAppsController.java`
- `src/main/java/com/rpb/reservation/appgate/api/MeAppsResponse.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateCommandService.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateAppEntry.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateAccessRequest.java`
- `src/main/java/com/rpb/reservation/appgate/guard/RequireAppGate.java`
- `src/main/java/com/rpb/reservation/appgate/guard/AppGateInterceptor.java`
- `src/main/java/com/rpb/reservation/appgate/guard/AppGateWebMvcConfiguration.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/entity/PlatformAppEntity.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/entity/TenantAppEntitlementEntity.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/entity/StoreAppSettingEntity.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/entity/AppGateAuditLogEntity.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/repository/PlatformAppJpaRepository.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/repository/TenantAppEntitlementJpaRepository.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/repository/StoreAppSettingJpaRepository.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/repository/AppGateAuditLogJpaRepository.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/adapter/AppGatePersistenceAdapter.java`
- `src/api/meAppsApi.ts`
- `src/types/meApps.ts`
- `src/test/java/com/rpb/reservation/appgate/AppGateMigrationTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateCommandServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/api/MeAppsControllerTest.java`
- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`

Modify:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingController.java`
- `src/main/java/com/rpb/reservation/cleaning/api/CleaningController.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/pages/StoreStaffHomePage.vue`
- Existing boundary tests with fixed Controller/Vue allowlists.

Final archive:

- `docs/backend/APP_GATE_FOUNDATION_V1_IMPLEMENTATION_REPORT.md`

## Tasks

### Task 1: Migration

- [ ] Add failing migration test proving app gate tables and `reservation_queue` seed/backfill are missing.
- [ ] Run `mvn -Dtest=com.rpb.reservation.appgate.AppGateMigrationTest test` and confirm failure.
- [ ] Add `V002__app_gate_foundation.sql`.
- [ ] Run the migration test and confirm it passes.

### Task 2: AppGateService

- [ ] Add failing unit tests for allowed access and deny reasons.
- [ ] Run `mvn -Dtest=com.rpb.reservation.appgate.application.AppGateServiceTest test` and confirm failure.
- [ ] Add appgate decision/domain/application/persistence classes.
- [ ] Run AppGateService tests and confirm they pass.

### Task 3: AppGateCommandService

- [ ] Add failing tests for tenant/store enable, disable, visibility update, config update, and audit log creation.
- [ ] Run `mvn -Dtest=com.rpb.reservation.appgate.application.AppGateCommandServiceTest test` and confirm failure.
- [ ] Implement command service operations and audit persistence.
- [ ] Run command service tests and confirm they pass.

### Task 4: Unified Guard

- [ ] Add failing guard integration tests for protected reservation, walk-in, cleaning start, and cleaning complete endpoints.
- [ ] Run `mvn -Dtest=com.rpb.reservation.appgate.guard.AppGateGuardIntegrationTest test` and confirm failure.
- [ ] Implement `@RequireAppGate`, interceptor, error mapper, and MVC registration.
- [ ] Annotate existing endpoint methods.
- [ ] Run guard tests and confirm they pass.

### Task 5: `/api/me/apps`

- [ ] Add failing controller tests for visible/available apps, tenant disabled, store disabled, hidden entry, no store access, and missing app permission.
- [ ] Run `mvn -Dtest=com.rpb.reservation.appgate.api.MeAppsControllerTest test` and confirm failure.
- [ ] Implement `/api/me/apps?storeId=xxx`.
- [ ] Update local security config to permit the local/test endpoint.
- [ ] Run `/api/me/apps` tests and confirm they pass.

### Task 6: Frontend Staff Home

- [ ] Add or update frontend type/API code for `/api/me/apps`.
- [ ] Update `StoreStaffHomePage.vue` to render entries returned by backend.
- [ ] Preserve existing route names and page routes.
- [ ] Run `npm run build` and confirm it passes.

### Task 7: Boundary And Regression Tests

- [ ] Update existing fixed Controller/Vue allowlists to include App Gate controller/guard artifacts and existing `DateTimeWheelPicker.vue`.
- [ ] Run controller boundary tests and confirm they pass.
- [ ] Run reservation, walk-in, and cleaning regression tests.

### Task 8: Smoke Test And Archive

- [ ] Run `mvn test`.
- [ ] Run `npm run build`.
- [ ] Run focused smoke commands for migration, guard denial, `/api/me/apps`, and frontend build.
- [ ] Write `docs/backend/APP_GATE_FOUNDATION_V1_IMPLEMENTATION_REPORT.md` with files created/modified, migration name, tables, protected endpoints, API, frontend changes, tests, regression result, known limitations, and state-machine non-refactor confirmation.
