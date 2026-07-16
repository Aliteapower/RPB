# Reservation Post-Booking Table Assignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tenant employees assign one eligible dining table to any confirmed, unassigned reservation and have the assigned table code immediately appear in existing reservation sharing.

**Architecture:** Add a focused reservation table-assignment application service with separate query and command entry points. Reuse `ReservationPreassignment`, table repositories, share projections, App Gate, audit, business events, and idempotency; add scoped row locks and exact-time conflict reads so concurrent assignment is safe without mixing preassignment with Seating.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA/JdbcTemplate, PostgreSQL/Flyway, Vue 3, TypeScript, Vite, JUnit 5, AssertJ, MockMvc.

## Global Constraints

- All confirmed and unassigned reservations are eligible regardless of `source_channel`.
- Assignment is limited to one `dining_table`; TableGroup assignment and reassignment are out of scope.
- Reservation must remain `confirmed`; do not create CheckIn, QueueTicket, Seating, occupancy, Cleaning, or Turnover data.
- Tenant and Store scope must be enforced in every query and write.
- The command requires `Idempotency-Key`, writes `reservation.table_assigned`, and records `reservation.table_assign` audit metadata.
- Use existing `reservation.create` for the write endpoint and `table.view` for the assignable-table query.
- New UI text must have `zh-CN` and `en-SG` i18n keys.
- Runtime database validation must use `target/local-postgres-current.txt`; never fall back to port 5432 or another worktree database.
- No unrelated reservation, queue, seating, sharing, or template behavior changes.

---

### Task 1: Persistence safety and exact-time availability

**Files:**
- Create: `src/main/resources/db/migration/V045__reservation_active_preassignment_uniqueness.sql`
- Create: `src/test/java/com/rpb/reservation/reservation/persistence/ReservationTableAssignmentMigrationTest.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationPreassignmentRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/port/out/DiningTableRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationPreassignmentJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/repository/DiningTableJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPreassignmentPersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/adapter/DiningTablePersistenceAdapter.java`
- Test: `src/test/java/com/rpb/reservation/reservation/persistence/ReservationCreatePersistenceTest.java`

**Interfaces:**
- Produces: `ReservationRepositoryPort.findByIdForUpdate(StoreScope, ReservationId)`.
- Produces: `DiningTableRepositoryPort.findByIdForUpdate(StoreScope, TableId)`.
- Produces: `ReservationPreassignmentRepositoryPort.findActiveResourceAssignmentsOverlapping(StoreScope, BusinessDate, TimeRange)`.

- [ ] **Step 1: Write the failing migration source test**

```java
@Test
void migrationEnforcesOneActiveAssignmentPerReservation() throws IOException {
    String sql = Files.readString(Path.of(
        "src/main/resources/db/migration/V045__reservation_active_preassignment_uniqueness.sql"
    ));
    assertThat(sql).contains(
        "create unique index",
        "tenant_id, store_id, reservation_id",
        "status = 'active'",
        "deleted_at is null"
    );
}
```

- [ ] **Step 2: Run the migration test and confirm RED**

Run: `mvn -q "-Dtest=ReservationTableAssignmentMigrationTest" test`

Expected: FAIL because `V045__reservation_active_preassignment_uniqueness.sql` does not exist.

- [ ] **Step 3: Add the partial unique index**

```sql
create unique index if not exists ux_reservation_preassignments_one_active_reservation
    on reservation_preassignments (tenant_id, store_id, reservation_id)
    where status = 'active' and deleted_at is null;
```

- [ ] **Step 4: Write failing adapter tests for locks and overlap semantics**

Add tests that require scoped pessimistic reads and prove `[start,end)` overlap behavior:

```java
assertThat(repository.findActiveResourceAssignmentsOverlapping(
    scope, businessDate, new TimeRange(start, end)
)).extracting(ReservationResourceAssignment::resourceCode).containsExactly("A01");
```

The fixture must include one overlapping confirmed assignment, one boundary-touching non-overlap, one cancelled reservation, and one deleted preassignment.

- [ ] **Step 5: Run focused persistence tests and confirm RED**

Run: `mvn -q "-Dtest=ReservationCreatePersistenceTest,ReservationTableAssignmentMigrationTest" test`

Expected: FAIL because the new port methods and repository queries are absent.

- [ ] **Step 6: Add scoped lock methods**

Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on JPA methods that include ID, Tenant, Store, and `deletedAt is null`, then delegate through adapters:

```java
default Optional<Reservation> findByIdForUpdate(StoreScope scope, ReservationId reservationId) {
    return findById(scope, reservationId);
}
```

```java
default Optional<DiningTable> findByIdForUpdate(StoreScope scope, TableId tableId) {
    return findById(scope, tableId);
}
```

Production adapters override both defaults with the pessimistic JPA queries.

- [ ] **Step 7: Add exact overlap read**

The native query must include:

```sql
and r.status in ('confirmed', 'arrived')
and r.business_date = :businessDate
and r.reserved_start_at < :requestedEnd
and r.reserved_end_at > :requestedStart
```

Map its projections through the existing `toAssignment` mapper. Do not change the existing date-scoped methods used by other workflows.

- [ ] **Step 8: Run persistence tests and confirm GREEN**

Run: `mvn -q "-Dtest=ReservationCreatePersistenceTest,ReservationTableAssignmentMigrationTest" test`

Expected: PASS.

- [ ] **Step 9: Commit the persistence slice**

```powershell
git add src/main/resources/db/migration/V045__reservation_active_preassignment_uniqueness.sql src/main/java/com/rpb/reservation/reservation src/main/java/com/rpb/reservation/table src/test/java/com/rpb/reservation/reservation/persistence
git commit -m "feat: protect reservation table preassignments"
```

---

### Task 2: Reservation table-assignment application use case

**Files:**
- Create: `src/main/java/com/rpb/reservation/reservation/application/command/AssignReservationTableCommand.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/query/AssignableReservationTablesQuery.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentError.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentResult.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/AssignableReservationTable.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/AssignableReservationTablesResult.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationTableAssignmentRule.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationTableAssignmentApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentApplicationServiceTest.java`

**Interfaces:**
- Consumes: scoped lock and overlap methods from Task 1.
- Produces: `listAssignableTables(AssignableReservationTablesQuery)`.
- Produces: `assignTable(AssignReservationTableCommand)`.

- [ ] **Step 1: Write failing application tests**

Cover these named cases:

```java
@Test void confirmedUnassignedPublicBookingCanAssignTable()
@Test void confirmedUnassignedStaffBookingCanAssignTable()
@Test void arrivedReservationIsRejected()
@Test void sameExistingAssignmentReturnsSuccessWithoutSecondSave()
@Test void differentExistingAssignmentIsRejected()
@Test void overlappingAssignmentIsRejected()
@Test void boundaryTouchingAssignmentDoesNotConflict()
@Test void inactiveAndCapacityMismatchedTablesAreRejected()
@Test void crossStoreTableIsNotFound()
@Test void assignmentWritesEventAuditAndCompletedIdempotency()
@Test void repeatedCompletedCommandReplaysSnapshot()
@Test void sameKeyDifferentPayloadReturnsIdempotencyConflict()
```

Assert the successful write keeps `reservation.status()` equal to `ReservationStatus.CONFIRMED` and that fake queue/seating repositories are not dependencies of the service.

- [ ] **Step 2: Run the service test and confirm RED**

Run: `mvn -q "-Dtest=ReservationTableAssignmentApplicationServiceTest" test`

Expected: compilation failure because the new use-case types are absent.

- [ ] **Step 3: Add command, query, result, and error types**

```java
public record AssignReservationTableCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID tableId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String source
) {}
```

```java
public record AssignableReservationTablesQuery(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID actorId,
    String actorType
) {}
```

`ReservationTableAssignmentError` must define stable codes for invalid command, store/permission failures, missing resources, invalid reservation state, existing assignment, capacity, availability, idempotency, persistence, event, and audit failures.

- [ ] **Step 4: Add the assignment rule**

```java
public ReservationTableAssignmentError validate(Reservation reservation, boolean alreadyAssigned) {
    if (reservation.status() != ReservationStatus.CONFIRMED) {
        return ReservationTableAssignmentError.RESERVATION_NOT_ASSIGNABLE;
    }
    return alreadyAssigned ? ReservationTableAssignmentError.RESERVATION_ALREADY_ASSIGNED : null;
}
```

Same-table idempotent success is handled before calling the `alreadyAssigned` branch.

- [ ] **Step 5: Implement assignable-table query**

Load the scoped reservation, require `confirmed` and unassigned, load `findVisibleResourceRows(..., partySize)`, exclude `inactive`, and remove table IDs found by the exact overlapping assignment read. Return table ID/code/display name/area/capacity sorted in repository order.

- [ ] **Step 6: Implement assignment transaction**

Use `@Transactional` and lock in fixed order:

```java
Reservation reservation = reservationRepository.findByIdForUpdate(scope, reservationId).orElseThrow(...);
Optional<ReservationResourceAssignment> existing = preassignmentRepository
    .findActiveAssignmentForReservation(scope, reservation.id().value());
DiningTable table = diningTableRepository.findByIdForUpdate(scope, tableId).orElseThrow(...);
```

Recheck exact overlap after both locks, save one active `ReservationPreassignment`, append `reservation.table_assigned`, append `reservation.table_assign`, and complete the idempotency snapshot.

- [ ] **Step 7: Run application tests and confirm GREEN**

Run: `mvn -q "-Dtest=ReservationTableAssignmentApplicationServiceTest" test`

Expected: PASS.

- [ ] **Step 8: Commit the application slice**

```powershell
git add src/main/java/com/rpb/reservation/reservation/application src/test/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentApplicationServiceTest.java
git commit -m "feat: add reservation table assignment service"
```

---

### Task 3: Protected REST API and security contract

**Files:**
- Create: `src/main/java/com/rpb/reservation/reservation/api/AssignReservationTableRequest.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentResponse.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/AssignableReservationTablesResponse.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentApiErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentApiErrorMapper.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentApiMapper.java`
- Create: `src/main/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentController.java`
- Test: `src/test/java/com/rpb/reservation/reservation/api/ReservationTableAssignmentControllerTest.java`
- Test: `src/test/java/com/rpb/reservation/reservation/integration/ReservationTableAssignmentApiIntegrationTest.java`
- Test: `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationTableAssignmentSecurityTest.java`

**Interfaces:**
- Consumes: Task 2 query and command methods.
- Produces: `GET .../assignable-tables` and `PUT .../table-assignment`.

- [ ] **Step 1: Write failing MockMvc controller tests**

Require tests for missing actor, wrong role, missing permission, wrong Store, missing idempotency key, invalid body, success, replay, and stable error mapping.

```java
mockMvc.perform(put(BASE + "/reservations/{reservationId}/table-assignment", reservationId)
        .header("Idempotency-Key", "assign-table-1")
        .contentType(APPLICATION_JSON)
        .content("""{"tableId":"%s"}""".formatted(tableId)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.tableCode").value("A01"));
```

- [ ] **Step 2: Run controller tests and confirm RED**

Run: `mvn -q "-Dtest=ReservationTableAssignmentControllerTest" test`

Expected: compilation failure because API classes are absent.

- [ ] **Step 3: Implement request/response mapper and error mapper**

Map `TABLE_NOT_AVAILABLE`, capacity, state, and existing-assignment failures to HTTP 409; missing resources to 404; permission failures to 403; invalid contract to 400; persistence/event/audit failures to 500.

- [ ] **Step 4: Implement protected controller**

Use:

```java
@GetMapping("/reservations/{reservationId}/assignable-tables")
@RequireAppGate(appKey = "reservation_queue", permission = "table.view")
```

```java
@PutMapping("/reservations/{reservationId}/table-assignment")
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.create")
```

Repeat current actor role, permission, and `canAccessStore` checks before invoking the service.

- [ ] **Step 5: Run controller tests and confirm GREEN**

Run: `mvn -q "-Dtest=ReservationTableAssignmentControllerTest" test`

Expected: PASS.

- [ ] **Step 6: Add PostgreSQL integration and local security tests**

Fixture creates a confirmed public booking and two tables. Assert assignment persists once, today view returns `assignedResourceCode`, share-info returns the assigned code, and public/customer actors cannot call protected endpoints.

- [ ] **Step 7: Run API integration tests with the configured local runtime**

Read `target/local-postgres-current.txt` first and use the pointed port through the existing local test database helper.

Run: `mvn -q "-Dtest=ReservationTableAssignmentApiIntegrationTest,LocalRuntimeReservationTableAssignmentSecurityTest" test`

Expected: PASS.

- [ ] **Step 8: Commit the API slice**

```powershell
git add src/main/java/com/rpb/reservation/reservation/api src/test/java/com/rpb/reservation/reservation/api src/test/java/com/rpb/reservation/reservation/integration src/test/java/com/rpb/reservation/walkin/auth
git commit -m "feat: expose reservation table assignment API"
```

---

### Task 4: Employee assignment dialog and reservation-card integration

**Files:**
- Create: `src/types/reservationTableAssignment.ts`
- Create: `src/api/reservationTableAssignmentApi.ts`
- Create: `src/components/reservation-workbench/ReservationTableAssignmentDialog.vue`
- Modify: `src/components/reservation-workbench/ReservationTodayListItem.vue`
- Modify: `src/components/reservation-workbench/ReservationTodayListPanel.vue`
- Modify: `src/pages/ReservationTodayViewPage.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Create: `src/test/java/com/rpb/reservation/appgate/ui/ReservationTableAssignmentUiValidationTest.java`

**Interfaces:**
- Consumes: Task 3 REST responses.
- Produces: `table-assignment-requested` and `assigned` UI events.

- [ ] **Step 1: Write the failing static UI validation test**

Assert source contains:

```java
assertThat(itemSource).contains(
    "item.status === 'confirmed'",
    "!item.assignedResourceId",
    "table-assignment-requested"
);
assertThat(dialogSource).contains("assignable-tables", "table-assignment", "Idempotency-Key");
```

- [ ] **Step 2: Run UI validation and confirm RED**

Run: `mvn -q "-Dtest=ReservationTableAssignmentUiValidationTest" test`

Expected: FAIL because the dialog/API do not exist.

- [ ] **Step 3: Add typed frontend API**

Define:

```ts
export interface AssignableReservationTable {
  tableId: string
  tableCode: string
  displayName: string
  areaName?: string | null
  capacityMin: number
  capacityMax: number
}
```

Expose `getAssignableReservationTables(storeId, reservationId)` and `assignReservationTable(storeId, reservationId, tableId, idempotencyKey)` with runtime response guards and the established API error class pattern.

- [ ] **Step 4: Implement the focused dialog**

The dialog receives `open`, `storeId`, and `item`, groups returned tables by area, keeps one selected table, disables confirm while submitting, refreshes after conflict, and emits `assigned` on success. Do not expose temporary groups or Table Switch.

- [ ] **Step 5: Wire visibility and events**

In the list item:

```ts
const showAssignTable = computed(() =>
  props.item.status === 'confirmed' &&
  !props.item.assignedResourceId?.trim() &&
  !props.item.currentResourceId?.trim()
)
```

Pass the event through the list panel to the page. The page opens one dialog and reloads today view on `assigned`.

- [ ] **Step 6: Invalidate stale share data**

Watch assignment fields in `ReservationTodayListItem`:

```ts
watch(
  () => [props.item.assignedResourceId, props.item.assignedResourceCode],
  () => {
    shareInfo.value = null
    resetShareFeedback()
  }
)
```

- [ ] **Step 7: Add zh-CN and en-SG translations**

Add keys for action label, dialog title/instructions, loading, empty, selection, confirmation, refreshing, success, and error states. Use `$t`/`t` for every visible string.

- [ ] **Step 8: Run UI validation and frontend build**

Run: `mvn -q "-Dtest=ReservationTableAssignmentUiValidationTest" test`

Run: `npm run build`

Expected: both PASS.

- [ ] **Step 9: Commit the frontend slice**

```powershell
git add src/types/reservationTableAssignment.ts src/api/reservationTableAssignmentApi.ts src/components/reservation-workbench src/pages/ReservationTodayViewPage.vue src/i18n/locales src/test/java/com/rpb/reservation/appgate/ui/ReservationTableAssignmentUiValidationTest.java
git commit -m "feat: add reservation table assignment dialog"
```

---

### Task 5: Share regressions, contract documentation, and release note

**Files:**
- Modify: `src/test/java/com/rpb/reservation/reservation/application/ReservationShareInfoApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/application/ReservationPublicShareApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/integration/ReservationShareInfoApiIntegrationTest.java`
- Create: `docs/api/RESERVATION_TABLE_ASSIGNMENT_API_CONTRACT.md`
- Create: `docs/release-notes/2026-07-15-reservation-post-booking-table-assignment.md`

**Interfaces:**
- Consumes: active preassignment written by Tasks 1–4.
- Produces: verified customer share text and public H5 payload with assigned table code.

- [ ] **Step 1: Add share regression tests**

Assert assigned and unassigned behavior separately:

```java
assertThat(assigned.shareInfo().shareText()).contains("A01");
assertThat(unassigned.shareInfo().shareText()).contains("待确认");
```

For public share assert `tableCode == "A01"` and `tablePending == false`.

- [ ] **Step 2: Run share tests and confirm existing projections are GREEN**

Run: `mvn -q "-Dtest=ReservationShareInfoApplicationServiceTest,ReservationPublicShareApplicationServiceTest,ReservationShareInfoApiIntegrationTest" test`

Expected: PASS without changing share production code; if a test exposes a projection defect, apply only the minimal scoped fix and rerun.

- [ ] **Step 3: Write API contract**

Document paths, permissions, headers, request/response JSON, exact error codes, time-overlap semantics, idempotent same-table behavior, and the separation between preassignment and Seating.

- [ ] **Step 4: Apply repository review skills**

Run `database-review`, `api-review`, and `tdd-review` checklists against the migration, contracts, tests, and implementation. Fix every blocking finding before final verification.

- [ ] **Step 5: Write release note**

Record user-visible behavior, migration `V045`, permission reuse, audit/event changes, deployment considerations, rollback behavior, and explicitly unchanged flows.

- [ ] **Step 6: Commit docs and share regressions**

```powershell
git add src/test/java/com/rpb/reservation/reservation docs/api/RESERVATION_TABLE_ASSIGNMENT_API_CONTRACT.md docs/release-notes/2026-07-15-reservation-post-booking-table-assignment.md
git commit -m "docs: record reservation table assignment release"
```

---

### Task 6: Full verification and code review

**Files:**
- Review all files changed by Tasks 1–5.

**Interfaces:**
- Consumes: completed feature.
- Produces: evidence-backed completion report.

- [ ] **Step 1: Run focused backend suite**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignment*Test,ReservationShareInfoApplicationServiceTest,ReservationPublicShareApplicationServiceTest,ReservationTodayViewApiIntegrationTest" test
```

Expected: PASS.

- [ ] **Step 2: Run frontend validation**

Run:

```powershell
npm run build
```

Expected: Vite production build succeeds with no TypeScript error.

- [ ] **Step 3: Run broader regression suite**

Run:

```powershell
mvn -q test
```

Expected: PASS. If environment-only tests require the local PostgreSQL runtime, verify the pointer and run the documented local-runtime subset separately rather than using another database.

- [ ] **Step 4: Review the final diff**

Run:

```powershell
git diff --check HEAD~5..HEAD
git status --short
```

Apply `code-review` and `verification-before-completion`. Confirm no unrelated files, hard-coded user text, secret values, or generated build artifacts are included.

- [ ] **Step 5: Record final evidence**

Report exact commands, pass/fail counts, any environment-limited checks, migration impact, and the final commit list. Do not claim runtime verification unless the pointer-backed PostgreSQL checks actually ran.
