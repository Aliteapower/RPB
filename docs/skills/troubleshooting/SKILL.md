---
name: troubleshooting
description: Use when diagnosing RPB bugs, failing tests, startup failures, database failures, API exceptions, local runtime failures, App Gate denials, or unexpected Reservation, Queue, Walk-in, Seating, or Cleaning behavior.
---

# Troubleshooting Skill

## Purpose

Diagnose RPB failures with evidence first, then apply the smallest safe fix only after the root cause is understood.

Apply the RPB baseline: Java backend, PostgreSQL, Vue frontend, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill for bugs, failing tests, startup failures, database failures, API exceptions, UI runtime failures, local runtime security problems, App Gate failures, or unexpected workflow state.

## Inputs

- Symptom description.
- Logs, stack traces, screenshots, or failing command output.
- Failing test file and test output.
- Related API contract, backend contract, database document, or UI contract.
- Relevant files in the call chain.
- Runtime environment notes.

## Required checks

- Start with read-only analysis.
- Do not directly change code before identifying evidence and scope.
- Read logs.
- Read the failing test.
- Identify scope.
- Trace the call chain from UI/API/controller/application/domain/persistence as relevant.
- Compare observed behavior with API contract, App Gate rules, tenant isolation, and workflow state rules.
- Find the most likely root cause.
- Propose a minimal fix before editing.
- Apply only the minimal patch needed for the confirmed cause.
- Run related tests.
- Write a troubleshooting report.

## Output format

```markdown
# Troubleshooting Report
## Symptom
## Evidence
## Root Cause
## Affected Files
## Fix Plan
## Verification
## Remaining Risk
```

## Stop conditions

- Stop before editing if logs, failing tests, or reproduction evidence are unavailable.
- Stop if the failure could be caused by tenant isolation, App Gate, database constraints, or API contract mismatch and those paths have not been checked.
- Stop if the proposed fix changes API behavior, database schema, permissions, or runtime configuration without explicit approval.
- Stop if related tests cannot be run; report the blocker and residual risk.

## Example prompt

```text
Use the RPB troubleshooting skill to diagnose the failing reservation create local runtime security test before changing code.
```
