# Operating Entity Delete Design

## Context

The platform tenant edit page manages operating entities and their stores. Stores already support a platform-admin delete action, but operating entities can only be created or edited. This leaves unused operating entities visible indefinitely even when they no longer own any current stores.

Both `operating_entities` and `stores` already use `deleted_at` for soft deletion. Historical store rows retain their `operating_entity_id`, so deleting an operating entity must preserve that history and must not rely on a database hard delete.

## Goal

Allow a platform administrator to delete an operating entity when it has no current, non-deleted stores. An operating entity whose stores have all been soft-deleted is eligible. The final operating entity in a tenant is also eligible when it has no current stores.

## Scope

This change covers the platform tenant structure API, application service, persistence repository, audit trail, frontend API client, tenant structure component, tenant form page, bilingual copy, focused tests, API documentation, and release notes.

The database schema, existing migrations, tenant/store APIs, role and permission model, and existing business status transitions remain unchanged. No database migration is required.

## Options Considered

### Backend-enforced soft deletion with conditional frontend action

Add an operating-entity delete endpoint. The backend locks the entity, rejects deletion when a current store references it, and otherwise archives and soft-deletes it. The frontend displays the delete action only when its current store list shows no stores for the entity.

This is the selected approach because the backend remains authoritative, historical relationships stay intact, and the UI communicates eligibility without weakening protection against stale data or concurrent writes.

### Frontend-only eligibility check

The frontend could hide the action for entities that appear to have stores and call a generic update when deletion is allowed. This is rejected because direct API calls and concurrent store creation could bypass the browser check.

### Database hard delete

The backend could physically remove the operating-entity row. This is rejected because historical soft-deleted stores retain foreign-key references to the entity and the audit trail requires stable historical identity.

## API Design

Add an endpoint guarded by the existing platform-administrator role and `platform.tenant.manage` permission:

```http
DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}
```

On success, return HTTP 200 with the existing `PlatformOperatingEntityResponse` shape containing the archived, soft-deleted entity. This matches the existing store-delete response convention.

If the tenant or entity does not exist in the requested tenant, preserve the existing `TENANT_NOT_FOUND` and `OPERATING_ENTITY_NOT_FOUND` responses. If at least one store with `deleted_at is null` references the entity, return HTTP 409 with a new stable error:

```json
{
  "success": false,
  "error": {
    "code": "OPERATING_ENTITY_HAS_STORES",
    "messageKey": "platform.tenants.operating_entity_has_stores"
  }
}
```

Soft-deleted historical stores do not block deletion.

## Backend Design

`PlatformTenantStructureService.deleteOperatingEntity` performs the operation in one transaction:

1. Validate the tenant and operating-entity identifiers.
2. Load and lock the current operating entity for update.
3. Check for stores in the same tenant and entity whose `deleted_at` is null.
4. Raise `OPERATING_ENTITY_HAS_STORES` when such a store exists.
5. Set the entity status to `archived`, set `deleted_at` and `updated_at`, and increment `version`.
6. Append an operating-entity delete audit record containing the previous and deleted snapshots.

The repository adds focused methods for locked lookup, current-store existence, and soft deletion. Normal operating-entity list and lookup queries continue to exclude deleted rows.

## Concurrency

The browser's store count is advisory; the backend check determines eligibility.

To prevent deletion from racing with a new store assignment, store creation and store reassignment acquire a key-share lock while validating the active operating entity. Operating-entity deletion acquires an update lock before checking stores and soft-deleting the entity. Therefore:

- a store assignment that starts first completes before deletion checks and causes deletion to return 409;
- a deletion that starts first completes before store validation, after which the deleted entity is no longer eligible for assignment.

The locking remains scoped to the existing transaction and entity row.

## Audit Design

Add an `operating_entity.delete` audit operation targeting the operating entity. Metadata includes the deleted entity snapshot, its previous snapshot, and `deleted: true`, following the established store-delete audit shape.

Audit failure retains the existing fail-closed transaction behaviour, so the entity deletion rolls back when its audit record cannot be written.

## Frontend Design

The platform API client adds `deleteOperatingEntity`, using the new endpoint and the existing response/error wrapper.

`PlatformTenantStructurePanel` emits a delete event for an operating entity only through a visible danger action. The action is rendered when the current, non-deleted store list contains no store assigned to that entity. Entities with a current store keep only the edit action.

`PlatformTenantFormPage` asks for confirmation, calls the delete endpoint, and reloads operating entities, stores, and administrator store access. On success:

- deleting the selected entity selects the next available entity;
- an edit form for the deleted entity closes and resets;
- deleting the last entity shows the empty state while keeping the add-entity action available.

The empty-state copy is updated so it no longer implies that an operating entity is always present. Chinese and English copy cover the delete label, confirmation, empty state, and the backend conflict message.

## Error Handling And Security

- The delete endpoint uses the same platform-admin authentication and tenant-management permission as create and update.
- Cross-tenant identifiers return not found and do not reveal entity ownership.
- A stale UI or direct API request cannot delete an entity with a current store.
- A cancelled confirmation performs no request and does not enter a saving state.
- API errors use the page's existing error banner and translated message-key handling.
- Repeated deletion returns `OPERATING_ENTITY_NOT_FOUND` because list and lookup operations exclude soft-deleted rows.

## Testing

Backend coverage includes:

- successful soft deletion of an entity with no current stores;
- successful deletion when all historical stores are already soft-deleted;
- deletion of the final no-store entity;
- HTTP 409 and no mutation when a current store references the entity;
- tenant and entity not-found handling, including cross-tenant identifiers;
- platform-admin permission enforcement;
- delete audit metadata and rollback behaviour;
- store-assignment validation using the locking repository path.

Frontend behavioural coverage includes:

- a delete action for an entity with zero current stores;
- no delete action for an entity with a current store;
- confirmation cancellation performs no API call;
- confirmed deletion calls the correct endpoint and reloads structure data;
- selection and entity-edit form state recover after deletion;
- the last-entity empty state retains the add action;
- the conflict error is translated and displayed.

Focused backend tests, frontend tests, and `npm run build` must pass before completion. Database-backed validation, if needed, must use the PostgreSQL runtime referenced by `target/local-postgres-current.txt`.

## Documentation, Release, And Rollback

Update the platform tenant API contract with the additive endpoint and stable conflict error. Add a release note describing the new guarded delete capability and its no-schema-change deployment characteristics.

Backend and frontend can deploy together. An older frontend does not call the additive endpoint. Rollback removes the frontend action and endpoint; soft-deleted rows created before rollback remain valid historical data and require no schema or data rollback.
