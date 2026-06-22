---
name: database-review
description: Use when reviewing RPB database design, PostgreSQL migrations, Flyway order, table structure, indexes, constraints, tenant or store isolation, audit fields, soft delete, rollback risk, or persistence contracts.
---

# Database Review Skill

## Purpose

Review RPB database and migration work for PostgreSQL correctness, tenant isolation, operational safety, and rollback risk.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend consumers, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill before creating or changing migration files, after drafting schema design, when reviewing persistence contracts, or when a PR affects table structure, indexes, constraints, or data compatibility.

## Inputs

- Migration file or schema design.
- Table and column definitions.
- Persistence contract or repository query behavior.
- Business ownership scope: platform, tenant, store, or audit.
- Expected query patterns.
- Rollback and data compatibility notes.
- Related API and application contracts.

## Required checks

- Check `tenant_id` presence and enforcement where tenant-scoped data exists.
- Check `store_id` presence where store-scoped operational data exists.
- Check FK coverage and cross-tenant safety.
- Check unique constraints and tenant/store scope in uniqueness.
- Check check constraints for enum-like status and value bounds.
- Check indexes for expected lookup, uniqueness, and foreign key access patterns.
- Check soft delete, archive, inactive, or lifecycle state strategy.
- Check `created_at` and `updated_at`.
- Check audit fields and audit table impact.
- Check status enum values against Reservation, Queue, Table, Seating, and Cleaning state machines.
- Check rollback risk and data compatibility.
- Check Flyway naming, order, and repeatability risk.
- Check PostgreSQL-specific syntax and constraint behavior.

## Output format

```markdown
# Database Review Report
## Migration Scope
## Table Check
## Constraint Check
## Index Check
## Tenant Isolation
## Risk
## Recommendation
```

## Stop conditions

- Stop if tenant or store ownership is unclear.
- Stop if uniqueness, FK, or status constraints can allow cross-tenant or invalid workflow data.
- Stop if migration order or rollback risk is unresolved.
- Stop if API or application behavior depends on schema details not documented in a contract.
- Stop if the task is docs-only and would require creating executable migrations.

## Example prompt

```text
Use the RPB database-review skill to review the proposed PostgreSQL migration for table occupancy and cleaning status.
```
