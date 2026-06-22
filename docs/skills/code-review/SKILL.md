---
name: code-review
description: Use when reviewing completed RPB code changes, implementation reports, or diffs before final delivery, especially Java backend, Vue frontend, App Gate, multi-tenant SaaS, API contract, TDD, reservation, queue, walk-in, seating, or cleaning work.
---

# Code Review Skill

## Purpose

Review completed RPB development work for correctness, maintainability, tenant safety, and delivery readiness.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate authorization, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill after implementation and before the final response, commit, PR, or release handoff.

Use it for backend, frontend, persistence, App Gate, API, reservation workflow, queue workflow, walk-in workflow, seating workflow, cleaning workflow, or cross-module changes.

## Inputs

- User request or issue scope.
- Changed files, implementation report, or diff.
- Relevant API contract documents.
- Relevant database or persistence documents.
- Relevant frontend UI contract or validation report.
- Related tests and test output.
- Existing RPB governance and architecture documents when module boundaries are involved.

## Required checks

- Confirm the change stays within the requested scope.
- Check module boundaries and ownership.
- Check DDD / application / domain / infrastructure layering.
- Verify Controllers do not directly access Repositories.
- Flag Services that are too large or mix orchestration, domain rules, persistence, and presentation mapping.
- Check transaction boundaries and propagation.
- Check exception handling and stable error mapping.
- Check permission validation.
- Check App Gate annotation or equivalent App Gate enforcement.
- Check tenant isolation and required `tenant_id` filtering.
- Check `store_id` handling where store-scoped data is involved.
- Check idempotency for repeated commands, retries, and replay.
- Check audit logging for critical operations.
- Check concurrency risks for reservation, queue, seating, table lock, and cleaning flows.
- Check PostgreSQL persistence assumptions, constraints, and query safety when relevant.
- Check Vue state, API integration, i18n display, and error handling when frontend files changed.
- Check test coverage for normal path, failure path, permission failure, duplicate submission, cross-tenant access, and App Gate denial where applicable.

## Output format

```markdown
# Code Review Report
## Scope
## P0 - Must Fix
## P1 - Should Fix
## P2 - Improvement
## Test Coverage
## Final Decision
APPROVE / APPROVE_WITH_COMMENTS / REQUEST_CHANGES
```

## Stop conditions

- Stop and request changes if tenant isolation, App Gate enforcement, transaction safety, or data integrity is unclear.
- Stop if the implementation changes API behavior without an API contract update.
- Stop if persistence or migration behavior is affected without database review.
- Stop if tests are missing for high-risk behavior.
- Do not approve code that bypasses module boundaries or directly couples Controller to Repository.

## Example prompt

```text
Use the RPB code-review skill to review the completed reservation check-in changes and decide whether this PR should be approved.
```
