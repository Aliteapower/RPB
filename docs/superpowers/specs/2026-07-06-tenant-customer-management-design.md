# Tenant Customer Management Design

## Goal

Add a tenant backend Customer maintenance menu for guest phone, name, salutation, and optional email, and make staff Reservation plus public booking automatically create or enrich tenant-scoped Customer profiles from the submitted contact information.

## Confirmed Decisions

- Use approach A: keep Customer ownership inside the existing `customer` module and make reservation/public-booking flows reuse Customer profile resolution instead of duplicating customer rules.
- Customer phone is optional. When present it must remain E.164 and unique inside one Tenant.
- Customer salutation uses the existing `Customer.nickname` field. The UI should offer "先生" and "女士" as quick choices and still allow free text.
- V1 Customer maintenance scope is list/search, create, edit, and archive. V1 does not physically delete customers and does not implement customer merge.
- Internationalization is required for new frontend copy and API message keys.

## Existing Context

The database already has a tenant-scoped `customers` table with `display_name`, `nickname`, `phone_e164`, `email`, `lookup_note`, `status`, `created_at`, `updated_at`, `deleted_at`, and `version`.

The current Customer domain object and mapper expose name, nickname, phone, type, and status, but do not expose email. Reservation creation already resolves Customer by `customerId` first, then by phone, and creates a Customer when no existing phone match is found. Public booking currently submits phone and note only; if login is required, it passes the authenticated Customer id and display name.

Tenant admin routes use `/stores/{storeId}/tenant-admin/...` as the permission and store-access entrypoint. Customer identity is still Tenant-level shared data, so Customer maintenance must resolve tenant scope from the authenticated tenant admin and path store access, then read and write Customers by `tenant_id`, not by `store_id`.

## Architecture

Add a focused Customer profile management capability inside the `customer` boundary:

- Domain: extend `Customer` with optional `email` and profile refresh semantics.
- Application: introduce Customer management commands and a reusable Customer profile resolver/upsert service.
- Persistence: keep using the existing `customers` table; add small repository/read-port methods for list, get, create, update, archive, and email-aware save.
- API: expose tenant-admin Customer endpoints through a controller or tenant-admin controller extension that delegates to Customer application services.
- Frontend: add a tenant admin ERP page and navigation item for Customer maintenance.

Reservation Management and Public Booking must depend on the Customer application boundary, not directly on Customer persistence. This keeps Customer rules reusable and prevents reservation services from becoming responsible for profile maintenance.

## Data Design

No new database table is required for V1 because `customers.email` already exists.

Recommended migration scope, only if implementation finds it missing:

- Add a nullable email format check on `customers.email` using the project's existing simple email style.
- Add an index on `(tenant_id, lower(email)) where email is not null and deleted_at is null` if email search or dedupe is needed.

The design does not require email uniqueness in V1. Phone remains the primary automatic match key because the existing unique index already enforces tenant-scoped phone uniqueness.

Archive behavior:

- Archive sets `status = 'archived'`, `deleted_at = now()`, `updated_at = now()`, and increments `version`.
- Archived customers are excluded from list/search and automatic matching.
- Existing historical reservations keep their `customer_id` reference.

## Customer Matching Rules

Customer profile resolution for reservation and public booking:

1. If `customerId` is present, load the active Customer in the actor Tenant. If not found, return the existing customer-not-found error.
2. If no `customerId` and phone is present, find active Customer by `tenant_id + phone_e164`.
3. If no match, create a new Customer.
4. Refresh nonblank profile fields on an existing Customer:
   - phone only when the submitted phone is present.
   - display name only when nonblank.
   - nickname only when nonblank.
   - email only when nonblank.
5. If phone is absent, create or update only through `customerId`; otherwise create a new no-phone Customer with a generated customer code and submitted profile hints.

This preserves no-phone customer support and avoids guessing by name or email, which can cause false merges.

## API Design

Tenant admin Customer endpoints:

```text
GET  /api/v1/stores/{storeId}/tenant-admin/customers?keyword=&limit=&offset=
POST /api/v1/stores/{storeId}/tenant-admin/customers
GET  /api/v1/stores/{storeId}/tenant-admin/customers/{customerId}
PATCH /api/v1/stores/{storeId}/tenant-admin/customers/{customerId}
POST /api/v1/stores/{storeId}/tenant-admin/customers/{customerId}/archive
```

Authorization:

- Authenticated tenant admin only.
- Requires tenant admin role and existing tenant-admin permission behavior.
- Path `storeId` must be accessible by the actor.
- Reads and writes must use the actor Tenant scope; Customer is not store-owned.

Customer response shape:

```json
{
  "success": true,
  "customer": {
    "id": "40000000-0000-0000-0000-000000001501",
    "customerCode": "C-20260706-0001",
    "displayName": "王小明",
    "nickname": "先生",
    "phoneE164": "+6591234567",
    "email": "guest@example.com",
    "status": "active",
    "createdAt": "2026-07-06T00:00:00Z",
    "updatedAt": "2026-07-06T00:00:00Z"
  }
}
```

List response:

```json
{
  "success": true,
  "customers": [],
  "page": {
    "limit": 20,
    "offset": 0,
    "total": 0
  }
}
```

Mutation request:

```json
{
  "displayName": "王小明",
  "nickname": "先生",
  "phoneE164": "+6591234567",
  "email": "guest@example.com"
}
```

Validation:

- `displayName`, `nickname`, `phoneE164`, and `email` are all optional individually, but at least one of display name, phone, or email must be present for a manually created Customer.
- `phoneE164` must be valid E.164 when present.
- `email` must be syntactically valid when present.
- Duplicate active phone in the same Tenant returns a stable conflict error.
- Cross-tenant Customer ids must return not found or forbidden without leaking data.

Reservation create API extension:

- Add optional `customerEmail` to staff reservation create request and command.
- Existing request fields remain backward compatible.
- Response can include email later, but V1 does not require changing existing response consumers.

Public booking API extension:

- Add optional `customerName`, `customerNickname`, and `customerEmail` to public booking create request and command.
- Public booking continues to require idempotency key and booking window validation.
- When login is required, authenticated `customerId` remains authoritative, and submitted public profile fields can refresh nonblank Customer profile fields.

## Frontend Design

Add a new tenant admin navigation item:

```text
顾客资料 / Customers
```

Route:

```text
/stores/:storeId/admin/customers
```

Page behavior:

- ERP-style table, not a marketing layout.
- Search by phone, name, salutation, email, or customer code.
- Create button opens an inline form or dedicated form section.
- Edit updates profile fields.
- Archive asks for confirmation and removes the row from the active list after success.
- Empty, loading, error, and permission-denied states are explicit.
- The layout must work on desktop and mobile without clipped Chinese or English labels.

Form fields:

- Phone: optional; accept E.164. If the UI keeps Singapore helper behavior, compose `+65` from local 8-digit input only where appropriate.
- Name: optional, but create requires at least name, phone, or email.
- Salutation: bind to `nickname`; offer "先生" and "女士", allow manual text.
- Email: optional.

Public booking page update:

- Add name, salutation, and optional email fields to the contact step.
- Keep phone optional only if the final public booking policy allows no-phone bookings. If current public page validation requires phone, implementation may keep phone required for public booking while the Customer object remains phone-optional.
- Submit the new fields to the public booking API.

All new user-facing text must use i18n keys in zh-CN and en-SG locale files, including generated locale files if the current project pattern requires them.

## OOD And Module Boundaries

- Customer module owns Customer profile rules, validation, matching, archiving, and persistence ports.
- Tenant admin module may expose the ERP route and resolve tenant admin scope, but it must delegate Customer behavior to Customer application services.
- Reservation and Public Booking modules pass profile hints into Customer profile resolution and must not query or update the `customers` table directly.
- Customer is not Member. No loyalty, marketing, points, or POS integration is included.
- Customer is tenant-scoped shared data. Do not add `store_id` to Customer rows or treat one store's path parameter as Customer ownership.

## Internationalization

- API errors return stable codes and message keys, not display text.
- Frontend labels, table headers, buttons, empty states, error states, and confirmation text use i18n keys.
- zh-CN labels use "顾客资料", "姓名", "称呼", "电话", "电邮", "归档".
- en-SG labels use "Customers", "Name", "Salutation", "Phone", "Email", "Archive".
- Phone remains E.164 at the API boundary.
- Dates in the Customer table should use existing frontend date formatting patterns. If no shared formatter exists, show ISO text without inventing a store timezone rule for Customer profile history.

## Testing Plan

Backend:

- Customer management service tests for create, update, list/search, archive, optional phone, invalid phone, invalid email, and duplicate phone.
- API tests for tenant admin auth, store access mismatch, create, update, archive, and active-only list.
- Reservation create tests proving `customerEmail` is saved/refreshed and old requests without email still work.
- Public booking tests proving submitted name, nickname, and email are passed into Customer profile resolution.

Frontend:

- Static UI validation for tenant admin nav, router, API client, and customer page i18n keys.
- Public booking UI validation for new contact fields and mutation payload.
- Build validation with TypeScript.

Validation commands should include focused Maven tests and `npm run build`. Local runtime or migration validation must use `target/local-postgres-current.txt` if runtime verification is needed.

## Acceptance Criteria

- Tenant admin can open the new Customer menu.
- Tenant admin can list/search active Customers.
- Tenant admin can create a Customer with optional phone and optional email.
- Tenant admin can edit name, salutation, phone, and email.
- Tenant admin can archive a Customer without physical deletion.
- Staff reservation creation automatically creates or refreshes Customer phone, name, salutation, and email.
- Public booking creation automatically creates or refreshes Customer phone and profile information.
- No Customer data crosses Tenant boundaries.
- All new UI copy is localized for zh-CN and en-SG.
