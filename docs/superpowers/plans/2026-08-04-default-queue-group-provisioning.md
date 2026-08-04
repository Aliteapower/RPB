# Default Queue Group Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the four canonical QueueGroups for every newly created Store and backfill existing Stores that have never had QueueGroup configuration.

**Architecture:** A Queue-owned application port and JDBC adapter perform one idempotent, Store-scoped default insert. Platform tenant onboarding and explicit Store creation invoke that capability inside their existing transactions. An additive Flyway migration applies the same zero-history rule to existing data.

**Tech Stack:** Java 21, Spring Boot 3.5, JdbcTemplate, PostgreSQL 17, Flyway, JUnit 5, AssertJ, MockMvc.

## Global Constraints

- Default groups are exactly `1-2`, `3-4`, `5-6`, and `7+` with non-overlapping ranges.
- All rows must carry the supplied `tenant_id` and `store_id`.
- Historical backfill applies only when a Store has no QueueGroup row of any status or deletion state.
- Existing active, inactive, soft-deleted, custom, or partial Store configuration must remain unchanged.
- Queue API, frontend, App Gate, entitlement, and permission contracts must not change.
- Database validation must use `target/local-postgres-current.txt` for manual migration and runtime checks.

---

### Task 1: Lock New-Store Provisioning With Failing Integration Tests

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/auth/integration/AuthPostgresTestDatabase.java`

**Interfaces:**
- Consumes: existing platform tenant and Store creation APIs.
- Produces: observable assertions over persisted `queue_groups` rows.

- [ ] **Step 1: Add cleanup and migration-fixture support**

Delete QueueGroups for `codex-%` Stores before deleting those Stores in `setUp`. Extend the auth test database migration chain through V046 once the migration exists.

- [ ] **Step 2: Write the failing default-Store test**

Create a single-Store tenant through `POST /api/v1/platform/tenants`, resolve its Store, and assert this exact ordered result:

```java
assertThat(defaultQueueGroups(tenantId, storeId)).containsExactly(
    new QueueGroupRow("1-2", 1, 2, "queue.group.1_2", "active", 1),
    new QueueGroupRow("3-4", 3, 4, "queue.group.3_4", "active", 2),
    new QueueGroupRow("5-6", 5, 6, "queue.group.5_6", "active", 3),
    new QueueGroupRow("7+", 7, null, "queue.group.7_plus", "active", 4)
);
```

- [ ] **Step 3: Write the failing explicit-Store test**

Create a group tenant and then a Store through `POST /api/v1/platform/tenants/{tenantId}/stores`; assert the same four rows for that Store.

- [ ] **Step 4: Run tests and verify RED**

Run:

```powershell
mvn "-Dtest=PlatformTenantApiIntegrationTest#platformAdminCreatesTenantWithDefaultQueueGroups+platformAdminCreatesStoreWithDefaultQueueGroups" test
```

Expected: both tests fail because `queue_groups` is empty for the new Store.

### Task 2: Implement Queue-Owned Default Provisioning

**Files:**
- Create: `src/main/java/com/rpb/reservation/queue/application/port/out/DefaultQueueGroupProvisioningPort.java`
- Create: `src/main/java/com/rpb/reservation/queue/application/DefaultQueueGroupProvisioningService.java`
- Create: `src/main/java/com/rpb/reservation/queue/persistence/DefaultQueueGroupProvisioningPersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantService.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantStructureService.java`
- Test: `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`

**Interfaces:**
- Produces: `void DefaultQueueGroupProvisioningPort.provisionDefaults(StoreScope scope)`.
- Produces: `void DefaultQueueGroupProvisioningService.provisionDefaults(StoreScope scope)`.
- Consumes: `StoreScope`, `TenantId`, and existing platform Store identifiers.

- [ ] **Step 1: Add the application port and service**

The service validates a non-null StoreScope and delegates to the port. Keep Store transaction ownership in the calling platform service.

- [ ] **Step 2: Add the JDBC adapter**

Use one `WITH defaults (...) AS (VALUES ...) INSERT ... SELECT` statement. Guard the insert with:

```sql
where not exists (
    select 1
    from queue_groups existing
    where existing.tenant_id = ?
      and existing.store_id = ?
)
on conflict (tenant_id, store_id, group_code) where deleted_at is null do nothing
```

- [ ] **Step 3: Wire both Store creation paths**

After `ensureDefaultStore` in `PlatformTenantService.createTenant`, call the provisioner with the new tenant and Store identifiers. After `insertStore` in `PlatformTenantStructureService.createStore`, call the same provisioner before other onboarding side effects.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the two Task 1 tests and confirm both pass with exactly four rows.

- [ ] **Step 5: Add and run idempotency coverage**

Autowire the provisioning service in the integration test, invoke it twice for an already provisioned Store, and assert the Store still has exactly the same four rows.

### Task 3: Backfill Existing Zero-History Stores

**Files:**
- Create: `src/main/resources/db/migration/V046__backfill_default_queue_groups.sql`
- Create: `src/test/java/com/rpb/reservation/auth/integration/DefaultQueueGroupProvisioningMigrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/auth/integration/AuthPostgresTestDatabase.java`

**Interfaces:**
- Consumes: existing `stores`, `queue_groups`, Store-scope foreign key, and partial unique index.
- Produces: four canonical QueueGroups only for zero-history, non-deleted Stores.

- [ ] **Step 1: Extend the PostgreSQL test fixture**

Add `startWithBaseSchema()` and package-visible `applyMigration(String)` so the migration test can run V001, controlled fixtures, and V046 against a real temporary PostgreSQL database.

- [ ] **Step 2: Write the migration test before the migration**

Create four Stores: zero history, active custom group, inactive group, and soft-deleted group. Apply V046 and assert:

```text
zero history -> four canonical active defaults
active custom -> unchanged one row
inactive history -> unchanged one row
soft-deleted history -> unchanged one row, zero active rows
```

Apply V046 again and assert every Store's total remains unchanged.

- [ ] **Step 3: Run migration test and verify RED**

Run:

```powershell
mvn -Dtest=DefaultQueueGroupProvisioningMigrationTest test
```

Expected: fail because V046 does not exist.

- [ ] **Step 4: Add the migration**

Implement the same canonical values and all-history `NOT EXISTS` predicate as the application adapter. Use a set-based insert and the existing scoped partial unique index for replay safety.

- [ ] **Step 5: Run migration test and verify GREEN**

Re-run the focused migration test and confirm all preservation and replay assertions pass.

### Task 4: Verify Queue Resolution And Regression Scope

**Files:**
- Modify only if a discovered regression requires a focused fix.

**Interfaces:**
- Consumes: persisted defaults through existing `QueueGroupRepositoryPort.findActiveByPartySize`.
- Produces: unchanged reservation and walk-in queue behavior.

- [ ] **Step 1: Run platform provisioning tests**

```powershell
mvn -Dtest=PlatformTenantApiIntegrationTest test
```

- [ ] **Step 2: Run queue selection and command regressions**

```powershell
mvn -Dtest=ReservationArrivedToQueueApplicationServiceTest,ReservationArrivedToQueueApiIntegrationTest,WalkInQueueControllerTest test
```

- [ ] **Step 3: Run migration regression**

```powershell
mvn -Dtest=DefaultQueueGroupProvisioningMigrationTest test
```

- [ ] **Step 4: Run frontend build**

```powershell
npm run build
```

No frontend files should change; this confirms the unchanged API/UI contract still compiles.

### Task 5: Pointer-Database Validation And Delivery Artifacts

**Files:**
- Create: `docs/troubleshooting/2026-08-04-default-queue-group-provisioning.md`
- Create: `docs/release-notes/2026-08-04-default-queue-group-provisioning.md`

**Interfaces:**
- Consumes: `target/local-postgres-current.txt`, V046, and the completed implementation.
- Produces: current validation evidence, rollback notes, and release summary.

- [ ] **Step 1: Validate the pointer runtime**

Read the pointer immediately before use. Confirm its port is listening and that its `runtimeWorktree` is valid; repair/start it using `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md` if stale.

- [ ] **Step 2: Apply V046 in a rollback transaction on the pointer database**

Create transaction-scoped fixture Stores covering zero history and custom history, apply the migration body, assert results, and roll back the fixtures. Do not use port 5432 or another database.

- [ ] **Step 3: Write troubleshooting and release records**

Document the App Gate evidence, missing onboarding initialization, generic fix, no API/permission change, migration behavior, test commands, residual risk, and rollback constraints.

- [ ] **Step 4: Run final verification**

Run `git diff --check`, focused Maven tests, and `npm run build`. Review the complete diff for tenant isolation, transaction safety, migration replay, and accidental LCD-specific logic.

- [ ] **Step 5: Commit implementation**

```powershell
git add -- src/main src/test docs/troubleshooting docs/release-notes
git commit -m "fix: provision default queue groups for new stores"
```
