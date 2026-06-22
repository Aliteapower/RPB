---
name: tdd-review
description: Use when checking whether RPB development followed TDD, before implementation planning, after implementation, or when reviewing tests for API, App Gate, tenant isolation, local runtime security, or repeated submissions.
---

# TDD Review Skill

## Purpose

Confirm that RPB work is driven by meaningful tests and that critical SaaS, App Gate, tenant, API, and reservation workflow risks are covered.

Apply the RPB baseline: Java backend tests, Vue frontend tests where applicable, PostgreSQL persistence assumptions, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill before implementation to define required tests and after implementation to verify coverage.

Use it for new features, bug fixes, API changes, permission changes, local runtime security changes, persistence behavior, and workflow state transitions.

## Inputs

- User request or issue scope.
- API, application, persistence, or UI contract.
- Test files and test names.
- Test output.
- Implementation report or changed files.
- Known risk scenarios.

## Required checks

- Confirm tests were added before or alongside implementation, not only after completion.
- Check normal path coverage.
- Check permission failure coverage.
- Check nonexistent data coverage.
- Check duplicate submission coverage.
- Check cross-tenant access coverage.
- Check App Gate disabled, not entitled, and store off coverage.
- Check local runtime security coverage.
- Check API error code and HTTP status coverage.
- Check idempotency and replay coverage for commands.
- Check workflow state transition coverage for Reservation, Queue, Walk-in, Seating, and Cleaning where relevant.
- Check frontend empty, loading, error, and permission state coverage when UI changed.

## Output format

```markdown
# TDD Review Report
| Scenario | Test Exists | Result | Notes |
|---|---|---|---|
## Missing Tests
## Required Next Tests
```

## Stop conditions

- Stop before implementation if no test checklist exists for a non-trivial change.
- Stop before final approval if high-risk scenarios lack tests.
- Stop if tests only assert mocks and do not verify meaningful behavior.
- Stop if test output is missing or stale.
- Stop if permission, tenant isolation, or local runtime security is untested for affected paths.

## Example prompt

```text
Use the RPB tdd-review skill to check whether the queue call API has the required tests before final review.
```
