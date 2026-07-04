# RPB Agent Guidance

## Required Skill Usage

For any non-trivial development task, Codex must check the relevant skill before implementation.

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

## Documentation-Only Scope

When a task is explicitly documentation-only, keep edits inside the requested documentation paths and do not change business code, database schema, migrations, API behavior, dependency files, or runtime configuration.

## Local Validation Database

For local backend, browser, or migration validation, Codex must use the PostgreSQL runtime pointed to by `target/local-postgres-current.txt`.

- Read the pointer file from the workspace that owns the local validation runtime before starting Spring Boot, applying migrations, or running ad hoc SQL.
- Use the pointer `port` with `jdbc:postgresql://127.0.0.1:<port>/postgres?stringtype=unspecified`, username `postgres`, and a blank password.
- Do not silently fall back to `localhost:5432`, `reservation_platform`, a hard-coded old port, the system PostgreSQL service, or another worktree's backend/database.
- If the pointer file is missing or stale, stop and repair/start the local validation runtime using `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md` before claiming runtime verification.
