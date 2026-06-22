---
name: api-review
description: Use when RPB adds, changes, or reviews API endpoints, API contracts, DTOs, error mapping, App Gate permissions, local runtime allowlists, idempotency, replay behavior, or backward compatibility.
---

# API Review Skill

## Purpose

Review RPB API changes before and after implementation so API behavior is contract first, stable, secure, tenant-safe, and compatible.

Apply the RPB baseline: Java backend, PostgreSQL persistence, Vue consumers, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, `/api/v1`, API contract first, and TDD preferred.

## When to use

Use this skill before adding or changing an endpoint, after writing an API contract, after implementing an endpoint, or when reviewing a PR that affects API behavior.

## Inputs

- Endpoint path and HTTP method.
- API contract document.
- Request DTO and Response DTO definitions.
- Error code and HTTP status mapping.
- App Gate permission metadata.
- Local runtime allowlist requirements.
- Idempotency and replay requirements.
- Compatibility notes for Vue frontend or external consumers.
- Related tests and validation output.

## Required checks

- API path uses `/api/v1` and the existing resource naming style.
- Request DTO is explicit, validated, and not a domain entity leak.
- Response DTO is explicit, stable, and not a persistence entity leak.
- Error code is stable and documented.
- HTTP status matches the error and command/query semantics.
- App Gate permission is defined, registered, and enforced.
- Local runtime allowlist is updated only when required and remains narrow.
- Tenant and store scope are explicit in validation and query behavior.
- Idempotency is specified for commands that can be retried or replayed.
- Replay behavior is documented for success, duplicate, stale, and conflicting requests.
- Backward compatibility is preserved or the breaking change is explicitly approved.
- API contract document exists and matches implementation and tests.
- Vue frontend consumers can handle empty, loading, error, and permission-denied responses.

## Output format

```markdown
# API Review Report
## Endpoint
## Contract Check
## Permission Check
## Error Mapping
## Replay / Idempotency
## Compatibility
## Missing Items
```

## Stop conditions

- Stop before implementation if the API contract is missing.
- Stop if the endpoint skips App Gate or tenant isolation.
- Stop if request or response uses internal domain or persistence objects directly.
- Stop if error codes, HTTP statuses, or replay behavior are ambiguous.
- Stop if local runtime access is broadened without an explicit security reason.

## Example prompt

```text
Use the RPB api-review skill to review POST /api/v1/reservations/{id}/check-in before implementation.
```
