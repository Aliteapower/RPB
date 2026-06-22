---
name: pr-review
description: Use when preparing, summarizing, or reviewing an RPB pull request, including scope control, tests, API contracts, release notes, migrations, permissions, frontend impact, rollback notes, and final review decision.
---

# PR Review Skill

## Purpose

Prepare or review an RPB PR so the scope, tests, contracts, risk, and rollback path are clear before handoff.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill before opening a PR, when reviewing a PR description, or before final delivery for a task that produced code or documentation changes.

## Inputs

- Issue or task description.
- Changed files or diff.
- Test output.
- API contract documents.
- Release notes.
- Migration notes.
- Permission and App Gate notes.
- Frontend impact notes.
- Rollback notes.

## Required checks

- Check change scope against the task.
- Check whether changes exceed the task.
- Check whether tests exist and are relevant.
- Check whether API contract exists for API changes.
- Check whether release note exists for deliverable changes.
- Check whether migration is affected.
- Check whether permissions or App Gate registration are affected.
- Check whether frontend behavior is affected.
- Check whether rollback is described.
- Check tenant isolation and local runtime security impact where relevant.
- Check Reservation, Queue, Walk-in, Seating, and Cleaning workflow impact where relevant.

## Output format

```markdown
# PR Review
## Summary
## Changed Files
## Tests
## Risk
## Rollback
## Review Decision
```

## Stop conditions

- Stop if the PR scope cannot be mapped to the issue or task.
- Stop if tests, API contract, release note, migration impact, permission impact, or rollback notes are missing for affected areas.
- Stop if unrelated business code, database, API, or config changes are mixed into a docs-only or narrow task.
- Stop if tenant isolation, App Gate, or local runtime security risk is unreviewed.

## Example prompt

```text
Use the RPB pr-review skill to prepare a PR summary for the queue call API implementation.
```
