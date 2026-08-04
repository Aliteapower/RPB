# Default Queue Group Provisioning Design

## Context

The LCD tenant can open the staff reservation and walk-in queue pages, but both queue commands return `QUEUE_GROUP_NOT_FOUND`. The App Gate has already admitted the requests, so the failure is not caused by a missing `reservation_queue` entitlement or disabled store entry.

Both command paths resolve an active `QueueGroup` from the authenticated `Tenant + Store` scope and the party size. The platform tenant onboarding flow creates a default Store without creating QueueGroups, and the platform Store creation flow has the same omission. A newly created Store therefore has no rows in `queue_groups`, even though confirmed business rule BR-024 requires the default Store-scoped groups `1-2`, `3-4`, `5-6`, and `7+`.

## Goal

Provision the four default QueueGroups for every newly created Store and repair existing Stores that have never had QueueGroup configuration, including the LCD Store, without overwriting intentional custom configuration.

## Scope

This change covers Store onboarding orchestration, QueueGroup provisioning persistence, an idempotent Flyway data migration, database-backed integration tests, a troubleshooting report, and release notes.

The Queue and WalkIn API paths, request and response contracts, App Gate entitlement rules, permissions, QueueTicket state machine, and frontend UI remain unchanged.

## Options Considered

### Application provisioning plus historical backfill

Create one Queue-owned provisioning capability and invoke it after both default-Store creation and explicit Store creation. Add a Flyway data migration that inserts defaults only for existing Stores with no QueueGroup history.

This is the selected approach. It keeps new-Store behavior explicit and testable, repairs existing affected Stores during deployment, and avoids changing runtime queue commands.

### Database trigger on Store insert

A trigger could create QueueGroups for every Store insert. This has broad coverage but hides onboarding behavior in the database, complicates application tests, and makes future queue-policy customization harder to reason about.

### Lazy provisioning during queue commands

The reservation and walk-in services could create defaults when selection fails. This would turn an operational command into configuration mutation and could silently undo intentional Store configuration. It is rejected.

## Default Queue Groups

Each newly provisioned Store receives these active groups:

| Code | Minimum | Maximum | Display key | Sort order |
| --- | ---: | ---: | --- | ---: |
| `1-2` | 1 | 2 | `queue.group.1_2` | 1 |
| `3-4` | 3 | 4 | `queue.group.3_4` | 2 |
| `5-6` | 5 | 6 | `queue.group.5_6` | 3 |
| `7+` | 7 | null | `queue.group.7_plus` | 4 |

All rows use the Store's `tenant_id` and `store_id`, status `active`, generated UUID identifiers, and existing timestamp/version defaults.

## Application Design

Add a focused Queue application service named `DefaultQueueGroupProvisioningService`. It accepts a `StoreScope` and delegates to a Queue persistence component that inserts the four defaults idempotently.

`PlatformTenantService.createTenant` invokes the provisioner after `ensureDefaultStore` returns the default Store identifier and before the tenant administrator account is created. `PlatformTenantStructureService.createStore` invokes the same provisioner immediately after the Store insert and before account, host binding, and audit work.

Both platform operations are already transactional. A provisioning failure therefore rolls back the Store or tenant creation instead of leaving another partially initialized Store.

The provisioner belongs to the Queue module because QueueGroup policy and persistence are Queue concerns. Platform onboarding only coordinates the Store identifier and does not duplicate queue ranges or SQL.

## Persistence Design

The provisioning repository performs one set-based insert for the four canonical rows. It is safe to repeat for the same Store by relying on the existing active unique index over `tenant_id + store_id + group_code` and `ON CONFLICT ... DO NOTHING`.

The application service is called only for newly created Stores. It does not run from queue commands, Store update, tenant restore, or ordinary reads.

## Migration Design

Add `V046__backfill_default_queue_groups.sql`.

The migration selects non-deleted Stores for which no QueueGroup row exists at all for the same `tenant_id + store_id`, including deleted or inactive history. It inserts all four defaults in one set-based statement.

The all-history condition is intentional:

- a Store with zero QueueGroup rows is an onboarding defect and is repaired;
- a Store with active custom groups is preserved;
- a Store with inactive or soft-deleted groups is treated as intentionally configured and is not recreated;
- a partially configured Store is preserved to avoid introducing overlapping party-size ranges.

The migration contains no LCD-specific identifier and repairs every Store affected by the same historical defect. Re-running or deploying into a database where defaults already exist produces no duplicates.

## Security And Tenant Isolation

Provisioning requires an explicit `StoreScope`. Every insert carries both `tenant_id` and `store_id`, and the existing composite Store foreign key prevents cross-tenant Store references.

No permission or App Gate rule changes are needed. The current `reservation_queue` entitlement remains the authority for whether staff may use queue features; provisioning only supplies required Store configuration.

## Testing

Test-driven implementation must cover:

- creating a single-Store tenant persists exactly four canonical QueueGroups for its default Store;
- creating an additional Store persists exactly four canonical QueueGroups for that Store;
- provisioning the same Store twice does not create duplicates;
- tenant and Store scope values are preserved on every inserted group;
- the migration backfills a Store with no QueueGroup history;
- the migration does not modify a Store with active custom groups;
- the migration does not recreate groups for a Store with inactive or soft-deleted history;
- the migration remains idempotent;
- a party size of 2 resolves `1-2`, and a party size of 7 resolves `7+` after provisioning.

Focused unit/integration tests and the relevant platform, reservation-queue, and walk-in queue regressions must pass. Database-backed validation must use the PostgreSQL runtime referenced by `target/local-postgres-current.txt`.

## Deployment And Rollback

Deploy the migration and application in the same release. Flyway backfills existing affected Stores before the new application serves traffic; the application then protects all future Store creation paths.

Application rollback removes automatic provisioning for future Stores but leaves valid QueueGroup rows in place. The data migration is additive and should not be reversed automatically because queue tickets may reference the new groups after deployment. If rollback is required before any queue use, operators may remove only provably unused backfilled rows through an audited manual procedure.

## Success Criteria

- LCD walk-in取号 and reservation到店排队 no longer return `QUEUE_GROUP_NOT_FOUND` after deployment.
- New tenants and newly added Stores can queue parties across all four default size bands without manual QueueGroup setup.
- Existing Store-specific QueueGroup configuration is not overwritten or supplemented by the historical backfill.
- No API, frontend, permission, or App Gate contract changes are introduced.
