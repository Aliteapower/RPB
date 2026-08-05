# PayNow Payment Product Line Foundation

## Summary

Adds the RPB-native `payment` product-line foundation for PayNow quick payment.

## Included

- Payment product-line seed, App Gate entry permissions, and product-line pricing seed.
- Payment operational schema for profiles, intents, sessions, counters, proofs, OCR results, verifications, and events.
- PayNow method profile service and tenant-admin profile API.
- PayNow QR payload builder based on the business-flow value from `D:\payment_runtime`.
- Quick Pay intent/session creation service with idempotency replay and transaction-scoped intent numbering.
- Payment intent API foundation at `POST /api/v1/stores/{storeId}/payments/intents`.

## Not Included

- OCR implementation.
- POS adapter.
- Reservation deposit adapter.
- Bank reconciliation.
- Frontend payment workbench.
- Sidecar runtime integration with `D:\payment_runtime`.

## Review

- API review: store-scoped `/api/v1` endpoints use explicit request/response DTOs, App Gate annotations, and stable error mapping.
- Database review: payment operational tables include tenant/store scope, FK coverage, uniqueness, status checks, monetary constraints, and indexes for Phase 1 lookup paths.
- TDD review: App Gate, migration, profile validation, QR payload generation, intent creation, and controller App Gate contracts were added with focused tests.
- Code review: controllers call application services, persistence stays behind repository ports, and Quick Pay intent numbering is serialized with a transaction-scoped advisory lock.

## Verification

- PASS: `mvn '-Dtest=AppGateServiceTest,PaymentMigrationTest,PayNowQrPayloadBuilderTest,PaymentMethodProfileServiceTest,PaymentIntentServiceTest,PaymentProfileControllerTest,PaymentIntentControllerTest' test`
- PASS after constructor fix: `mvn -Dtest=PlatformTenantApiIntegrationTest test`
- PASS after constructor fix: `mvn '-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest' test`
- FULL SUITE ATTEMPTED: `mvn test` initially failed. A payment Spring bean constructor-selection failure was fixed and verified with `PlatformTenantApiIntegrationTest`. Existing frontend validation failures were also observed in:
  - `FrontendHardcodedChineseMigrationValidationTest`
  - `PlatformGroupTenantOnboardingUiValidationTest`
  - `ReservationArrivedToQueueUiImplementationValidationTest`
  - `ReservationShareInfoUiValidationTest`
