---
name: changelog
description: Use when generating an RPB changelog entry from git diff, implementation reports, PR summaries, release notes, validation reports, migrations, security changes, or internal documentation changes.
---

# Changelog Skill

## Purpose

Create a structured RPB changelog entry that separates product-visible, migration, security, and internal changes.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill after implementation, before release notes, before PR finalization, or when summarizing a diff or implementation report for CHANGELOG inclusion.

## Inputs

- Git diff, changed file list, implementation report, or PR summary.
- API contract changes.
- Migration notes.
- Permission and security notes.
- User-visible behavior notes.
- Internal-only refactor or documentation notes.

## Required checks

- Classify new capabilities under Added.
- Classify behavior changes under Changed.
- Classify bug fixes under Fixed.
- Classify removals under Removed.
- Classify schema or data changes under Migration.
- Classify App Gate, tenant isolation, local runtime, audit, and permission changes under Security when relevant.
- Classify docs, tests, refactors, and internal process changes under Internal when not product-visible.
- Mention Reservation, Queue, Walk-in, Seating, or Cleaning workflow impact when relevant.
- Avoid claiming test or release readiness unless verification evidence is available.

## Output format

```markdown
# Changelog Entry
## Added
## Changed
## Fixed
## Removed
## Migration
## Security
## Internal
```

## Stop conditions

- Stop if there is no diff, report, PR summary, or reliable change source.
- Stop if migration or security impact cannot be classified.
- Stop if the entry would mix unrelated tasks into one release item.
- Stop if the source indicates code, API, database, or config changes that were outside the approved scope.

## Example prompt

```text
Use the RPB changelog skill to generate a changelog entry from the reservation create API implementation report.
```
