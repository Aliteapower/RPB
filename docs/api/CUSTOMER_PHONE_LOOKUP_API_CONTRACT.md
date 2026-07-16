# Customer Phone Lookup API Contract

## Scope

Customer Phone Lookup V1 provides exact phone match lookup for staff reservation and walk-in entry forms.

It helps staff reuse an existing tenant-scoped Customer when a Singapore phone number is already known.

## Boundary

- Exact phone lookup only.
- No fuzzy search.
- No customer list browsing.
- No customer mutation.
- No reservation, queue, seating, cleaning, or table mutation.
- No migration required.
- Phone remains tenant-scoped and is not shared across tenants.

## Endpoint

```http
GET /api/v1/stores/{storeId}/customers/phone-lookup?phoneE164=+6591234567
```

## App Gate

| Field | Value |
|---|---|
| App key | `reservation_queue` |
| Required permission | `customer.lookup` |
| Allowed roles | `tenant_admin`, `store_manager`, `store_staff` |

## Request

| Field | Required | Notes |
|---|---:|---|
| `storeId` | Yes | Store scope for App Gate and actor store access. |
| `phoneE164` | Yes | Must be valid E.164. Singapore UI composes this from fixed `+65` plus 8 local digits. |

## Success Response

### Found

```json
{
  "success": true,
  "found": true,
  "customer": {
    "customerId": "40000000-0000-0000-0000-000000001501",
    "displayName": "王先生",
    "nickname": "先生",
    "phoneE164": "+6591234567"
  }
}
```

### Not Found

```json
{
  "success": true,
  "found": false,
  "customer": null
}
```

## Error Response

```json
{
  "success": false,
  "error": {
    "code": "INVALID_PHONE_E164",
    "messageKey": "customer.phone_lookup.invalid_phone_e164",
    "details": {}
  }
}
```

| Code | HTTP | Message key |
|---|---:|---|
| `INVALID_PHONE_E164` | 400 | `customer.phone_lookup.invalid_phone_e164` |
| `FORBIDDEN` | 403 | `customer.phone_lookup.forbidden` |
| `STORE_SCOPE_MISMATCH` | 403 | `customer.phone_lookup.store_scope_mismatch` |
| `PERSISTENCE_ERROR` | 500 | `customer.phone_lookup.persistence_error` |

## Persistence

The lookup reads `customers` by:

```text
tenant_id + phone_e164 + deleted_at is null
```

The existing database already has:

```text
ux_customers_phone_active on (tenant_id, phone_e164)
where phone_e164 is not null and deleted_at is null
```

No new migration is required for V1.

## Frontend Contract

Staff entry forms call lookup only after the local Singapore phone has exactly 8 digits.

When found:

- set hidden `customerId`
- fill customer name when present
- fill salutation/nickname when present

When not found:

- keep `customerId` empty
- allow the existing create reservation / direct seating submit path to create the Customer
