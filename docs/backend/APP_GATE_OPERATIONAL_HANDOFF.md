# App Gate Operational Handoff

## Purpose

This document fixes the operational rules for connecting future Reservation Platform business slices to App Gate.

It is based on:

- `docs/backend/APP_GATE_FOUNDATION_V1_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_RUNTIME_VALIDATION_V1_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

This handoff is documentation only. It does not create new apps, permissions, migrations, business flows, API paths, or state-machine transitions.

## Confirmed Runtime Baseline

App Gate Runtime Validation V1 passed.

- 8 runtime acceptance points passed.
- WalkIn, Cleaning, and Reservation focused regression tests passed.
- `mvn test` passed with 256 tests, 0 failures, 0 errors.
- `npm run build` passed.
- `V003` migration was not created.
- Reservation, WalkIn, and Cleaning business state machines were not changed.
- Existing API paths were not changed.
- Production database was not touched.

## Current App Gate Implementation

The current backend implementation uses these App Gate files:

- `src/main/java/com/rpb/reservation/appgate/guard/RequireAppGate.java`
- `src/main/java/com/rpb/reservation/appgate/guard/AppGateInterceptor.java`
- `src/main/java/com/rpb/reservation/appgate/guard/AppGateWebMvcConfiguration.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateDenialAuditService.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateCommandService.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateDecision.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateDenyReason.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/appgate/api/MeAppsController.java`
- `src/main/java/com/rpb/reservation/appgate/api/MeAppsResponse.java`
- `src/main/java/com/rpb/reservation/appgate/api/AppGateApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/appgate/persistence/**`

The current frontend app-entry implementation uses:

- `src/api/meAppsApi.ts`
- `src/types/meApps.ts`
- `src/pages/StoreStaffHomePage.vue`

## App Key Rule

The platform app key for the Reservation Platform operational loop is:

```text
reservation_queue
```

This app key covers the business line:

```text
Reservation
WalkIn
Queue
Seating
Cleaning
Turnover
```

The currently implemented and App Gate protected capability set is:

- Reservation Create.
- Reservation CheckIn.
- WalkIn Direct Seating.
- Cleaning Start and Cleaning Complete inside the Cleaning Complete flow.

Future Queue, Seating, No-show, and Cancellation endpoints that belong to the same reservation and queue business line must use:

```text
app_key = reservation_queue
```

Do not create separate app keys for the same closed loop:

```text
reservation_app
queue_app
walkin_app
cleaning_app
seating_app
```

## Permission Key Rule

Permission keys must be stable RBAC capability keys, not page names, controller names, URL fragments, or temporary labels.

Current keys to preserve:

```text
walkin.direct_seating.create
cleaning.start
cleaning.complete
reservation.create
reservation.check_in
```

Recommended future keys:

```text
reservation.cancel
reservation.no_show
reservation.seat
queue.ticket.create
queue.call
queue.skip
queue.rejoin
seating.create
seating.change_table
cleaning.start
cleaning.complete
```

Naming rules:

- Use lowercase.
- Use dot hierarchy.
- First segment is the business object.
- Second segment is the action or sub-capability.
- Use a verb or stable business action.
- Do not use page names.
- Do not use controller names.
- Do not use URL fragments.
- Do not use temporary pinyin or localized implementation labels.

## Protected Endpoint Rule

Every protected business endpoint in this business line must declare:

- `appKey`
- `permission`
- store scope source
- tenant scope source
- actor source

The current project annotation is:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.create")
```

The current annotation declares `appKey` and `permission`. Store scope is resolved by the guard from the endpoint store scope, currently `{storeId}` in the path. Tenant scope is resolved from the server-side actor. Actor identity and permissions come from `CurrentActorProvider`.

Rules:

- `appKey` must be a platform app key.
- `permission` must be an RBAC permission key.
- `storeId` must come from path, query, or server-side request context and must enter App Gate evaluation before the business handler runs.
- `tenantId` must come from the server-side actor or security context.
- Do not trust `tenantId` from the request body.
- Do not scatter entitlement checks inside business services.
- Do not let App Gate rewrite the business state machine.
- Do not change existing API paths only to fit App Gate.

## Currently Protected Endpoints

| Capability | Method | Path | App key | Permission |
|---|---|---|---|---|
| Reservation Create | `POST` | `/api/v1/stores/{storeId}/reservations` | `reservation_queue` | `reservation.create` |
| Reservation CheckIn | `POST` | `/api/v1/stores/{storeId}/reservations/{reservationId}/check-in` | `reservation_queue` | `reservation.check_in` |
| WalkIn Direct Seating | `POST` | `/api/v1/stores/{storeId}/walk-ins/direct-seating` | `reservation_queue` | `walkin.direct_seating.create` |
| Cleaning Start | `POST` | `/api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start` | `reservation_queue` | `cleaning.start` |
| Cleaning Complete | `POST` | `/api/v1/stores/{storeId}/cleanings/{cleaningId}/complete` | `reservation_queue` | `cleaning.complete` |

## App Gate Decision Order

The operational decision order is:

1. Resolve the protected handler and `@RequireAppGate`.
2. Resolve actor from server-side security context.
3. Resolve tenant scope from actor.
4. Resolve store scope from path, query, or trusted server context.
5. Check platform app exists and is active.
6. Check tenant entitlement exists and is active or valid trial.
7. Check tenant entitlement is not expired or suspended.
8. Check store app setting exists and is enabled.
9. Check actor can access the target store.
10. Check actor has the required permission.
11. Allow the business handler.
12. On deny, return a stable App Gate reject code.
13. On deny, write `app_gate_audit_logs` before the business handler can run.

The current implementation follows this shape with `AppGateInterceptor` resolving handler, actor, and store scope, then `AppGateService` evaluating platform app, tenant entitlement, store setting, actor store scope, and permission.

## Denial Boundaries

App Gate denial must happen before business processing.

- App disabled and permission denied are different errors.
- Tenant not entitled and store not enabled are different errors.
- Actor permission failure and app entitlement failure are different layers.
- Denial must not trigger business state changes.
- Denial must not write business events, state transitions, idempotency success, or table status changes.
- Denial must write App Gate audit evidence.

Current V1 rejection enum names are:

```text
APP_DISABLED
TENANT_APP_NOT_ENABLED
TENANT_APP_EXPIRED
STORE_APP_NOT_ENABLED
STORE_ACCESS_DENIED
PERMISSION_DENIED
```

Future canonical rejection-code guidance is defined in `docs/backend/APP_GATE_REJECTION_CODES.md`.

## App Gate Audit Rule

Denied requests must write:

```text
app_gate_audit_logs
```

with action:

```text
APP_GATE_DENIED
```

Required operational evidence:

- tenant scope.
- store scope when known.
- actor id when known.
- actor type or role when known.
- app key.
- permission.
- target API or handler.
- reject code.
- request method.
- request path.
- occurrence timestamp.
- metadata needed for support without exposing sensitive request body.

Current V1 schema stores these columns:

- `tenant_id`
- `store_id`
- `app_key`
- `action`
- `operator_user_id`
- `operator_role`
- `before_json`
- `after_json`
- `created_at`

Current V1 denial audit stores the denial snapshot in `after_json` with `decision`, `denyReason`, `messageKey`, and `requiredPermission`.

Until an approved future migration adds explicit method/path/handler metadata fields, new App Gate work must place non-sensitive request metadata in an approved JSON audit field when the implementation supports it. This round does not add that migration or change runtime code.

Allow-request audit is optional in V1. Deny-request audit is mandatory.

If audit writing fails, the business handler must not run. The current implementation records denial audit before writing the denial response; an audit persistence failure propagates and prevents the business handler from being called.

Audit metadata must not contain sensitive body content.

## `/api/me/apps` Rule

The current endpoint is:

```text
GET /api/me/apps?storeId=xxx
```

Responsibilities:

- Return app entries visible and usable by the current actor in the current store.
- Return only tenant-entitled apps.
- Return only store-enabled and entry-visible apps.
- Return only apps for which the actor has at least one entry permission.
- Do not return disabled apps.
- Do not return unentitled apps.
- Do not return apps for actors without permission.
- Do not expose internal entitlement details.

Current response fields:

```text
appKey
appName
status
entryRoute
entryVisible
permissions
```

The current wire response includes `permissions` for foundation compatibility, but the V1 product contract is app-level entry visibility. Staff Home must treat `/api/me/apps` as an app-entry source, not as a complete permission or capability matrix.

Do not use the current `permissions` field to add CheckIn, Queue, Seating, No-show, Cancellation, or Calendar/List buttons in Staff Home. Button-level capability visibility requires a separately approved capability-level response contract.

The staff homepage must use this endpoint to render app entry visibility dynamically. It must not hardcode all platform app entries as visible.

Current staff-home behavior:

- `StoreStaffHomePage.vue` calls `fetchMeApps(storeId)`.
- It finds `reservation_queue` with `entryVisible=true`.
- It renders WalkIn, Cleaning, and Reservation links only when that app entry is visible.
- It does not render CheckIn, Queue, Seating, No-show, Cancellation, or Calendar/List entries.

## Reservation CheckIn Rule

Reservation CheckIn uses:

```text
app_key = reservation_queue
permission = reservation.check_in
```

The CheckIn business state change remains:

```text
confirmed -> arrived
```

App Gate must not introduce Queue or Seating behavior into CheckIn.

CheckIn must not create table occupancy.

CheckIn must not create a QueueTicket unless a later approved Queue/Seating slice explicitly performs that decision after arrival.

## Operational Boundaries

For App Gate integration rounds:

- Do not create a new app key for the same reservation/queue/seating/cleaning loop.
- Do not create a new migration unless the approved round explicitly asks for schema change.
- Do not change existing API paths.
- Do not alter Reservation, QueueTicket, Table, Seating, WalkIn, or Cleaning state machines.
- Do not implement CheckIn, Queue, Seating, No-show, or Cancellation as part of an App Gate handoff-only round.
- Do not connect to production databases.
- Do not insert production data.
- Do not weaken App Gate to make tests pass.
