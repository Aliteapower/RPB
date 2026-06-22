# App Gate New Slice Template

Use this template when designing or implementing a new Reservation Platform business slice that exposes protected APIs.

Do not use this template to create a new app key for the existing reservation/queue/seating/cleaning loop.

## App Gate Integration Section

### App

```text
app_key:
```

### Permission

```text
permission_key:
```

### Protected Endpoints

| Method | Path | App key | Permission |
|---|---|---|---|
| | | | |

### Scope Source

```text
tenant_id source:
store_id source:
actor source:
```

### Deny Cases

- Tenant not entitled.
- Tenant entitlement suspended or expired.
- Store disabled or not configured.
- Actor missing.
- Actor store scope mismatch.
- Permission denied.
- App Gate scope mismatch.
- Unknown App Gate failure.

### Audit

```text
deny audit action: APP_GATE_DENIED
required audit fields:
- tenant_id
- store_id when known
- actor_id when known
- actor_type or role when known
- app_key
- permission
- target API or handler
- reject_code
- request method
- request path
- occurred_at
- non-sensitive metadata
```

### Tests

- Allowed request.
- Tenant not entitled.
- Tenant suspended or expired.
- Store disabled or not configured.
- Permission denied.
- Actor missing or store scope mismatch.
- Deny writes `app_gate_audit_logs`.
- Existing business regression.
- `/api/me/apps` app-entry behavior when the slice needs a staff-home entry.
- Capability-level `/api/me/apps` response behavior only when a separate UI/API contract approves button-level visibility.

### Boundary Check

```text
New app_key created:
New migration created:
Existing API path changed:
Business state machine changed:
Production database touched:
```

## Example: Reservation CheckIn

### App

```text
app_key: reservation_queue
```

### Permission

```text
permission_key: reservation.check_in
```

### Protected Endpoints

| Method | Path | App key | Permission |
|---|---|---|---|
| `POST` | `/api/v1/stores/{storeId}/reservations/{reservationId}/check-in` | `reservation_queue` | `reservation.check_in` |

### Scope Source

```text
tenant_id source: server-side actor/security context
store_id source: trusted path variable {storeId}
actor source: CurrentActorProvider or equivalent server-side security context
```

### Deny Cases

- Tenant lacks `reservation_queue` entitlement.
- Tenant entitlement is suspended or expired.
- Store has `reservation_queue` disabled or not configured.
- Actor lacks `reservation.check_in`.
- Actor cannot access `{storeId}`.

### Audit

```text
deny audit action: APP_GATE_DENIED
required audit fields:
- tenant_id
- store_id
- actor_id
- actor_type or role
- app_key = reservation_queue
- permission = reservation.check_in
- target API or handler
- reject_code
- request method
- request path
- occurred_at
- non-sensitive metadata
```

### Tests

- Allowed CheckIn request changes Reservation `confirmed -> arrived`.
- Tenant not entitled is denied before business handler.
- Store disabled is denied before business handler.
- Permission denied is denied before business handler.
- Deny writes `app_gate_audit_logs`.
- Existing Reservation Create, WalkIn Direct Seating, and Cleaning regressions still pass.
- `/api/me/apps` continues to return the `reservation_queue` app entry when the current actor/store is eligible.
- Staff Home does not add a CheckIn entry unless a separate CheckIn UI round approves it.

### Business Boundary

Reservation CheckIn only performs:

```text
confirmed -> arrived
```

It must not create QueueTicket.

It must not create Seating.

It must not create table occupancy.

It must not change existing Reservation Create behavior.
