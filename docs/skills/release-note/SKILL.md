---
name: release-note
description: Use when producing RPB release notes after completed development, implementation reports, PRs, validation rounds, permission changes, migrations, rollback planning, or user-facing workflow changes.
---

# Release Note Skill

## Purpose

Generate concise RPB release notes that describe what changed, what risk exists, and how to roll back.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill after a development round, before PR handoff, before deployment review, or when summarizing a completed vertical slice.

## Inputs

- Version or release identifier.
- Date.
- User request, issue, or PR summary.
- Changed files or implementation report.
- Test and validation output.
- API contract changes.
- Migration or database notes.
- Permission and App Gate notes.
- Rollback constraints.

## Required checks

- Separate new behavior from changed behavior and fixes.
- Identify migration impact, including no-migration confirmation when applicable.
- Identify App Gate permission additions or changes.
- Identify tenant, store, local runtime, and API compatibility risk.
- Include rollback notes that are actionable.
- Keep user-facing descriptions understandable without implementation noise.
- Mention Reservation, Queue, Walk-in, Seating, or Cleaning workflow impact when relevant.

## Output format

```markdown
# Release Notes
## Version / Date
## New
## Changed
## Fixed
## Migration
## Permission
## Risk
## Rollback Notes
```

## Stop conditions

- Stop if version or date is unknown and cannot be inferred from the release context.
- Stop if migration, permission, risk, or rollback impact is unknown.
- Stop if release notes would claim tests passed without current verification evidence.
- Stop if the note hides a breaking API, permission, tenant, or data compatibility change.

## Example prompt

```text
Use the RPB release-note skill to write release notes for the reservation arrived-to-queue implementation round.
```
