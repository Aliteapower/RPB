# Tenant Profile Self-Maintenance Alignment Design

## Goal

Make tenant backend self-maintenance show and save the same tenant profile fields that platform Tenant Management exposes, while keeping tenant administrator account maintenance available on the same page.

## Problem

Platform Tenant Management edits tenant-level data: tenant code, display name, status, default locale, principal, contact phone, address, password, and logo.

Tenant backend Employee Management now lets the tenant administrator edit only the administrator login account. That makes the tenant backend look like it cannot maintain the same tenant profile data as the platform backend, even though the separate Tenant Profile page already has most of the fields.

## Design

The self-admin edit route `/stores/:storeId/admin/staff/me/edit` becomes a combined self-maintenance page:

- Tenant profile section:
  - tenant code: read-only
  - status: read-only
  - tenant name: editable
  - default language: editable
  - principal: editable
  - tenant contact phone: editable
  - tenant address: editable
  - tenant logo: upload and clear
- Administrator account section:
  - employee number: read-only
  - account display name: editable
  - email: editable
  - password: optional 6-character update

The account phone field is removed from the self-admin form to avoid two competing phone fields. The tenant contact phone is the authoritative phone shown alongside platform tenant data.

## Data Flow

On self mode load:

1. `GET /api/v1/stores/{storeId}/tenant-admin/profile`
2. `GET /api/v1/stores/{storeId}/tenant-admin/staff/me`

On self mode save:

1. `PATCH /api/v1/stores/{storeId}/tenant-admin/profile`
2. `PATCH /api/v1/stores/{storeId}/tenant-admin/staff/me`
3. If a logo file is selected, `POST /api/v1/stores/{storeId}/tenant-admin/profile/logo`

Existing backend authorization and store-scope checks remain unchanged.

## Testing

- Extend the UI static validation test to require the self-admin page to load and save tenant profile data.
- Keep the existing backend integration tests for `staff/me` because account permissions and isolation are unchanged.
- Run `mvn "-Dtest=AuthLoginUiValidationTest,TenantAdminApiIntegrationTest" test`.
- Run `npm run build`.

## Deployment

Frontend deployment is required. Backend deployment is not required unless packaging the full current commit for consistency.
