# Final Review Fix Report

## Scope

Closed the final operating-entity deletion review gaps without changing the API shape, database schema, migrations, dependencies, permissions, tenant isolation, or business state machines.

Implementation commit: `e648d14f test: close operating entity delete audit gaps`.

The workspace pointer `D:\RPB\target\local-postgres-current.txt` was read before database-backed validation. It points to port `61644`. The integration tests continued to use the repository's isolated PostgreSQL 17 test runtime through `AuthPostgresTestDatabase`; no backend process, migration, or ad hoc SQL was run against a fallback database.

## Troubleshooting Report

### Symptom

- Existing tests did not prove either PostgreSQL lock direction with independent transactions.
- The delete integration test asserted only coarse row counts, not immutable 409/replay state or complete audit semantics.
- A new-store form could retain removed operating entity A after the entity list changed to contain only B.
- The formal contract and release note did not fully state the success envelope, replay behavior, or status-independent current-store guard.

### Evidence

- `PlatformTenantStructureRepository` already used `FOR KEY SHARE` for store assignment and `FOR UPDATE` for deletion, but no test observed a real blocked PostgreSQL backend.
- The Vue watcher changed `selectedOperatingEntityId` after A disappeared but updated `storeForm.operatingEntityId` only when it was empty, leaving the non-empty stale A value intact.
- The existing 409 assertion only checked `deleted_at is null`; the existing audit assertion only checked count `1`.

### Root Cause

The backend behavior was implemented but under-specified by tests. The functional Vue defect came from treating a non-empty store-form entity id as valid without checking it against the current active entity list.

### Affected Files

- `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`
- `src/components/platform/PlatformTenantStructurePanel.vue`
- `src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts`
- `src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts`
- `src/api/platformApi.spec.ts`
- `docs/api/PLATFORM_TENANT_API_CONTRACT.md`
- `docs/release-notes/2026-07-17-operating-entity-delete.md`

### Fix Plan And Result

1. Added two real-PostgreSQL concurrency tests with two independent `TransactionTemplate` transactions.
   - Latches control the lock-holder commit point.
   - Each transaction records `pg_backend_pid()`.
   - `pg_stat_activity.wait_event_type = 'Lock'` plus `pg_blocking_pids(...)` proves the waiter is actually blocked.
   - No fixed sleep is used.
   - Assignment-first uses the production repository key-share lock and production service create path; deletion waits, then returns `OPERATING_ENTITY_HAS_STORES` after assignment commits.
   - Delete-first uses the production delete service/update lock; creation waits, then returns `REQUEST_INVALID` after deletion commits and creates no current store.
2. Strengthened delete integration assertions for 409 and repeated DELETE state immutability, inactive current-store blocking, actor/source, and every operating-entity audit snapshot field.
3. Closed/reset the new-store form when its selected entity is no longer an active entity.
4. Added component coverage for stale-form removal and inactive-store delete visibility, page cancellation saving state, and API client URL/method/error behavior.
5. Expanded the formal contract and release note for HTTP 200, `PlatformOperatingEntityResponse`, archived/deleted/deletedAt, non-replaying repeat behavior, additive compatibility, all current-store statuses, and local validation matcher wiring.

### Remaining Risk

No known correctness gap remains in the requested scope. The concurrency tests are PostgreSQL-specific by design and use bounded latch/future timeouts only as failure guards. They do not use timing sleeps as coordination.

## TDD RED Evidence

### Vue stale form

Command:

```powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts
```

Before the production watcher fix: 5 tests ran, 1 failed and 4 passed. The expected failure was `closes a new-store form when its operating entity disappears`; the form still existed after A was removed.

### PostgreSQL lock mutation proof

The production lock was already present, so the completed concurrency tests were mutation-checked. `FOR KEY SHARE` was temporarily removed, the following command was run, and the production line was immediately restored without entering the final diff:

```powershell
mvn -q "-Dtest=PlatformTenantApiIntegrationTest#storeAssignmentKeyShareLockMakesDeleteWaitAndRecheckCurrentStores+operatingEntityUpdateLockMakesStoreCreationWaitAndRejectsItAfterDeleteCommits" test
```

Result with the intentional mutation: 2 tests ran and both failed.

- Assignment-first failed because the delete backend was idle instead of blocked by the assignment backend.
- Delete-first failed because store creation did not return the required `REQUEST_INVALID` after commit.

This proves both tests detect loss of the production key-share lock rather than passing from thread timing alone.

## TDD GREEN Evidence

After restoring the production lock, the same two-test command passed 2/2. The focused delete/concurrency command passed 3/3, and the complete `PlatformTenantApiIntegrationTest` passed 23/23.

Final frontend command:

```powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts src/api/platformApi.spec.ts
```

Result: 3 files, 11 tests, 0 failures.

## Final Verification

| Command | Result |
| --- | --- |
| `npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts src/api/platformApi.spec.ts` | Passed: 3 files, 11 tests. |
| `npm run build` | Passed: TypeScript check and Vite production build; 363 modules transformed. |
| `mvn -q "-Dtest=PlatformTenantApiIntegrationTest,PlatformTenantLocalRuntimeSecurityTest,AuthLoginUiValidationTest" test` | Passed: 47 tests total; 23 + 8 + 16, zero failures/errors/skips. |
| `mvn -q -DskipTests package` | Passed. |
| `git diff --check` | Passed; no whitespace errors. Only existing LF-to-CRLF conversion warnings were printed. |
| `git status --short` before implementation commit | Only the seven intended implementation/test/document files. |

## TDD Review Report

| Scenario | Test Exists | Result | Notes |
| --- | --- | --- | --- |
| Assignment key-share lock first | Yes | Pass | Observable PostgreSQL blocker and post-commit 409. |
| Delete update lock first | Yes | Pass | Observable PostgreSQL blocker and post-commit create rejection/no store. |
| 409 no mutation | Yes | Pass | Status, deleted_at, updated_at, version, audit count unchanged. |
| Repeated DELETE | Yes | Pass | Archived status, deleted_at, updated_at, version, audit count unchanged. |
| Successful audit semantics | Yes | Pass | Actor, actor type, source, and complete previous/deleted JSON snapshots. |
| Inactive current store blocks/hides delete | Yes | Pass | Backend and Vue component coverage. |
| Stale new-store form | Yes | Pass | Real component behavior test; A removed while B remains closes form. |
| Cancel saving state | Yes | Pass | Cancel leaves the child action enabled and does not call delete. |
| API client behavior | Yes | Pass | Encoded segments, DELETE, credentials, and `PlatformApiError` wrapping. |
| Permission/tenant/local runtime | Yes | Pass | Existing platform-admin, cross-tenant, and 8 local runtime tests remain green. |

No required test is missing. No P2 item was deferred.

## API Review Report

### Endpoint

`DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}`.

### Contract Check

HTTP 200 and the existing explicit `PlatformOperatingEntityResponse` envelope are documented. Archived status, `deleted = true`, non-null `deletedAt`, HTTP 409, repeat 404 behavior, and additive compatibility match implementation and tests.

### Permission Check

The existing `platform_admin` plus `platform.tenant.manage` enforcement is unchanged. The local validation request matcher remains narrow to the exact DELETE resource route and still relies on the configured actor.

### Error Mapping And Replay

`OPERATING_ENTITY_HAS_STORES` remains HTTP 409. Invalid/deleted/repeated/cross-tenant entity ids remain `OPERATING_ENTITY_NOT_FOUND`. Successful deletion is intentionally not replayed as 200; a repeat is 404 with no state or audit mutation.

### Compatibility

The endpoint remains additive. No request/response DTO, schema, migration, dependency, permission, or existing endpoint behavior changed.

### Missing Items

None.

## Code Review Report

### Scope

One minimal Vue production change, test coverage, and documentation only. No module boundary or persistence implementation change remains in the final diff.

### P0 - Must Fix

None.

### P1 - Should Fix

None.

### P2 - Improvement

None required for this brief.

### Test Coverage

Concurrency, conflict, replay, audit, tenant/permission, local runtime, component behavior, API client, production frontend build, and backend package gates are green with fresh evidence above.

### Final Decision

APPROVE

## Release Notes Review

- New/fixed behavior is separated from compatibility, migration, permission, risk, validation, and rollback details.
- Every non-deleted current store is explicitly documented as blocking regardless of status.
- No migration, dependency, App Gate entitlement, permission, external runtime configuration, or state-machine change is claimed.
- Rollback remains removal of the additive UI/endpoint; existing soft-deleted history requires no schema/data rollback.

## Deferred P2

None.
