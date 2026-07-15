# Reservation Preassignment And Seating Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make post-booking table assignment and direct Seating apply one consistent ownership rule so only currently eligible Tables can be assigned and the Reservation's own preassignment is atomically consumed by successful Seating.

**Architecture:** Keep `ReservationPreassignment` as time-range ownership rather than physical occupancy. Tighten the existing assignment rule at both list and command boundaries, add a scoped preassignment release port backed by the existing columns, and make the direct-Seating service resolve and consume its own preassignment inside the existing Seating transaction.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, JUnit 5, Mockito, AssertJ, MockMvc, Maven, Vue 3/Vite regression build.

## Global Constraints

- Preserve Tenant and Store scope on every read and write.
- Reservation assignment remains `confirmed`; only CheckIn and Seating change it to `arrived` and `seated`.
- Time conflicts keep half-open `[reservedStartAt,reservedEndAt)` semantics.
- Never override `occupied`, `cleaning`, `inactive`, active Table locks, active SeatingResource occupancy, or another Reservation's preassignment.
- Do not add or change REST paths, DTOs, permissions, Flyway migrations, or frontend behavior.
- Do not automatically complete stale Seating or Cleaning records.
- Use the PostgreSQL runtime referenced by `target/local-postgres-current.txt` for local integration/runtime validation.
- Keep existing reservation, Seating, Table, audit, event, and idempotency contracts unless this plan explicitly adds preassignment release metadata.

---

### Task 1: Make table-assignment list and command use current physical eligibility

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationTableAssignmentRule.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationTableAssignmentApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentApplicationServiceTest.java`

**Interfaces:**
- Consumes: `TableLockRepositoryPort.existsActiveConflict(StoreScope, String, UUID, OffsetDateTime)`.
- Consumes: `SeatingRepositoryPort.existsActiveResourceOccupancy(StoreScope, String, UUID)`.
- Produces: one shared application predicate that accepts only status `available`, matching capacity, no active lock, no active occupancy, and no overlapping preassignment.

- [ ] **Step 1: Write failing list tests for every unavailable physical state**

Add a parameterized or loop-based test that returns rows for all six statuses and proves only `available` survives:

```java
@Test
void assignableTableQueryReturnsOnlyCurrentlyAvailableTables() {
    Scenario scenario = Scenario.ready("public_booking");
    UUID locked = UUID.randomUUID();
    UUID reserved = UUID.randomUUID();
    UUID occupied = UUID.randomUUID();
    UUID cleaning = UUID.randomUUID();
    UUID inactive = UUID.randomUUID();
    when(scenario.tableRepository.findVisibleResourceRows(scenario.scope, null, new PartySize(2))).thenReturn(List.of(
        scenario.row(scenario.tableId, "A01", 1, 4, "available"),
        scenario.row(locked, "A02", 1, 4, "locked"),
        scenario.row(reserved, "A03", 1, 4, "reserved"),
        scenario.row(occupied, "A04", 1, 4, "occupied"),
        scenario.row(cleaning, "A05", 1, 4, "cleaning"),
        scenario.row(inactive, "A06", 1, 4, "inactive")
    ));

    AssignableReservationTablesResult result = scenario.service.listAssignableTables(scenario.query());

    assertThat(result.tables()).extracting(AssignableReservationTable::tableCode).containsExactly("A01");
}
```

- [ ] **Step 2: Write failing list and command tests for active locks and occupancy**

Add mocks to `Scenario` and inject them into the service:

```java
private final TableLockRepositoryPort tableLockRepository = mock(TableLockRepositoryPort.class);
private final SeatingRepositoryPort seatingRepository = mock(SeatingRepositoryPort.class);
```

Test list filtering and stale-dialog command revalidation separately:

```java
@Test
void assignableTableQueryExcludesActiveLockAndOccupancy() {
    Scenario scenario = Scenario.ready("staff");
    UUID lockedId = UUID.randomUUID();
    UUID occupiedId = UUID.randomUUID();
    when(scenario.tableRepository.findVisibleResourceRows(scenario.scope, null, new PartySize(2))).thenReturn(List.of(
        scenario.row(lockedId, "A02", 1, 4, "available"),
        scenario.row(occupiedId, "A03", 1, 4, "available")
    ));
    when(scenario.tableLockRepository.existsActiveConflict(
        eq(scenario.scope), eq("dining_table"), eq(lockedId), any(OffsetDateTime.class)
    )).thenReturn(true);
    when(scenario.seatingRepository.existsActiveResourceOccupancy(
        scenario.scope, "dining_table", occupiedId
    )).thenReturn(true);

    AssignableReservationTablesResult result = scenario.service.listAssignableTables(scenario.query());

    assertThat(result.tables()).isEmpty();
}

@Test
void assignmentRejectsTableThatBecameOccupiedAfterListLoad() {
    Scenario scenario = Scenario.ready("staff");
    when(scenario.seatingRepository.existsActiveResourceOccupancy(
        scenario.scope, "dining_table", scenario.tableId
    )).thenReturn(true);

    assertThat(scenario.service.assignTable(scenario.command()).error())
        .isEqualTo(ReservationTableAssignmentError.TABLE_NOT_AVAILABLE);
    verify(scenario.preassignmentRepository, never()).save(any(), any());
}
```

- [ ] **Step 3: Run the assignment application test and verify RED**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentApplicationServiceTest" test
```

Expected: FAIL because non-`inactive` statuses are still returned and the service has no lock/occupancy dependencies.

- [ ] **Step 4: Tighten the reusable assignment rule**

Change `validateTable` to require `AVAILABLE` before capacity:

```java
public ReservationTableAssignmentError validateTable(DiningTable table, PartySize partySize) {
    if (table.status() != DiningTableStatus.AVAILABLE) {
        return ReservationTableAssignmentError.TABLE_NOT_AVAILABLE;
    }
    if (!table.capacity().includes(partySize)) {
        return ReservationTableAssignmentError.TABLE_CAPACITY_INSUFFICIENT;
    }
    return null;
}
```

- [ ] **Step 5: Add the physical blocker dependencies and one helper**

Inject `TableLockRepositoryPort` and `SeatingRepositoryPort`. Use one helper from list and command paths:

```java
private boolean hasPhysicalBlocker(StoreScope scope, UUID tableId) {
    return tableLockRepository.existsActiveConflict(
        scope, RESOURCE_DINING_TABLE, tableId, OffsetDateTime.now(clock)
    ) || seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_DINING_TABLE, tableId);
}
```

Require `row.status()` to equal `available` in `isEligible`, filter blocker rows in `listAssignableTables`, and throw `TABLE_NOT_AVAILABLE` after the locked Table lookup when the helper returns true.

- [ ] **Step 6: Run the focused test and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentApplicationServiceTest" test
```

Expected: PASS with public/staff parity, `confirmed` unchanged, boundary-touching overlap accepted, and physical blockers rejected.

- [ ] **Step 7: Commit the assignment consistency slice**

```powershell
git add src/main/java/com/rpb/reservation/reservation/application/rule/ReservationTableAssignmentRule.java src/main/java/com/rpb/reservation/reservation/application/service/ReservationTableAssignmentApplicationService.java src/test/java/com/rpb/reservation/reservation/application/ReservationTableAssignmentApplicationServiceTest.java
git commit -m "fix: align reservation table assignment availability"
```

---

### Task 2: Add a scoped preassignment lookup and release persistence boundary

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationPreassignmentRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationPreassignmentJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPreassignmentPersistenceAdapter.java`
- Test: `src/test/java/com/rpb/reservation/reservation/persistence/ReservationTableAssignmentPersistenceAdapterTest.java`

**Interfaces:**
- Produces: `Optional<ReservationPreassignment> findActivePreassignmentForReservation(StoreScope scope, UUID reservationId)`.
- Produces: `boolean releaseActivePreassignment(StoreScope scope, UUID preassignmentId, UUID reservationId, String resourceType, UUID resourceId, OffsetDateTime releasedAt)`.
- Consumes: existing `reservation_preassignments.status`, `released_at`, and `updated_at`; no schema change.

- [ ] **Step 1: Write failing adapter tests for lookup and exact scoped release**

Use a mocked JPA repository and assert every ownership key is forwarded:

```java
@Test
void releasesOnlyMatchingActivePreassignmentWithinScope() {
    ReservationPreassignmentJpaRepository repository = mock(ReservationPreassignmentJpaRepository.class);
    UUID preassignmentId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    OffsetDateTime releasedAt = OffsetDateTime.parse("2026-07-16T00:15:00Z");
    when(repository.releaseActivePreassignment(
        preassignmentId, TENANT_ID, STORE_ID, reservationId, "dining_table", tableId, releasedAt
    )).thenReturn(1);

    boolean released = new ReservationPreassignmentPersistenceAdapter(repository)
        .releaseActivePreassignment(SCOPE, preassignmentId, reservationId, "dining_table", tableId, releasedAt);

    assertThat(released).isTrue();
    verify(repository).releaseActivePreassignment(
        preassignmentId, TENANT_ID, STORE_ID, reservationId, "dining_table", tableId, releasedAt
    );
}
```

Add a lookup test that maps entity ID, Reservation ID, resource type/ID, and `active` status back to `ReservationPreassignment`.

- [ ] **Step 2: Run the persistence adapter test and verify RED**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentPersistenceAdapterTest" test
```

Expected: compilation failure because the two port/repository methods do not exist.

- [ ] **Step 3: Add default port methods**

Keep unrelated fake repositories source-compatible:

```java
default Optional<ReservationPreassignment> findActivePreassignmentForReservation(
    StoreScope scope, UUID reservationId
) {
    return Optional.empty();
}

default boolean releaseActivePreassignment(
    StoreScope scope,
    UUID preassignmentId,
    UUID reservationId,
    String resourceType,
    UUID resourceId,
    OffsetDateTime releasedAt
) {
    return false;
}
```

- [ ] **Step 4: Add JPA lookup and conditional update**

Add a scoped derived lookup and a native update guarded by every ownership field:

```java
Optional<ReservationPreassignmentEntity> findFirstByTenantIdAndStoreIdAndReservationIdAndStatusAndDeletedAtIsNullOrderByPreassignedAtAsc(
    UUID tenantId, UUID storeId, UUID reservationId, String status
);

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(value = """
    update reservation_preassignments
       set status = 'released', released_at = :releasedAt, updated_at = :releasedAt
     where id = :preassignmentId
       and tenant_id = :tenantId
       and store_id = :storeId
       and reservation_id = :reservationId
       and resource_type = :resourceType
       and coalesce(table_id, table_group_id) = :resourceId
       and status = 'active'
       and deleted_at is null
    """, nativeQuery = true)
int releaseActivePreassignment(
    @Param("preassignmentId") UUID preassignmentId,
    @Param("tenantId") UUID tenantId,
    @Param("storeId") UUID storeId,
    @Param("reservationId") UUID reservationId,
    @Param("resourceType") String resourceType,
    @Param("resourceId") UUID resourceId,
    @Param("releasedAt") OffsetDateTime releasedAt
);
```

Declare all seven exact `@Param` values shown by the SQL.

- [ ] **Step 5: Implement adapter mapping and boolean release**

Map the entity to the existing domain record, and return `updated == 1`. Treat `0` as false and any impossible count above one as false so the application can fail closed.

- [ ] **Step 6: Run focused persistence tests and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentPersistenceAdapterTest" test
```

Expected: PASS; no Flyway file is added.

- [ ] **Step 7: Commit the release persistence slice**

```powershell
git add src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationPreassignmentRepositoryPort.java src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationPreassignmentJpaRepository.java src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPreassignmentPersistenceAdapter.java src/test/java/com/rpb/reservation/reservation/persistence/ReservationTableAssignmentPersistenceAdapterTest.java
git commit -m "feat: release reservation preassignment on seating"
```

---

### Task 3: Convert the owning preassignment into Seating atomically

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingApplicationServiceTest.java`

**Interfaces:**
- Consumes: Task 2 `findActivePreassignmentForReservation` and `releaseActivePreassignment`.
- Produces: own-preassignment-aware Table validation and release metadata in the existing `reservation.seat` audit.

- [ ] **Step 1: Write the failing own-preassignment success test**

```java
@Test
void seatsArrivedReservationOnOwnPreassignedTableAndReleasesOwnership() {
    Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
    scenario.preassignmentRepository.assignPreassignment(scenario.preassignment("active"));

    ReservationArrivedDirectSeatingResult result = scenario.service()
        .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

    assertThat(result.success()).isTrue();
    assertThat(scenario.preassignmentRepository.released).containsExactly(scenario.preassignmentRepository.active.id());
    assertThat(scenario.reservationRepository.saved.getFirst().status()).isEqualTo(ReservationStatus.SEATED);
    assertThat(scenario.diningTableRepository.saved.getFirst().status()).isEqualTo(DiningTableStatus.OCCUPIED);
}
```

Extend the fake preassignment repository with one active domain record, exact ownership lookup, conditional release, released-ID capture, and a `releaseSucceeds` switch.

- [ ] **Step 2: Write failing safety and idempotency tests**

Add named tests:

```text
ownPreassignmentDoesNotOverrideOccupiedTable
ownPreassignedReservedTableCanSeatWithoutPhysicalOccupancy
preassignmentReleaseFailureReturnsRepositoryError
completedIdempotencyReplayDoesNotReleasePreassignmentTwice
seatAuditRecordsReleasedPreassignmentOwnership
```

The occupied case must remain `TABLE_NOT_AVAILABLE`. The audit assertion must contain preassignment ID, `"preassignmentStatusBefore":"active"`, and `"preassignmentStatusAfter":"released"`.

- [ ] **Step 3: Run the direct-Seating application test and verify RED**

Run:

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingApplicationServiceTest" test
```

Expected: FAIL because the service neither loads the domain preassignment nor releases/audits it.

- [ ] **Step 4: Load ownership before resource availability validation**

In `execute`, load the active domain preassignment once, then pass it into resource resolution and ownership validation:

```java
ReservationPreassignment ownPreassignment = preassignmentRepository
    .findActivePreassignmentForReservation(scope, reservation.id().value())
    .orElse(null);
ResourceSelection selection = resolveResource(command, scope, reservation, ownPreassignment);
validatePreassignment(scope, reservation, selection, ownPreassignment);
```

For Table validation, accept `RESERVED` only when the domain preassignment matches the selected resource. Continue checking capacity, lock, and active Seating occupancy for every accepted status.

- [ ] **Step 5: Release only the matching ownership inside the transaction**

After SeatingResource and Table occupancy are created and before the completed audit/idempotency record, call:

```java
private boolean releaseOwnPreassignment(
    StoreScope scope,
    Reservation reservation,
    ResourceSelection selection,
    ReservationPreassignment ownPreassignment,
    Instant releasedAt
) {
    if (ownPreassignment == null) {
        return false;
    }
    if (!matches(ownPreassignment, selection)) {
        throw new ApplicationFailure(resourceUnavailableError(selection));
    }
    boolean released = preassignmentRepository.releaseActivePreassignment(
        scope,
        ownPreassignment.id(),
        reservation.id().value(),
        selection.resourceType(),
        selection.resourceId(),
        OffsetDateTime.ofInstant(releasedAt, ZoneOffset.UTC)
    );
    if (!released) {
        throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
    }
    return true;
}
```

Throw `REPOSITORY_SAVE_FAILED` when a matching active preassignment exists but the conditional release returns false. Because the service already runs in the transaction template, all Seating/Table/Reservation mutations roll back together in production.

- [ ] **Step 6: Add release context to the existing Seating audit**

When a preassignment was consumed, append these fields to `appendCompletedAudit` metadata:

```java
String preassignmentExtra = """
    ,"preassignmentId":"%s","preassignmentResourceType":"%s","preassignmentResourceId":"%s","preassignmentStatusBefore":"active","preassignmentStatusAfter":"released","preassignmentReleasedAt":"%s"
    """.formatted(
        ownPreassignment.id(),
        ownPreassignment.resourceType(),
        ownPreassignment.resourceId(),
        releasedAt
    ).trim();
```

Do not add a new REST response field or public business-event type.

- [ ] **Step 7: Run the focused direct-Seating test and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingApplicationServiceTest" test
```

Expected: PASS; existing capacity, lock, other-preassignment, status, events, idempotency, and state-transition assertions remain green.

- [ ] **Step 8: Commit the direct-Seating slice**

```powershell
git add src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java src/test/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingApplicationServiceTest.java
git commit -m "fix: consume reservation table ownership on seating"
```

---

### Task 4: Prove assignment and Seating consistency with PostgreSQL and APIs

**Files:**
- Modify: `src/test/java/com/rpb/reservation/reservation/integration/ReservationTableAssignmentApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingLocalRuntimeTransactionTest.java`

**Interfaces:**
- Consumes: unchanged assignment and direct-Seating REST contracts.
- Produces: database evidence that unavailable Tables are not assignable and own preassignment becomes released exactly once after Seating.

- [ ] **Step 1: Add failing assignment API tests for occupied rows and stale commands**

Create one confirmed Reservation and one `occupied` Table. Assert the GET response omits it and PUT returns the existing `TABLE_NOT_AVAILABLE` conflict without inserting a preassignment. Repeat the PUT after GET with the Table changed from `available` to `occupied` to prove server-side revalidation.

- [ ] **Step 2: Add a failing direct-Seating API integration test**

Insert an arrived Reservation, available Table, and active matching preassignment. Call the existing direct-Seating endpoint and assert:

```java
assertThat(fixture.scalarString(
    "select status from reservation_preassignments where reservation_id = ?", RESERVATION_ID
)).isEqualTo("released");
assertThat(fixture.scalarString(
    "select status from dining_tables where id = ?", TABLE_ID
)).isEqualTo("occupied");
assertThat(fixture.scalarString(
    "select status from reservations where id = ?", RESERVATION_ID
)).isEqualTo("seated");
assertThat(fixture.countWhere(
    "select count(*) from seating_resources where table_id = '%s' and status = 'active'".formatted(TABLE_ID)
)).isEqualTo(1);
```

Repeat the same idempotency key and assert the preassignment remains one released row and no second SeatingResource appears.

- [ ] **Step 3: Run API integration tests and verify RED**

First read `target/local-postgres-current.txt`. If it is missing or stale, repair the runtime using `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md` before continuing.

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentApiIntegrationTest,ReservationArrivedDirectSeatingApiIntegrationTest" test
```

Expected: FAIL on occupied-table eligibility and active-to-released preassignment assertions.

- [ ] **Step 4: Add the end-to-end local-runtime path**

Extend the existing local transaction test to execute:

```text
public-style Reservation creation (confirmed)
-> protected employee table assignment
-> CheckIn (arrived)
-> direct Seating on assigned Table
```

Assert one active SeatingResource, Reservation `seated`, Table `occupied`, preassignment `released`, assignment and Seating audits, existing business events, and completed idempotency records for every command.

- [ ] **Step 5: Run PostgreSQL/API integration tests and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=ReservationTableAssignmentApiIntegrationTest,ReservationArrivedDirectSeatingApiIntegrationTest,ReservationArrivedDirectSeatingLocalRuntimeTransactionTest" test
```

Expected: PASS using only the pointer-backed PostgreSQL runtime.

- [ ] **Step 6: Commit the integration slice**

```powershell
git add src/test/java/com/rpb/reservation/reservation/integration/ReservationTableAssignmentApiIntegrationTest.java src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiIntegrationTest.java src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingLocalRuntimeTransactionTest.java
git commit -m "test: cover preassigned reservation seating flow"
```

---

### Task 5: Review, release note, and full verification

**Files:**
- Create: `docs/release-notes/2026-07-16-reservation-preassignment-seating-consistency.md`
- Review: all files changed by Tasks 1-4.

**Interfaces:**
- Consumes: completed implementation and fresh test output.
- Produces: release/deployment evidence without a migration or API compatibility action.

- [ ] **Step 1: Apply the repository TDD review checklist**

Record coverage for: public/staff parity, physical status, active occupancy, locks, capacity, half-open time boundaries, Tenant/Store scope, idempotent replay, audit metadata, Reservation `confirmed` after assignment, `arrived -> seated`, and release rollback.

- [ ] **Step 2: Run focused backend regression**

```powershell
mvn -q "-Dtest=ReservationTableAssignment*,ReservationArrivedDirectSeating*,ReservationTodayViewApiIntegrationTest,ReservationShareInfoApplicationServiceTest,ReservationPublicShareApplicationServiceTest" test
```

Expected: PASS.

- [ ] **Step 3: Run frontend production build**

```powershell
npm run build
```

Expected: Vite/TypeScript production build succeeds; no frontend source change is expected.

- [ ] **Step 4: Run full backend regression**

```powershell
mvn -q test
```

Expected: PASS. If an environment-only test is intentionally excluded by its profile, report it explicitly and retain the pointer-backed integration result from Task 4.

- [ ] **Step 5: Run static diff checks and repository code review**

```powershell
git diff --check 3408cf5e..HEAD
git status --short
```

Confirm no secret, generated artifact, unrelated refactor, API change, migration, or hard-coded user-facing text was introduced.

- [ ] **Step 6: Write the release note**

Document the user-visible fix, root cause, unchanged API/schema/permissions, preassignment release behavior, operational note that stale active Seating must be completed normally, verification commands, deployment order, and rollback behavior.

- [ ] **Step 7: Commit documentation**

```powershell
git add docs/release-notes/2026-07-16-reservation-preassignment-seating-consistency.md
git commit -m "docs: record preassignment seating consistency fix"
```

- [ ] **Step 8: Final verification before push or deployment**

Re-run the focused backend suite after the documentation commit, verify `git status --short` is empty, and report the exact commit list and test evidence. Push/deployment is performed only when requested or when continuing the user's already-authorized release workflow.
