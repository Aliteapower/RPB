---
name: issue-to-pr
description: Use when converting an RPB Linear issue, ticket, product request, or task description into a PR-ready development plan with scope, tests, API contract, migration assessment, and PR description draft.
---

# Issue to PR Skill

## Purpose

Convert an RPB issue or task into a PR-ready plan with clear business goal, technical scope, exclusions, tests, contract needs, migration needs, and PR description.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill before implementation when starting from a Linear issue, task description, bug report, feature request, or planning note.

## Inputs

- Issue title and description.
- Acceptance criteria.
- Related docs, screenshots, or reproduction steps.
- Existing API, database, backend, frontend, or governance documents.
- Known constraints, out-of-scope items, and release expectations.

## Required checks

- Identify the business goal in operational RPB terms.
- Identify affected workflow: Reservation, Queue, Walk-in, Seating, Cleaning, App Gate, local runtime, or shared platform.
- Define technical scope by backend, API, database, frontend, docs, and tests.
- Define explicit out-of-scope items.
- Determine whether API contract is needed.
- Determine whether DB migration is needed.
- Determine App Gate and permission impact.
- Determine tenant and store isolation impact.
- Convert acceptance criteria into implementation checklist items.
- Convert risk scenarios into test checklist items.
- Keep the plan PR-sized and avoid unrelated refactors.

## Output format

```markdown
# Issue to PR Plan
## Issue Summary
## Business Goal
## Technical Scope
## Out of Scope
## Implementation Checklist
## Test Checklist
## API Contract Needed
## DB Migration Needed
## PR Description Draft
```

## Stop conditions

- Stop if the issue lacks enough information to define scope or acceptance criteria.
- Stop if the request crosses multiple PR-sized deliverables without a split plan.
- Stop if API, migration, permission, or tenant impact is unknown.
- Stop if implementation would begin before the contract and test checklist are defined.

## Example prompt

```text
Use the RPB issue-to-pr skill to convert this Linear issue about walk-in direct seating into a PR-ready plan.
```
