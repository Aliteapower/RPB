# API Contract Checklist V1

## Purpose

This checklist validates that the API design round remains limited to WalkIn Direct Seating API contract design.

No Controller, API DTO Java class, endpoint implementation, Vue UI, Application Service change, Repository change, Mapper change, Entity change, migration, SQL, seed data, mock runtime data, Docker, CI/CD, or production configuration is created in this round.

## Read Inputs

- [x] `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- [x] `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- [x] `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- [x] `docs/backend/VERTICAL_SLICE_CHECKLIST.md`
- [x] `docs/backend/DOMAIN_MODEL_DESIGN.md`
- [x] `docs/backend/STATE_MACHINE_DESIGN.md`
- [x] `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- [x] `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- [x] `docs/backend/REPOSITORY_PORT_DESIGN.md`
- [x] `docs/governance/BUSINESS_RULES.md`
- [x] `docs/governance/DATA_STANDARD.md`
- [x] `docs/architecture/ARCHITECTURE.md`
- [x] `docs/skills/reservation-system/SKILL.md`

## Previous Round Confirmation

- [x] `mvn test` passed.
- [x] 56 tests.
- [x] 0 failures.
- [x] 0 errors.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Reservation implemented: No.
- [x] Queue implemented: No.
- [x] Cleaning implemented: No.
- [x] Turnover implemented: No.
- [x] Migration changed: No.

## API Scope

- [x] Only WalkIn Direct Seating API is designed.
- [x] Endpoint is documented as `POST /api/v1/stores/{storeId}/walk-ins/direct-seating`.
- [x] The endpoint is documented as a contract recommendation only.
- [x] No Reservation API is designed.
- [x] No Queue API is designed.
- [x] No Cleaning API is designed.
- [x] No Turnover API is designed.
- [x] No Customer search API is designed.
- [x] No Table management API is designed.

## Auth / RBAC / Scope

- [x] JWT authentication is required.
- [x] Tenant scope comes from JWT or server context.
- [x] Store scope comes from path `storeId` and must be validated under Tenant scope.
- [x] Allowed roles are documented: `tenant_admin`, `store_manager`, `store_staff`.
- [x] Forbidden roles are documented: `customer`, `integration_app`.
- [x] Permission key is documented: `walkin.direct_seating.create`.
- [x] Cross-Tenant references are forbidden.
- [x] Cross-Store resource references are forbidden.
- [x] Request body `tenantId` is not trusted or accepted as source of truth.

## Request Contract

- [x] `partySize` is required and must be greater than 0.
- [x] `customerId` is optional.
- [x] `customerName` is optional.
- [x] `customerNickname` is optional.
- [x] `phoneE164` is optional.
- [x] `phoneE164` must be E.164 if present.
- [x] `tableId` is optional.
- [x] `tableGroupId` is optional.
- [x] `tableId` and `tableGroupId` are mutually exclusive.
- [x] `overrideReasonCode` is optional.
- [x] `overrideNote` is optional.
- [x] Manual non-recommended resource selection requires `overrideReasonCode` or `overrideNote`.

## Response Contract

- [x] Success response shape is documented.
- [x] Completed replay response is documented.
- [x] Response is described as API DTO, not Domain Object.
- [x] Entity exposure is forbidden.
- [x] Repository and Mapper details are not exposed.
- [x] Full AuditLog metadata is not exposed.
- [x] Event exposure is limited to short event codes.

## Error Contract

- [x] Common error envelope is documented.
- [x] Stable error code list is documented.
- [x] `messageKey` convention is documented.
- [x] `details` object convention is documented.
- [x] HTTP status mapping is documented.
- [x] i18n rule is documented.
- [x] Display text is not hardcoded in error responses.
- [x] Required error codes are covered.

## Idempotency Contract

- [x] `Idempotency-Key` header is required.
- [x] Request hash rule is documented.
- [x] Completed replay behavior is documented.
- [x] In-progress behavior is documented.
- [x] Failed behavior requiring a new key is documented.
- [x] Conflict behavior for same key with different hash is documented.
- [x] Missing key behavior is documented.
- [x] V1 does not introduce `retryable_failure`.

## Test Contract

- [x] Success with no-phone customer is covered.
- [x] Success with specified table is covered.
- [x] Success with existing table group is covered.
- [x] Success with auto-selected table is covered.
- [x] Invalid party size is covered.
- [x] Invalid phone is covered.
- [x] `tableId` and `tableGroupId` both present is covered.
- [x] Table locked is covered.
- [x] Table inactive is covered.
- [x] Capacity insufficient is covered.
- [x] Invalid table group is covered.
- [x] Override missing is covered.
- [x] Idempotency completed replay is covered.
- [x] Idempotency in progress is covered.
- [x] Idempotency failed requires new key is covered.
- [x] Idempotency hash conflict is covered.
- [x] Forbidden role is covered.
- [x] Store scope mismatch is covered.

## Forbidden Artifact Check

- [x] Controller implemented: No.
- [x] API DTO Java class created: No.
- [x] REST endpoint implemented: No.
- [x] Vue UI created: No.
- [x] Application Service changed: No.
- [x] Repository changed: No.
- [x] Mapper changed: No.
- [x] Entity changed: No.
- [x] Migration changed: No.
- [x] SQL created: No.
- [x] Seed data inserted: No.
- [x] Mock runtime data inserted: No.
- [x] Docker changed: No.
- [x] CI/CD changed: No.
- [x] Production configuration changed: No.

## Final Gate

- [x] Created/updated files are limited to `docs/api`.
- [x] API contract is clear.
- [x] Auth / Scope / RBAC contract is clear.
- [x] Request / Response / Error contract is clear.
- [x] Idempotency contract is clear.
- [x] i18n `messageKey` contract is clear.
- [x] Test contract is clear.
- [x] No implementation artifact is created.

Next allowed round:

```text
WalkIn Direct Seating API Implementation
```

That later round should still avoid Vue UI and should not expand into Reservation, Queue, Cleaning, or Turnover APIs unless separately approved.
