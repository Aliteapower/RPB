# Cleaning API Contract Checklist V1

## 1. Phase Boundary

- [x] This round only designs Cleaning API contracts.
- [x] This round splits Start Cleaning and Complete Cleaning.
- [x] This round does not implement Controller.
- [x] This round does not implement REST endpoints.
- [x] This round does not create API DTO Java classes.
- [x] This round does not modify Application Service.
- [x] This round does not modify Repository, Mapper, or Entity.
- [x] This round does not create Vue UI.
- [x] This round does not create migration.
- [x] This round does not create SQL.
- [x] This round does not connect to database.
- [x] This round does not insert seed data or mock runtime data.

## 2. Endpoint Boundary

- [x] Start Cleaning endpoint is designed.
- [x] Complete Cleaning endpoint is designed.
- [x] Start Cleaning uses `POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start`.
- [x] Complete Cleaning uses `POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete`.
- [x] Start and Complete are separate store staff actions.
- [x] Endpoint paths are contract recommendations only in this round.

## 3. Resource Derivation

- [x] Start Cleaning derives resource from `seatingId`.
- [x] Complete Cleaning derives resource from `cleaningId`.
- [x] Request body does not require `tableId`.
- [x] Request body does not require `tableGroupId`.
- [x] Request body must not accept duplicated resource input as trusted source.

## 4. Auth / RBAC / Scope

- [x] JWT authentication or current server-side actor context is required.
- [x] Tenant scope comes from authenticated context.
- [x] Store scope comes from path plus server-side validation.
- [x] `tenantId` is not trusted from request body.
- [x] Allowed roles are `tenant_admin`, `store_manager`, and `store_staff`.
- [x] `customer` role is forbidden.
- [x] `integration_app` role is forbidden.
- [x] Start Cleaning permission is `cleaning.start`.
- [x] Complete Cleaning permission is `cleaning.complete`.
- [x] Cross-Tenant and cross-Store references are rejected.

## 5. Request Contract

- [x] Start Cleaning request body is limited to `reasonCode` and `note`.
- [x] Complete Cleaning request body is limited to `reasonCode` and `note`.
- [x] `Idempotency-Key` header is required.
- [x] `storeId` comes from path.
- [x] `seatingId` comes from Start path.
- [x] `cleaningId` comes from Complete path.
- [x] Actor identity comes from authenticated context.

## 6. Response Contract

- [x] Start Cleaning success response includes `cleaningId`.
- [x] Start Cleaning success response includes `seatingId`.
- [x] Both success responses include resource projection.
- [x] Start Cleaning exposes final `cleaningStatus = cleaning`.
- [x] Start Cleaning exposes final `tableStatus = cleaning`.
- [x] Complete Cleaning exposes final `cleaningStatus = released`.
- [x] Complete Cleaning exposes final `tableStatus = available`.
- [x] Response is API DTO contract, not Domain Object.
- [x] Response does not expose Entity.
- [x] Response does not expose Repository or Mapper internals.
- [x] Response does not expose full AuditLog metadata.

## 7. Error / I18n Contract

- [x] Common error envelope is defined.
- [x] Stable public error codes are defined.
- [x] Every error code has a suggested i18n `messageKey`.
- [x] `details` object convention is defined.
- [x] HTTP status mapping is defined.
- [x] API responses do not hardcode display text.
- [x] Store locale and i18n resolution remain later responsibilities.

## 8. Idempotency Contract

- [x] `Idempotency-Key` header is required.
- [x] Start Cleaning action is `start_cleaning`.
- [x] Complete Cleaning action is `complete_cleaning`.
- [x] Request hash rule is defined for Start Cleaning.
- [x] Request hash rule is defined for Complete Cleaning.
- [x] Completed replay returns prior success response with `replayed = true`.
- [x] In-progress behavior returns retry-later conflict.
- [x] Failed behavior requires a new key.
- [x] Same key with different hash returns conflict.
- [x] Missing key returns validation error.
- [x] Replayed Complete Cleaning must not duplicate events, transitions, audit, or table release.

## 9. Test Contract

- [x] Start Cleaning success with table resource is covered.
- [x] Start Cleaning success with TableGroup resource is covered.
- [x] Start Cleaning missing idempotency key is covered.
- [x] Start Cleaning seating/resource/table/group failures are covered.
- [x] Start Cleaning auth and scope failures are covered.
- [x] Start Cleaning idempotency cases are covered.
- [x] Complete Cleaning success with table resource is covered.
- [x] Complete Cleaning success with TableGroup resource is covered.
- [x] Complete Cleaning missing idempotency key is covered.
- [x] Complete Cleaning cleaning/table/group failures are covered.
- [x] Complete Cleaning auth and scope failures are covered.
- [x] Complete Cleaning idempotency cases are covered.
- [x] No test code is written in this round.

## 10. Business Boundary

- [x] Cleaning is kept separate from Turnover.
- [x] Cleaning does not design Reservation API.
- [x] Cleaning does not design Queue API.
- [x] Cleaning does not design Turnover API.
- [x] Cleaning does not design Table management API.
- [x] Cleaning does not design Payment, POS, Marketing, or Member.
- [x] Cleaning does not change Seating source.
- [x] Cleaning response does not expose Turnover BI.

## 11. Output Documents

- [x] `docs/api/CLEANING_COMPLETE_API_CONTRACT.md` is created.
- [x] `docs/api/CLEANING_API_ERROR_CONTRACT.md` is created.
- [x] `docs/api/CLEANING_API_IDEMPOTENCY_CONTRACT.md` is created.
- [x] `docs/api/CLEANING_API_CONTRACT_CHECKLIST.md` is created.

## 12. Out-of-Scope Confirmation

- [x] Controller created: No.
- [x] API DTO Java class created: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Application Service changed: No.
- [x] Repository changed: No.
- [x] Mapper changed: No.
- [x] Entity changed: No.
- [x] Migration changed: No.
- [x] SQL created: No.
- [x] Reservation API designed: No.
- [x] Queue API designed: No.
- [x] Turnover API designed: No.
