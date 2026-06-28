# Reservation Public Share H5 Design

## Purpose

Create a customer-facing H5 reservation share page that can be opened from social media or messaging apps without staff login. Staff should be able to share a link from the existing reservation workflow. Customers should see only safe, read-only reservation confirmation details.

## Scope

In scope:

- Generate or reuse an unguessable public share token for a reservation.
- Return share token/path metadata from the staff-side reservation share info API.
- Add a public API that resolves one token to one customer-safe reservation share view.
- Add a public Vue route and H5 page at `/reservation-share/:token`.
- Prefer a native "转发" button through the Web Share API when supported.
- Keep copy-link as a fallback only when direct sharing is unavailable or fails.

Out of scope:

- Sending messages through WhatsApp, WeChat, SMS, webhook, or other third-party channels.
- Customer cancellation, confirmation, modification, check-in, queue, seating, or payment actions.
- QR code generation.
- Tracking analytics beyond the durable token state needed for safe lookup.

## Existing Context

The current staff workflow has `ReservationShareCopyPanel` in the create-reservation success dialog and today's reservation list. It calls the protected staff endpoint:

```text
GET /api/v1/stores/{storeId}/reservations/{reservationId}/share-info
```

That endpoint renders the tenant/store reservation share template and returns plaintext for manual copying. It requires App Gate permission and a logged-in staff actor, so it cannot be opened by customers from social media.

## Chosen Approach

Use a persisted random token.

This is preferred over a signed stateless token because persisted tokens can be revoked, expired, queried by reservation, and evolved later for audit or share-state controls. It is preferred over exposing `storeId + reservationId` because public URLs should not reveal internal operational identifiers or allow easy enumeration.

## Data Design

Add a store-operational token table:

```text
reservation_public_share_tokens
```

Ownership:

- `tenant_id` and `store_id` are required because the token belongs to one store-operational reservation.
- `reservation_id` is required and references one reservation inside the same tenant/store.
- `token` is required, unique, random, and URL-safe. V1 generates 32 random bytes and encodes them as base64url without padding.
- `status` supports at least `active` and `revoked`.
- `expires_at` is nullable in V1. Default local behavior can leave it null so links keep working unless revoked.
- `created_at` and `updated_at` are required.

Expected indexes:

- Unique index on `token`.
- Partial unique index on `(tenant_id, store_id, reservation_id)` for rows where `status = 'active'`, so staff share actions reuse one active token instead of creating many active links.
- Lookup index on `(tenant_id, store_id, reservation_id)`.

The token table is not a Reservation state machine object. It does not change Reservation status and does not create QueueTicket, CheckIn, Seating, Cleaning, or Turnover data.

## API Design

### Staff Share Info

Extend the existing protected staff endpoint response with share metadata:

```json
{
  "shareInfo": {
    "shareToken": "<token>",
    "sharePath": "/reservation-share/<token>",
    "shareTitle": "食刻订位中心 订位确认",
    "shareSummary": "20-06-2030 11:30 · 4人"
  }
}
```

The frontend builds the absolute share URL from `window.location.origin + sharePath`. This avoids backend environment coupling to a frontend host while still giving the staff browser a shareable absolute URL. The existing `shareText` remains for compatibility and as text fallback. The endpoint keeps App Gate enforcement and store-scope validation.

### Public Share View

Add a public read-only endpoint:

```text
GET /api/v1/public/reservation-shares/{token}
```

Response:

```json
{
  "success": true,
  "share": {
    "reservationNo": "R-SHARE-0007",
    "storeName": "食刻订位中心",
    "reservationDate": "20-06-2030",
    "reservationTime": "11:30",
    "partySize": 4,
    "tableCode": "A01",
    "tablePending": false,
    "arrivalNote": "请提前 10 分钟到店",
    "storePhone": "6333 1234",
    "storeAddress": "1 Example Road",
    "googleMapUrl": "https://maps.app.goo.gl/rpb",
    "shareTitle": "食刻订位中心 订位确认",
    "shareSummary": "20-06-2030 11:30 · 4人"
  }
}
```

Public errors:

- `TOKEN_NOT_FOUND`: HTTP 404.
- `TOKEN_REVOKED`: HTTP 410.
- `TOKEN_EXPIRED`: HTTP 410.
- `RESERVATION_NOT_FOUND`: HTTP 404.
- `PERSISTENCE_ERROR`: HTTP 500.

The public response must not include tenant ID, store ID, reservation ID, customer ID, full phone number, actor data, App Gate permissions, internal status transition details, or staff-only operations.

## Frontend Design

### Staff Workflow

The existing share panel changes from "复制订位信息" toward a share-first interaction:

- Primary button text: `转发订位链接`.
- If `navigator.share` is available, call it with title, summary, and URL.
- If direct share is unavailable or rejected by the browser, expose copy-link fallback.
- Keep manual share text fallback for browsers that block clipboard access.

### Customer H5 Page

Add a public route:

```text
/reservation-share/:token
```

The page is mobile-first and independent from the staff shell. It should show:

- Store name as the first viewport signal.
- Reservation confirmation heading.
- Date, time, party size, and table assignment or "待确认".
- Arrival note.
- Store address and phone.
- Map link when provided.
- Native "转发" button when supported.
- Copy-link fallback only when native sharing is unavailable.
- Loading, not found, expired/revoked, and retryable error states.

The page does not show staff bottom navigation, reservation operation buttons, filters, internal IDs, or permission messages.

## Security And Privacy

- Tokens must be random, URL-safe, and not derived from reservation IDs.
- Token lookup must resolve exactly one active token.
- Public API must return customer-safe fields only.
- Staff API continues to require App Gate permission.
- Public API is explicitly unauthenticated but narrow: only token lookup is allowed.
- Share page must not allow enumeration by store or reservation ID.
- Revoked and expired links return a safe error message without revealing reservation details.

## Reuse And Boundaries

Reuse existing capabilities where possible:

- `ReservationShareInfoJdbcRepository` style SQL projection for reservation/store/share profile data.
- `ReservationShareTemplateRenderer` and `StoreShareDateTimeFormatter` for consistent display text.
- Existing store timezone and share profile fields.
- Existing protected staff share-info flow for staff permission checks.

Do not reuse staff `CurrentActor` for public token lookup. Public lookup must be token-scoped rather than actor-scoped.

## Testing Plan

Backend tests:

- Application service creates or reuses an active token for a reservation.
- Public token lookup returns customer-safe fields.
- Revoked, expired, missing, and wrong-scope tokens return stable errors.
- Staff share-info response includes `shareToken`, `sharePath`, `shareTitle`, and `shareSummary` while preserving existing `shareText`.
- Public endpoint is read-only and does not write reservation, queue, seating, cleaning, idempotency, or audit state.

Frontend tests:

- Router includes public `/reservation-share/:token` route with `meta.public`.
- Staff share panel uses direct share path and copy fallback.
- H5 page renders loading, success, not found, expired/revoked, and retryable error states.
- Public page does not import staff shell or expose staff operation text.

Validation:

- Run focused Maven tests for reservation share API/application/UI validations.
- Run frontend build after TypeScript changes.
- Smoke check local H5 URL through Vite and backend proxy.

## Acceptance Criteria

- Staff can press a "转发订位链接" action from the reservation workflow.
- Supported mobile browsers open the native share sheet.
- Unsupported browsers still let staff copy the link.
- The shared URL opens without login.
- The customer H5 page shows reservation confirmation details only.
- The public API cannot expose internal IDs or full customer phone data.
- Existing manual share text behavior remains backward compatible.
