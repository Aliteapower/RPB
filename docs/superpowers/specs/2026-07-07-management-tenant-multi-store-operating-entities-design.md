# Management Tenant Multi-Store Operating Entities Design

## Purpose

Upgrade RPB from the current mostly one-tenant-one-store operating model to a long-term model where one management tenant can own multiple stores, while each store can still belong to a different operating entity.

The target case is:

- A new management tenant owns the staff pool, platform subscription scope, App Gate entitlement scope, and tenant-level configuration.
- `lsc106` becomes a store under that management tenant.
- `20000000` becomes another store under that same management tenant.
- `lsc106` and `20000000` each belong to a separate operating entity, preserving different business operators, addresses, contacts, and later settlement/tax metadata.
- Staff authorization becomes normal same-tenant store authorization through `auth_account_store_access`.

This design replaces the previous "same tenant store authorization" slice as the long-term architecture. The previous slice can remain as an intermediate UI and authorization foundation, but its assumption that each existing tenant already contains multiple stores is not enough.

## Current State

The schema already has a store table scoped by tenant:

- `stores.tenant_id` allows multiple stores per tenant.
- Operational data such as reservations, walk-ins, queue tickets, tables, seating, cleaning, audit logs, and idempotency rows are already scoped by `(tenant_id, store_id)`.
- `auth_account_store_access` already grants account access to multiple stores inside a tenant.

The product and application behavior are still mostly one tenant to one default store:

- Platform tenant creation calls `ensureDefaultStore` and creates only one store.
- There is no platform or tenant-admin store management surface for creating and managing additional stores.
- Login resolves accounts by `tenant_code + username`, so `tenant_code` currently acts as the primary tenant identity.
- Host prefixes such as `lsc106.booking.yumstone.sg` and `20000000.booking.yumstone.sg` currently behave like tenant prefixes.
- `auth_accounts.default_store_id` is constrained to the account's tenant through `(default_store_id, tenant_id)`.

## Target Model

### Tenant

`tenant` becomes the management boundary. It owns:

- Staff and tenant-admin account pool.
- App Gate tenant entitlements.
- Product subscriptions and billing.
- Tenant-level i18n catalog entries.
- Shared customers, where the product treats the customer base as shared across stores in the same management tenant.
- All stores that should be reachable by the same account after explicit authorization.

The management tenant code must be a new code selected by the operator, such as `rpb` or another brand/group code. Implementation must not hard-code this value.

### Operating Entity

`operating_entity` represents a business operator inside a management tenant. It owns descriptive and future settlement metadata:

- `entity_code`
- `display_name`
- `status`
- `default_locale`
- `contact_phone`
- `address`
- `principal_name`

The first implementation stores only operational identity fields. Settlement, tax, invoicing, and legal-registration fields are out of scope for the first slice, but the table boundary must allow adding them later without changing store authorization.

### Store

`store` remains the operational unit for reservations, queues, tables, cleaning, public booking, and staff workbench routes.

Each store belongs to exactly one tenant. `operating_entity_id` stays nullable during the migration window so existing stores continue to load before backfill. After production migration is complete, every active store in the management tenant should have an operating entity.

Store fields continue to include:

- `store_code`
- `display_name`
- `status`
- `timezone`
- `locale`
- `date_format`
- `time_format`
- `currency`

New or clarified fields:

- `operating_entity_id`: the owning operating entity under the same tenant.
- `host_prefix_code`: nullable public/login host alias for this store when the old tenant code must keep working as a browser entry.

## Host Prefix Strategy

Host prefixes must no longer mean "tenant code only".

Add a tenant host alias model:

```text
tenant_host_aliases
  id
  tenant_id
  alias_code
  alias_type
  default_store_id
  status
  created_at
  updated_at
  deleted_at
```

Rules:

- `alias_type = tenant` maps a host prefix to the management tenant without a default store override.
- `alias_type = store` maps a host prefix to the management tenant and a default store.
- `lsc106` and `20000000` should become `store` aliases after migration.
- `platform.<domain>` remains reserved for platform admin login and must not resolve through this table.
- Root-domain compatibility remains unchanged.

The login resolver should resolve a host prefix in this order:

1. Reserved platform prefix.
2. Active host alias.
3. Active tenant code.
4. Root-domain legacy behavior.

This keeps old URLs working while moving the true identity boundary to the management tenant.

## Account And Authorization Model

Accounts remain tenant-scoped:

- `auth_accounts.tenant_id` points to the management tenant.
- `auth_accounts.username` remains unique inside a tenant for non-platform accounts.
- `auth_accounts.default_store_id` stays valid because all authorized stores now share the management tenant.
- `auth_account_store_access` remains the authorization table for tenant admins and staff.

Rules:

- Platform admins can authorize a tenant admin to any active store inside the management tenant.
- Tenant admins can only assign ordinary staff to stores that the tenant admin can access.
- Tenant admins cannot create stores or operating entities unless a later permission explicitly grants that ability.
- Ordinary staff can only enter stores in their own `auth_account_store_access` rows.
- Store switching remains route-only through `/stores/:storeId/...`.

## API Design

### Platform Operating Entities

New endpoints:

```text
GET  /api/v1/platform/tenants/{tenantId}/operating-entities
POST /api/v1/platform/tenants/{tenantId}/operating-entities
GET  /api/v1/platform/tenants/{tenantId}/operating-entities/{entityId}
PATCH /api/v1/platform/tenants/{tenantId}/operating-entities/{entityId}
POST /api/v1/platform/tenants/{tenantId}/operating-entities/{entityId}/archive
```

Permission:

- Reuse `platform.tenant.manage` for the first slice.
- A future `platform.operating-entity.manage` permission can be split out if platform roles become finer-grained.

### Platform Stores

New endpoints:

```text
GET  /api/v1/platform/tenants/{tenantId}/stores
POST /api/v1/platform/tenants/{tenantId}/stores
GET  /api/v1/platform/tenants/{tenantId}/stores/{storeId}
PATCH /api/v1/platform/tenants/{tenantId}/stores/{storeId}
POST /api/v1/platform/tenants/{tenantId}/stores/{storeId}/archive
```

Rules:

- Store create and update must validate that `operatingEntityId`, when present, belongs to the same tenant and is active.
- Store code is unique within a tenant among undeleted stores.
- Store archive is forbidden in the first slice. Store archival requires a later workflow-safety contract that checks active reservations, queues, seating, table resources, public booking, and App Gate state before the endpoint is added.

### Host Aliases

New endpoints:

```text
GET  /api/v1/platform/tenants/{tenantId}/host-aliases
POST /api/v1/platform/tenants/{tenantId}/host-aliases
PATCH /api/v1/platform/tenants/{tenantId}/host-aliases/{aliasId}
POST /api/v1/platform/tenants/{tenantId}/host-aliases/{aliasId}/archive
```

Rules:

- Alias code must be DNS-label safe and unique among active tenant host aliases and active tenant codes.
- `platform` is reserved and cannot be used.
- A `store` alias must reference an active store in the same tenant.

### Current Store List

Extend `GET /api/v1/me/stores` response with fields that help the UI explain the management model:

```json
{
  "success": true,
  "stores": [
    {
      "tenantId": "management-tenant-id",
      "tenantCode": "rpb",
      "storeId": "store-id",
      "storeCode": "lsc106",
      "storeName": "LSC106",
      "operatingEntityId": "entity-id",
      "operatingEntityName": "LSC Operating Entity",
      "status": "active",
      "locale": "en-SG",
      "defaultStore": true
    }
  ]
}
```

Compatibility:

- Existing frontend code may continue using `storeId`, `storeCode`, `storeName`, `status`, `locale`, and `defaultStore`.
- New fields are additive.

## Database Design

### New Tables

`operating_entities`:

```sql
create table operating_entities (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    entity_code text not null,
    display_name text not null,
    status text not null,
    default_locale text null,
    contact_phone text null,
    address text null,
    principal_name text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_operating_entities_status check (status in ('active', 'inactive', 'archived')),
    constraint uq_operating_entities_id_tenant unique (id, tenant_id)
);

create unique index ux_operating_entities_tenant_code_active
    on operating_entities (tenant_id, lower(entity_code))
    where deleted_at is null;
```

`tenant_host_aliases`:

```sql
create table tenant_host_aliases (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    alias_code text not null,
    alias_type text not null,
    default_store_id uuid null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_tenant_host_aliases_store_scope
        foreign key (default_store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_tenant_host_aliases_type check (alias_type in ('tenant', 'store')),
    constraint ck_tenant_host_aliases_status check (status in ('active', 'inactive', 'archived')),
    constraint ck_tenant_host_aliases_store_required check (
        (alias_type = 'tenant' and default_store_id is null)
        or (alias_type = 'store' and default_store_id is not null)
    )
);

create unique index ux_tenant_host_aliases_code_active
    on tenant_host_aliases (lower(alias_code))
    where deleted_at is null;
```

### Store Table Extension

Add:

```sql
alter table stores
    add column operating_entity_id uuid null,
    add constraint fk_stores_operating_entity_scope
        foreign key (operating_entity_id, tenant_id)
        references operating_entities(id, tenant_id);
```

The first migration must keep `operating_entity_id` nullable for backward compatibility. A later migration may make it required after every active store has been assigned.

## Migration Strategy For lsc106 And 20000000

The production data migration is a separate operational milestone and must not be mixed with the first schema/API slice.

Planned flow:

1. Create the new management tenant with an operator-provided tenant code.
2. Create two operating entities under the management tenant, one for `lsc106` and one for `20000000`.
3. Move or recreate the `lsc106` and `20000000` stores under the management tenant.
4. Update every store-scoped table for each moved store so its `tenant_id` matches the management tenant.
5. Move or recreate tenant-level configurations that should become shared management-tenant data.
6. Create host aliases for `lsc106` and `20000000` pointing to their stores.
7. Migrate accounts into the management tenant and rebuild `auth_account_store_access`.
8. Disable or archive old tenant records only after smoke tests confirm login, store switching, public booking, reservations, queues, tables, and staff management.

The migration script must be parameterized and rehearsed against a production-like copy. It must print row counts per table before and after update, and it must stop if any table has unexpected rows for the store being moved.

## Module Boundaries

Create or keep these module responsibilities:

- `platform` owns tenant lifecycle, platform tenant pages, platform store management, and platform host alias management.
- `operatingentity` owns operating entity application records and persistence. If kept under `platform` for the first slice, classes should still be separated by package and service boundary.
- `store` owns store scope lookup and store metadata shared by operational modules.
- `auth` owns login principal, current store list, account authorization rows, and default store.
- `tenantadmin` owns tenant-admin workflows for staff, tables, customers, settings, and store-scoped admin pages.
- Operational modules continue to consume `StoreScope` and should not know about host aliases.

OOD rules:

- Controllers map HTTP to commands/responses only.
- Application services perform orchestration and validation.
- Repositories own SQL and no controller should call a repository directly.
- Store-scope resolution should be centralized, not repeated in every controller.
- UI store switcher stays reusable and receives store access rows instead of knowing platform persistence details.

## Frontend And Internationalization

Platform UI additions:

- Add a tenant detail sub-view or tabs for:
  - Tenant profile
  - Operating entities
  - Stores
  - Tenant admin store access
  - Host aliases
- Add create/edit forms for operating entities and stores.
- Add host alias management for old prefixes such as `lsc106` and `20000000`.

Tenant admin UI:

- Continue using `/stores/:storeId/...`.
- Store switcher should show store name and the operating entity name when the API returns one.
- Staff authorization panels should list stores inside the current management tenant that the tenant admin is allowed to delegate.

i18n:

- Every new label, empty state, validation error, and action must be added to `zh-CN` and `en-SG`.
- Avoid hard-coded product copy in Vue files.
- Chinese copy should use "经营主体" for operating entity and "门店别名" or "域名前缀" for host alias depending on UI context.

## Security And Permissions

- Platform store, operating entity, and host alias management is guarded by platform admin plus `platform.tenant.manage` in the first slice.
- Tenant admins cannot create operating entities, create stores, or edit host aliases.
- Tenant admins can delegate only stores they can access.
- Ordinary staff cannot self-expand store authorization.
- Host aliases must not allow a user to log into a tenant where the account does not exist.
- Store-scoped APIs must continue to enforce `CurrentActor.canAccessStore(storeId)`.

## Testing Plan

Backend tests:

- Migration test validates new tables, FKs, unique indexes, and nullable `stores.operating_entity_id`.
- Platform API tests create operating entities and stores under one tenant.
- Platform API tests reject cross-tenant operating entity ids on store create/update.
- Host alias tests resolve `lsc106` and `20000000` to the management tenant and correct default store.
- Auth tests prove `/api/v1/me/stores` returns operating entity metadata.
- Tenant admin tests prove an admin can authorize staff to multiple stores inside the management tenant.
- Tenant admin tests prove a tenant admin cannot delegate a store the admin does not have.

Frontend/static tests:

- Platform tenant page references operating entity, store, and host alias API clients.
- Locale files include zh-CN and en-SG keys for all new labels.
- Store switcher and staff authorization UI can display operating entity names.

Manual and production-like smoke tests:

- Login through `lsc106.<domain>/login` lands in the management tenant with the lsc106 default store.
- Login through `20000000.<domain>/login` lands in the same management tenant with the 20000000 default store.
- An authorized staff account can switch between both stores.
- Reservations and queue data stay separated by store.
- Tenant-level customer behavior is reviewed because customers may become shared across stores in the management tenant.

## Release Phases

### Phase 1: Schema And Platform Management Foundation

Deliver:

- `operating_entities` table.
- `tenant_host_aliases` table.
- `stores.operating_entity_id`.
- Platform APIs and minimal UI for operating entities, stores, and aliases.
- i18n keys.
- No production data merge.

### Phase 2: Authorization And Store List Refinement

Deliver:

- `/api/v1/me/stores` operating entity metadata.
- Tenant admin delegation limited to the admin's authorized stores.
- Store switcher display improvements.
- Remove or rewrite the misleading same-tenant-only platform tenant admin authorization copy.

### Phase 3: Host Alias Login

Deliver:

- Host prefix resolver backed by `tenant_host_aliases`.
- Login default store selected by alias when alias type is `store`.
- Compatibility tests for root, platform, tenant-code, and store-alias prefixes.

### Phase 4: Data Consolidation Migration

Deliver:

- Parameterized migration scripts for `lsc106` and `20000000`.
- Row-count verification.
- Dry run on local or staging copy.
- Rollback plan based on database backup and old jar/frontend deployment.

### Phase 5: Production Rollout

Deliver:

- Deploy code before data migration.
- Run smoke tests on existing one-store tenants.
- Execute data migration in a maintenance window.
- Verify host aliases, login, staff authorization, store switching, reservations, queues, tables, public booking, and tenant admin pages.

## Open Decisions

These are decisions for the operator before Phase 4, not blockers for Phase 1:

- The management tenant code.
- Whether old tenant records are archived or kept as historical references after migration.
- Whether customers are shared across `lsc106` and `20000000` after consolidation.
- Whether billing should be at the management tenant only, or later split by operating entity.

## Non-Goals

- Do not implement cross-tenant store authorization as the primary model.
- Do not change operational data tables away from `(tenant_id, store_id)` scope.
- Do not merge production data in the first implementation slice.
- Do not make operating entity settlement or tax fields part of the first slice.
- Do not remove old host-prefix behavior until aliases are live and verified.
