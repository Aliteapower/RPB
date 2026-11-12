# RPB Agent Guidance

## Required Skill Usage

For any non-trivial development task, Codex must check the relevant skill before implementation.

For new product lines or changes to product-entry visibility/synchronization across administrator, employee and customer surfaces, use `docs/skills/rpb-product-entry-sync/SKILL.md`. Reuse synthetic fixtures; do not change real subscriptions for acceptance testing.

Recommended flow:
1. Read related existing docs and code.
2. Use architecture-review before changing module boundaries.
3. Use api-review before adding or changing API.
4. Use database-review before adding migration.
5. Use tdd-review before and after implementation.
6. Use code-review before final response.
7. Use release-note after completed implementation.
8. Use troubleshooting for bugs or failing tests.
9. Use production-readiness before deployment-related work.

## Page Layout Consistency

Before adding or changing an RPB page layout, read `docs/frontend/PAGE_LAYOUT_CONSISTENCY_GUIDE.md` and inspect a comparable page in the same module. Reuse the owning Layout, shared container, panel and action styles; do not introduce arbitrary page-specific widths or floating save bars. Verify adjacent-page alignment and authenticated desktop/mobile viewport behavior, and report any unverified visual scope explicitly. This does not authorize unrelated page refactors or production deployment.

## Documentation-Only Scope

When a task is explicitly documentation-only, keep edits inside the requested documentation paths and do not change business code, database schema, migrations, API behavior, dependency files, or runtime configuration.

## Production Artifact Lifecycle

For future production releases, follow `docs/development/ARTIFACT_RETENTION_OPERATIONS.md` and use `ops/rpb_release_artifacts.py`: create managed staging, check the space budget, prepare immutable version artifacts, make a version-specific rollback snapshot, then register success only after verifying the live frontend/backend. Keep the publication switch and registration under the shared release lock (use the Python functions inside a deployer already holding the lock; do not recursively acquire it through the CLI). Uploads must use `/opt/rpb/staging`, not arbitrary `/home/ubuntu` files. Do not register failed, unfinished, unknown or manually reconstructed material as successful merely to make it eligible for deletion. Retain independent backend/frontend versions, at least two valid full recovery points, and explicit rollback references. Capacity alerts never authorize deleting production data, logs, unregistered originals or required recovery points.

## Local Validation Database Runtime

For local backend, browser, or migration validation, Codex must use the PostgreSQL runtime pointed to by `target/local-postgres-current.txt`.

- Read the pointer file from the workspace that owns the local validation runtime before starting Spring Boot, applying migrations, or running ad hoc SQL.
- Use the pointer `port` with `jdbc:postgresql://127.0.0.1:<port>/postgres?stringtype=unspecified`, username `postgres`, and a blank password.
- Do not silently fall back to `localhost:5432`, `reservation_platform`, a hard-coded old port, the system PostgreSQL service, or another worktree's backend/database.
- If the pointer file is missing or stale, stop and repair/start the local validation runtime using `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md` before claiming runtime verification.
