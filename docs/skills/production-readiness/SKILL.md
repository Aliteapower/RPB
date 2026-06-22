---
name: production-readiness
description: Use when checking RPB deployment readiness, release gates, migration safety, rollback plans, environment variables, permissions, local runtime security, logging, audit, error codes, performance, or data compatibility.
---

# Production Readiness Skill

## Purpose

Assess whether an RPB change is ready for production deployment and identify blockers before release.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill before deployment-related work, release approval, production rollout, or final handoff for changes that affect runtime behavior, data, permissions, APIs, or staff workflows.

## Inputs

- Release notes or PR summary.
- Changed files or implementation report.
- Test and validation output.
- Migration review.
- Rollback plan.
- Environment variable list.
- Permission and App Gate registration notes.
- Logging, audit, and error code notes.
- Performance and data compatibility notes.

## Required checks

- Tests are passed with current evidence.
- Migration is safe, ordered, and reviewed.
- Rollback is explicit and actionable.
- Environment variables are complete.
- Permissions are registered.
- Local runtime is secure.
- Logs are sufficient for support and troubleshooting.
- Audit is written for critical operations.
- Error codes are stable and documented.
- Performance risk is understood.
- Data compatibility risk is understood.
- Tenant and store isolation remain enforced.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows remain operational.

## Output format

```markdown
# Production Readiness Report
## Checklist
## Blocking Issues
## Non-blocking Issues
## Rollback Plan
## Decision
GO / NO_GO
```

## Stop conditions

- Stop with `NO_GO` if tests, migration safety, rollback, permissions, local runtime security, tenant isolation, or data compatibility are unresolved.
- Stop if production readiness depends on unverified assumptions.
- Stop if logs, audit, or stable error codes are missing for critical operations.
- Stop if deployment would include unrelated code, database, API, or config changes.

## Example prompt

```text
Use the RPB production-readiness skill to decide whether the cleaning complete API release is GO or NO_GO.
```
