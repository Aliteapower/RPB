# Operating Entity Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a platform administrator delete an operating entity when it has no current, non-deleted stores, including the tenant's final operating entity.

**Architecture:** Add an additive platform-admin DELETE endpoint backed by transactional soft deletion, a current-store guard, row locking that serializes deletion against store assignment, and an audit record. The Vue tenant structure UI shows the delete action only for locally eligible entities, while the page confirmation and backend remain authoritative and refresh all structure/access state after success.

**Tech Stack:** Java 21, Spring Boot, JDBC/PostgreSQL, JUnit 5/MockMvc, Vue 3, TypeScript, Vue Test Utils, Vitest, vue-router, vue-i18n.

## Global Constraints

- An operating entity is deletable when zero stores with `deleted_at is null` reference it.
- Soft-deleted historical stores do not block deletion and retain their `operating_entity_id` history.
- The tenant's final operating entity may be deleted when it has no current stores.
- Deletion is a soft delete: set status to `archived`, populate `deleted_at`, update `updated_at`, and increment `version`.
- Do not change the database schema, migrations, existing role/permission model, dependencies, or existing tenant/store API behaviour.
- Keep `platform_admin` plus `platform.tenant.manage` as the required authority.
- Return HTTP 409 with stable code `OPERATING_ENTITY_HAS_STORES` when a current store blocks deletion.
- Return the existing `PlatformOperatingEntityResponse` on success and preserve tenant/entity not-found semantics.
- Preserve tenant isolation: a cross-tenant entity identifier is `OPERATING_ENTITY_NOT_FOUND`.
- Keep the UI's store-count check advisory; the backend guard is authoritative.
- Keep Chinese and English copy aligned.
- Any database-backed validation must use `target/local-postgres-current.txt` as required by `AGENTS.md`.

---

## File Structure

- `docs/api/PLATFORM_TENANT_API_CONTRACT.md`: endpoint, soft-delete rules, error, replay, compatibility.
- `src/main/java/com/rpb/reservation/platform/application/PlatformTenantServiceErrorCode.java`: service conflict code.
- `src/main/java/com/rpb/reservation/platform/api/PlatformTenantApiErrorCode.java`: HTTP 409 mapping.
- `src/main/java/com/rpb/reservation/platform/persistence/PlatformTenantStructureRepository.java`: entity/store locks, store guard, soft deletion.
- `src/main/java/com/rpb/reservation/platform/application/PlatformTenantStructureService.java`: deletion transaction.
- `src/main/java/com/rpb/reservation/platform/application/PlatformTenantAuditService.java`: delete audit record.
- `src/main/java/com/rpb/reservation/platform/api/PlatformTenantController.java`: DELETE endpoint.
- `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`: persistence, conflict, replay, tenant scope, permission, audit.
- `src/test/java/com/rpb/reservation/platform/api/PlatformTenantLocalRuntimeSecurityTest.java`: local platform-admin path.
- `src/components/platform/PlatformTenantStructurePanel.vue`: conditional delete action and local state recovery.
- `src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts`: rendered eligibility behaviour.
- `src/api/platformApi.ts`: archived response type and DELETE client.
- `src/components/platform/platformTenantUi.ts`: editable status restriction.
- `src/pages/PlatformTenantFormPage.vue`: confirmation, delete, reload, conflict handling.
- `src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts`: cancellation, success orchestration, error state.
- `src/i18n/locales/zh-CN.ts` and `src/i18n/locales/en-SG.ts`: aligned copy.
- `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`: source wiring guard.
- `docs/release-notes/2026-07-17-operating-entity-delete.md`: release and rollback.

## Pre-Implementation API Review

### Endpoint

`DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}` follows the existing resource style and store-delete convention.

### Contract Check

The contract is updated before implementation. There is no request body. The explicit `PlatformOperatingEntityResponse` is reused; no persistence object is serialized directly.

### Permission Check

The controller calls `requirePlatformAdmin()`, enforcing `platform_admin` and `platform.tenant.manage`. No App Gate registration or local-runtime allowlist expansion is required.

### Error Mapping

`OPERATING_ENTITY_HAS_STORES` maps to HTTP 409 and `platform.tenants.operating_entity_has_stores`. Tenant absence maps to `TENANT_NOT_FOUND`. Absent, deleted, repeated, and cross-tenant entity IDs map to `OPERATING_ENTITY_NOT_FOUND`.

### Replay / Idempotency

The first eligible DELETE returns 200. Repeating it returns 404 while preserving the same deleted state. A 409 makes no mutation and may be retried after current stores are removed.

### Compatibility

The endpoint and error are additive. The TypeScript response union acknowledges `archived`, while mutation/form types remain restricted to `active|inactive`.

### Missing Items

None. A database migration and local-runtime allowlist change are intentionally absent.

## Pre-Implementation TDD Review

| Scenario | Planned Test | Expected |
|---|---|---|
| Current store blocks delete | API integration | 409, unchanged entity |
| Soft-deleted store no longer blocks | API integration | 200 |
| Final entity deletion | API integration | archived, deleted, list empty |
| Repeat delete | API integration | 404, state remains deleted |
| Missing tenant | API integration | 404 TENANT_NOT_FOUND |
| Cross-tenant entity | API integration | 404 |
| Tenant admin calls endpoint | API integration | 403 |
| Local platform admin calls endpoint | controller security test | 200 |
| Audit append | API integration | one delete audit row |
| Audit append fails | API integration with audit spy | 500 and entity mutation rolled back |
| Entity has no current store | panel behaviour | delete rendered and emitted |
| Entity has a current store | panel behaviour | delete absent |
| Confirmation cancelled | page behaviour | no request |
| Confirmed deletion | page behaviour | DELETE plus reload |
| Duplicate click while pending | page behaviour | one DELETE request |
| Backend conflict | page behaviour | translated alert |
| Last entity removed | panel behaviour | empty state, add action retained |

No Reservation, Queue, Walk-in, Seating, or Cleaning state transition changes are in scope.

---

### Task 1: Add The Guarded Backend Soft Delete

**Files:**
- Modify: `docs/api/PLATFORM_TENANT_API_CONTRACT.md`
- Modify: `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/platform/api/PlatformTenantLocalRuntimeSecurityTest.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantServiceErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/platform/api/PlatformTenantApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/platform/persistence/PlatformTenantStructureRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantStructureService.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantAuditService.java`
- Modify: `src/main/java/com/rpb/reservation/platform/api/PlatformTenantController.java`

**Interfaces:**
- Consumes: existing `PlatformOperatingEntity`, `PlatformOperatingEntityResponse`, `PlatformOperator`, tenant lookup, transactions, `requirePlatformAdmin()`.
- Produces: `deleteOperatingEntity(UUID, UUID, PlatformOperator)`, locking repository methods, DELETE endpoint, `OPERATING_ENTITY_HAS_STORES`.

- [ ] **Step 1: Write the contract before the endpoint**

Add after operating-entity create/update rules:

~~~markdown
### `DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}`

Soft-deletes an operating entity only when no current store references it.

Rules:
- A current store is in the same tenant/entity with `stores.deleted_at is null`.
- Soft-deleted historical stores do not block deletion.
- The final no-store operating entity may be deleted.
- Success sets `status = archived`, sets `deleted_at`, increments `version`, and returns `deleted = true`.
- A current store returns HTTP 409 with `OPERATING_ENTITY_HAS_STORES` and makes no mutation.
- Invalid tenants return `TENANT_NOT_FOUND`; invalid, deleted, repeated, or cross-tenant entities return `OPERATING_ENTITY_NOT_FOUND`.
- Repeating a successful delete returns `OPERATING_ENTITY_NOT_FOUND` while the entity remains deleted.
~~~

- [ ] **Step 2: Write failing API integration tests**

Add to `PlatformTenantApiIntegrationTest`:

Add the Mockito/Spring spy imports and field used by the rollback case:

~~~java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.rpb.reservation.platform.application.PlatformTenantAuditService;
import com.rpb.reservation.platform.application.PlatformTenantServiceErrorCode;
import com.rpb.reservation.platform.application.PlatformTenantServiceException;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpyBean(reset = MockReset.AFTER)
private PlatformTenantAuditService platformTenantAuditService;
~~~

~~~java
@Test
void platformAdminDeletesFinalOperatingEntityAfterItsOnlyStoreIsSoftDeleted() throws Exception {
    Cookie session = login("sysadmin");
    UUID tenantId = createGroupTenant(session, "codex-entity-delete", "Codex 主体删除集团", "abc123");
    UUID entityId = jdbc.queryForObject(
        "select id from operating_entities where tenant_id = ? and deleted_at is null",
        UUID.class,
        tenantId
    );
    UUID storeId = createStore(
        session, tenantId, entityId, "codex-entity-delete-store", "Codex 主体删除门店", null, null
    );

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            tenantId, entityId
        ).cookie(session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("OPERATING_ENTITY_HAS_STORES"))
        .andExpect(jsonPath("$.error.messageKey").value("platform.tenants.operating_entity_has_stores"));

    assertThat(countWhere(
        "select count(*) from operating_entities where tenant_id = ? and id = ? and deleted_at is null",
        tenantId, entityId
    )).isEqualTo(1);

    mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}/stores/{storeId}", tenantId, storeId)
            .cookie(session))
        .andExpect(status().isOk());

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            tenantId, entityId
        ).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operatingEntity.id").value(entityId.toString()))
        .andExpect(jsonPath("$.operatingEntity.status").value("archived"))
        .andExpect(jsonPath("$.operatingEntity.deleted").value(true));

    assertThat(countWhere(
        "select count(*) from operating_entities where tenant_id = ? and id = ? and status = 'archived' and deleted_at is not null",
        tenantId, entityId
    )).isEqualTo(1);
    assertThat(countWhere(
        "select count(*) from stores where tenant_id = ? and id = ? and operating_entity_id = ? and deleted_at is not null",
        tenantId, storeId, entityId
    )).isEqualTo(1);
    assertThat(countWhere(
        "select count(*) from audit_logs where target_type = 'operating_entity' and target_id = ? and operation_code = 'platform.tenant.operating_entity.delete'",
        entityId
    )).isEqualTo(1);

    mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/operating-entities", tenantId).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operatingEntities.length()").value(0));

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            tenantId, entityId
        ).cookie(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("OPERATING_ENTITY_NOT_FOUND"));
}

@Test
void operatingEntityDeleteHidesCrossTenantOwnershipAndRejectsTenantAdmin() throws Exception {
    Cookie platformSession = login("sysadmin");
    UUID firstTenantId = createGroupTenant(platformSession, "codex-entity-alpha", "Codex 主体甲", "abc123");
    UUID secondTenantId = createGroupTenant(platformSession, "codex-entity-beta", "Codex 主体乙", "abc123");
    UUID firstEntityId = jdbc.queryForObject(
        "select id from operating_entities where tenant_id = ? and deleted_at is null",
        UUID.class,
        firstTenantId
    );

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            UUID.randomUUID(), firstEntityId
        ).cookie(platformSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TENANT_NOT_FOUND"));

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            secondTenantId, firstEntityId
        ).cookie(platformSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("OPERATING_ENTITY_NOT_FOUND"));

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            firstTenantId, firstEntityId
        ).cookie(login("20000000")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
}

@Test
void operatingEntityDeleteRollsBackWhenAuditWriteFails() throws Exception {
    Cookie session = login("sysadmin");
    UUID tenantId = createGroupTenant(session, "codex-entity-audit", "Codex 主体审计集团", "abc123");
    UUID entityId = jdbc.queryForObject(
        "select id from operating_entities where tenant_id = ? and deleted_at is null",
        UUID.class,
        tenantId
    );
    doThrow(new PlatformTenantServiceException(PlatformTenantServiceErrorCode.AUDIT_WRITE_FAILED))
        .when(platformTenantAuditService)
        .recordOperatingEntityDeleted(any(), any(), any());

    mockMvc.perform(delete(
            "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
            tenantId, entityId
        ).cookie(session))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("AUDIT_WRITE_FAILED"));

    assertThat(countWhere(
        "select count(*) from operating_entities where tenant_id = ? and id = ? and status = 'active' and deleted_at is null",
        tenantId, entityId
    )).isEqualTo(1);
    assertThat(countWhere(
        "select count(*) from audit_logs where target_type = 'operating_entity' and target_id = ? and operation_code = 'platform.tenant.operating_entity.delete'",
        entityId
    )).isZero();
}
~~~

In `PlatformTenantLocalRuntimeSecurityTest`, add this fixture:

~~~java
private static PlatformOperatingEntity deletedOperatingEntity() {
    return new PlatformOperatingEntity(
        OPERATING_ENTITY_ID,
        TENANT_ID,
        "local-entity",
        "Local Operating Entity",
        "archived",
        "zh-CN",
        "+6590000000",
        "1 Local Street",
        "Local Manager",
        OffsetDateTime.parse("2026-06-25T00:00:00Z"),
        OffsetDateTime.parse("2026-07-17T00:00:00Z"),
        OffsetDateTime.parse("2026-07-17T00:00:00Z")
    );
}
~~~

Add to its structure API test:

~~~java
when(structureService.deleteOperatingEntity(eq(TENANT_ID), eq(OPERATING_ENTITY_ID), any()))
    .thenReturn(deletedOperatingEntity());

mockMvc.perform(delete(
        "/api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}",
        TENANT_ID, OPERATING_ENTITY_ID
    ))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.operatingEntity.id").value(OPERATING_ENTITY_ID.toString()))
    .andExpect(jsonPath("$.operatingEntity.deleted").value(true));
~~~

- [ ] **Step 3: Run tests and verify red**

~~~powershell
mvn -q "-Dtest=PlatformTenantApiIntegrationTest,PlatformTenantLocalRuntimeSecurityTest" test
~~~

Expected: compilation/assertion failure because the service method, endpoint, and conflict code do not exist.

- [ ] **Step 4: Add error mapping and controller**

Add `OPERATING_ENTITY_HAS_STORES` to the service enum and:

~~~java
OPERATING_ENTITY_HAS_STORES(HttpStatus.CONFLICT, "platform.tenants.operating_entity_has_stores"),
~~~

Add after operating-entity update:

~~~java
@DeleteMapping("/{tenantId}/operating-entities/{operatingEntityId}")
public ResponseEntity<PlatformOperatingEntityResponse> deleteOperatingEntity(
    @PathVariable UUID tenantId,
    @PathVariable UUID operatingEntityId
) {
    CurrentActor actor = requirePlatformAdmin();
    return ResponseEntity.ok(PlatformOperatingEntityResponse.from(
        structureService.deleteOperatingEntity(tenantId, operatingEntityId, toOperator(actor))
    ));
}
~~~

- [ ] **Step 5: Add repository locks, guard, and soft deletion**

Replace `activeOperatingEntityExists`:

~~~java
public boolean lockActiveOperatingEntity(UUID tenantId, UUID operatingEntityId) {
    return !jdbc.query(
        """
        select id
        from operating_entities
        where tenant_id = ?
          and id = ?
          and status = 'active'
          and deleted_at is null
        for key share
        """,
        (rs, rowNum) -> rs.getObject("id", UUID.class),
        tenantId,
        operatingEntityId
    ).isEmpty();
}
~~~

Add:

~~~java
public Optional<PlatformOperatingEntity> findOperatingEntityForUpdate(UUID tenantId, UUID operatingEntityId) {
    return jdbc.query(
        """
        select id, tenant_id, entity_code, display_name, status,
               default_locale, contact_phone, address, principal_name,
               created_at, updated_at, deleted_at
        from operating_entities
        where tenant_id = ?
          and id = ?
          and deleted_at is null
        for update
        """,
        (rs, rowNum) -> operatingEntity(rs),
        tenantId,
        operatingEntityId
    ).stream().findFirst();
}

public boolean currentStoreExists(UUID tenantId, UUID operatingEntityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from stores
        where tenant_id = ?
          and operating_entity_id = ?
          and deleted_at is null
        """,
        Integer.class,
        tenantId,
        operatingEntityId
    );
    return count != null && count > 0;
}

public Optional<PlatformOperatingEntity> softDeleteOperatingEntity(UUID tenantId, UUID operatingEntityId) {
    return jdbc.query(
        """
        update operating_entities
        set status = 'archived',
            deleted_at = coalesce(deleted_at, now()),
            updated_at = now(),
            version = version + 1
        where tenant_id = ?
          and id = ?
          and deleted_at is null
        returning id, tenant_id, entity_code, display_name, status,
                  default_locale, contact_phone, address, principal_name,
                  created_at, updated_at, deleted_at
        """,
        (rs, rowNum) -> operatingEntity(rs),
        tenantId,
        operatingEntityId
    ).stream().findFirst();
}
~~~

- [ ] **Step 6: Implement service and audit**

Change `validateOperatingEntity` to use `lockActiveOperatingEntity`. Add:

~~~java
@Transactional
public PlatformOperatingEntity deleteOperatingEntity(
    UUID tenantId,
    UUID operatingEntityId,
    PlatformOperator operator
) {
    requireTenant(tenantId);
    if (operatingEntityId == null) {
        throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
    }
    PlatformOperatingEntity existing = structureRepository
        .findOperatingEntityForUpdate(tenantId, operatingEntityId)
        .orElseThrow(() -> new PlatformTenantServiceException(
            PlatformTenantServiceErrorCode.OPERATING_ENTITY_NOT_FOUND
        ));
    if (structureRepository.currentStoreExists(tenantId, operatingEntityId)) {
        throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.OPERATING_ENTITY_HAS_STORES);
    }
    PlatformOperatingEntity deleted = structureRepository
        .softDeleteOperatingEntity(tenantId, operatingEntityId)
        .orElseThrow(() -> new PlatformTenantServiceException(
            PlatformTenantServiceErrorCode.OPERATING_ENTITY_NOT_FOUND
        ));
    auditService.recordOperatingEntityDeleted(existing, deleted, operator);
    return deleted;
}
~~~

Add audit operation/method:

~~~java
static final String OPERATION_OPERATING_ENTITY_DELETE = "platform.tenant.operating_entity.delete";

public void recordOperatingEntityDeleted(
    PlatformOperatingEntity before,
    PlatformOperatingEntity after,
    PlatformOperator operator
) {
    Map<String, Object> metadata = operatingEntityMetadata(after);
    metadata.put("previous", operatingEntityMetadata(before));
    metadata.put("deleted", true);
    append(OPERATION_OPERATING_ENTITY_DELETE, TARGET_OPERATING_ENTITY, after.id(), operator, metadata);
}
~~~

- [ ] **Step 7: Run backend tests**

~~~powershell
mvn -q "-Dtest=PlatformTenantApiIntegrationTest,PlatformTenantLocalRuntimeSecurityTest" test
git diff --check
~~~

Expected: both classes PASS; whitespace check prints nothing.

- [ ] **Step 8: Commit backend slice**

~~~powershell
git add -- docs/api/PLATFORM_TENANT_API_CONTRACT.md src/main/java/com/rpb/reservation/platform src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java src/test/java/com/rpb/reservation/platform/api/PlatformTenantLocalRuntimeSecurityTest.java
git commit -m "feat: guard operating entity deletion"
~~~

---

### Task 2: Add Behaviour-Tested Delete Eligibility To The Structure Panel

**Files:**
- Create: `src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts`
- Modify: `src/components/platform/PlatformTenantStructurePanel.vue`

**Interfaces:**
- Consumes: current `PlatformOperatingEntity[]`, `PlatformStore[]`, `saving`.
- Produces: `deleteOperatingEntity: [entity: PlatformOperatingEntity]` and an eligible danger action.

- [ ] **Step 1: Write failing component behaviour spec**

Create:

~~~ts
import { mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'

import type { PlatformOperatingEntity, PlatformStore } from '../../../api/platformApi'
import PlatformTenantStructurePanel from '../PlatformTenantStructurePanel.vue'

const EMPTY_ENTITY: PlatformOperatingEntity = {
  id: 'entity-empty', tenantId: 'tenant-1', entityCode: 'empty', displayName: 'Empty Entity',
  status: 'active', defaultLocale: 'en-SG', contactPhone: null, address: null,
  principalName: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}
const USED_ENTITY: PlatformOperatingEntity = {
  ...EMPTY_ENTITY, id: 'entity-used', entityCode: 'used', displayName: 'Used Entity'
}
const USED_STORE: PlatformStore = {
  id: 'store-1', tenantId: 'tenant-1', operatingEntityId: USED_ENTITY.id,
  operatingEntityCode: USED_ENTITY.entityCode, operatingEntityName: USED_ENTITY.displayName,
  storeCode: 'store-1', storeName: 'Used Store', status: 'active', timezone: 'Asia/Singapore',
  locale: 'en-SG', dateFormat: 'DD-MM-YYYY', timeFormat: 'HH:mm', currency: 'SGD',
  deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}

function mountPanel(): VueWrapper {
  const i18n = createI18n({ legacy: false, locale: 'en-SG', messages: { 'en-SG': {} } })
  return mount(PlatformTenantStructurePanel, {
    props: {
      operatingEntities: [EMPTY_ENTITY, USED_ENTITY],
      stores: [USED_STORE],
      adminStoreOptions: [],
      adminStoreIds: [],
      defaultAdminStoreId: '',
      saving: false
    },
    global: { plugins: [i18n] }
  })
}

describe('PlatformTenantStructurePanel operating entity deletion', () => {
  it('renders and emits delete only for an entity without current stores', async () => {
    const wrapper = mountPanel()
    const rows = wrapper.findAll('.structure-entity-row')
    expect(rows[0].find('.text-button.danger').exists()).toBe(true)
    expect(rows[1].find('.text-button.danger').exists()).toBe(false)
    await rows[0].get('.text-button.danger').trigger('click')
    expect(wrapper.emitted('deleteOperatingEntity')?.[0]).toEqual([EMPTY_ENTITY])
    await wrapper.setProps({ saving: true })
    expect(wrapper.findAll('.structure-entity-row')[0].get('.text-button.danger').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('closes a removed entity edit form and retains add in the empty state', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.structure-entity-row')[0].get('.row-actions .text-button').trigger('click')
    expect(wrapper.find('.inline-form').exists()).toBe(true)
    await wrapper.setProps({ operatingEntities: [], stores: [] })
    expect(wrapper.find('.inline-form').exists()).toBe(false)
    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.find('.structure-section .secondary-button').exists()).toBe(true)
    wrapper.unmount()
  })

  it('selects the next entity when the selected entity disappears', async () => {
    const wrapper = mountPanel()
    await wrapper.setProps({ operatingEntities: [USED_ENTITY] })
    expect(wrapper.get('.structure-entity-button').classes()).toContain('structure-entity-button--active')
    expect(wrapper.get('.structure-entity-button').text()).toContain('Used Entity')
    wrapper.unmount()
  })
})
~~~

- [ ] **Step 2: Run red test**

~~~powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts
~~~

Expected: FAIL because the delete event/action and stale-form cleanup do not exist.

- [ ] **Step 3: Implement component behaviour**

Add:

~~~ts
deleteOperatingEntity: [entity: PlatformOperatingEntity]

function entityHasCurrentStores(entityId: string): boolean {
  return props.stores.some(store => store.operatingEntityId === entityId)
}

function deleteOperatingEntity(entity: PlatformOperatingEntity): void {
  emit('deleteOperatingEntity', entity)
}
~~~

At the start of the operating-entities watcher:

~~~ts
if (entityForm.id && !props.operatingEntities.some(entity => entity.id === entityForm.id)) {
  entityFormOpen.value = false
  resetEntityForm()
}
~~~

Replace the entity row edit action:

~~~vue
<div class="row-actions">
  <button class="text-button" type="button" :disabled="saving" @click="editEntity(entity)">
    {{ $t('common.actions.edit') }}
  </button>
  <button
    v-if="!entityHasCurrentStores(entity.id)"
    class="text-button danger"
    type="button"
    :disabled="saving"
    @click="deleteOperatingEntity(entity)"
  >
    {{ $t('platform.tenants.structure.actions.deleteEntity') }}
  </button>
</div>
~~~

- [ ] **Step 4: Run green test/build**

~~~powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts
npm run build
~~~

Expected: test and build PASS.

- [ ] **Step 5: Commit panel slice**

~~~powershell
git add -- src/components/platform/PlatformTenantStructurePanel.vue src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts
git commit -m "feat: show eligible entity delete action"
~~~

---

### Task 3: Wire API Client, Confirmation, Reload, And Conflict Copy

**Files:**
- Modify: `src/api/platformApi.ts`
- Modify: `src/components/platform/platformTenantUi.ts`
- Modify: `src/components/platform/PlatformTenantStructurePanel.vue`
- Modify: `src/pages/PlatformTenantFormPage.vue`
- Create: `src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`

**Interfaces:**
- Consumes: Task 1 endpoint and Task 2 event.
- Produces: `deleteOperatingEntity(...)` client, page delete flow, bilingual feedback.

- [ ] **Step 1: Write failing page behaviour spec**

Create:

~~~ts
import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { zhCN } from '../../i18n/locales/zh-CN'

const api = vi.hoisted(() => ({
  getTenant: vi.fn(),
  getTenantAdminStoreAccess: vi.fn(),
  listOperatingEntities: vi.fn(),
  listTenantStores: vi.fn(),
  deleteOperatingEntity: vi.fn()
}))

vi.mock('../../api/platformApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/platformApi')>()),
  getTenant: api.getTenant,
  getTenantAdminStoreAccess: api.getTenantAdminStoreAccess,
  listOperatingEntities: api.listOperatingEntities,
  listTenantStores: api.listTenantStores,
  deleteOperatingEntity: api.deleteOperatingEntity
}))

import { PlatformApiError, type PlatformOperatingEntity } from '../../api/platformApi'
import PlatformTenantFormPage from '../PlatformTenantFormPage.vue'

const ENTITY: PlatformOperatingEntity = {
  id: 'entity-1', tenantId: 'tenant-1', entityCode: 'entity-1', displayName: '经营主体一',
  status: 'active', defaultLocale: 'zh-CN', contactPhone: null, address: null,
  principalName: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}

const StructureStub = defineComponent({
  props: { operatingEntities: { type: Array, required: true } },
  emits: ['deleteOperatingEntity'],
  template: '<button class="delete-entity" @click="$emit(\\'deleteOperatingEntity\\', operatingEntities[0])">Delete</button>'
})
const EmptyStub = defineComponent({ template: '<div />' })
let wrapper: VueWrapper | undefined

async function mountPage(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/platform/tenants/:tenantId/edit', name: 'platform-tenant-edit', component: EmptyStub },
      { path: '/platform/tenants', name: 'platform-tenants', component: EmptyStub }
    ]
  })
  await router.push('/platform/tenants/tenant-1/edit')
  await router.isReady()
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    fallbackLocale: 'zh-CN',
    messages: { 'zh-CN': zhCN }
  })
  wrapper = mount(PlatformTenantFormPage, {
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: {
        PlatformAdminNav: EmptyStub,
        PlatformTenantForm: EmptyStub,
        PlatformTenantStructurePanel: StructureStub
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('PlatformTenantFormPage operating entity deletion', () => {
  beforeEach(() => {
    api.getTenant.mockResolvedValue({
      success: true,
      tenant: {
        id: 'tenant-1', tenantCode: 'tenant-1', displayName: 'Tenant 1', status: 'active',
        defaultLocale: 'zh-CN', contactPhone: null, address: null, principalName: null,
        logoMediaUrl: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
        updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
      }
    })
    api.getTenantAdminStoreAccess.mockResolvedValue({
      success: true, stores: [], storeIds: [], defaultStoreId: null
    })
    api.listOperatingEntities.mockResolvedValue({ success: true, operatingEntities: [ENTITY] })
    api.listTenantStores.mockResolvedValue({ success: true, stores: [] })
    api.deleteOperatingEntity.mockResolvedValue({
      success: true,
      operatingEntity: {
        ...ENTITY, status: 'archived', deleted: true,
        updatedAt: '2026-07-17T01:00:00Z', deletedAt: '2026-07-17T01:00:00Z'
      }
    })
  })

  afterEach(() => wrapper?.unmount())

  it('does not call delete when confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    expect(api.deleteOperatingEntity).not.toHaveBeenCalled()
  })

  it('deletes and reloads structure/access after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await flushPromises()
    expect(api.deleteOperatingEntity).toHaveBeenCalledWith('tenant-1', 'entity-1')
    expect(api.listOperatingEntities).toHaveBeenCalledTimes(2)
    expect(api.listTenantStores).toHaveBeenCalledTimes(2)
    expect(api.getTenantAdminStoreAccess).toHaveBeenCalledTimes(2)
  })

  it('suppresses a duplicate delete while the first request is pending', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.deleteOperatingEntity.mockImplementationOnce(() => new Promise(() => undefined))
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await page.get('.delete-entity').trigger('click')
    expect(api.deleteOperatingEntity).toHaveBeenCalledTimes(1)
  })

  it('shows the translated current-store conflict', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.deleteOperatingEntity.mockRejectedValueOnce(new PlatformApiError(409, {
      success: false,
      error: {
        code: 'OPERATING_ENTITY_HAS_STORES',
        messageKey: 'platform.tenants.operating_entity_has_stores',
        details: {}
      }
    }))
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toBe('该经营主体仍有门店，无法删除')
  })
})
~~~

- [ ] **Step 2: Run red page test**

~~~powershell
npx vitest run src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts
~~~

Expected: FAIL because client, page handler, and copy do not exist.

- [ ] **Step 3: Add precise API types/client**

In `platformApi.ts`:

~~~ts
export type PlatformOperatingEntityStatus = 'active' | 'inactive' | 'archived'
export type PlatformOperatingEntityMutableStatus = Exclude<PlatformOperatingEntityStatus, 'archived'>
~~~

Use the full status on response entities and mutable status on mutations/forms. Add:

~~~ts
export async function deleteOperatingEntity(
  tenantId: string,
  operatingEntityId: string,
  fetcher?: PlatformFetcher
): Promise<PlatformOperatingEntityResponse> {
  return requestJson(
    '/api/v1/platform/tenants/' + encodeURIComponent(tenantId)
      + '/operating-entities/' + encodeURIComponent(operatingEntityId),
    { method: 'DELETE', fetcher }
  )
}
~~~

In `platformTenantUi.ts`, import/use `PlatformOperatingEntityMutableStatus` for `PlatformOperatingEntityFormModel.status`. In `PlatformTenantStructurePanel.editEntity`, narrow an archived response before assigning it to the editable form:

~~~ts
status: entity.status === 'archived' ? 'inactive' : entity.status,
~~~

- [ ] **Step 4: Implement page flow/error mapping**

Import the client, add `@delete-operating-entity="deleteEntity"`, and add:

~~~ts
async function deleteEntity(entity: PlatformOperatingEntity): Promise<void> {
  if (structureSaving.value || mode.value !== 'edit') {
    return
  }
  const entityName = entity.displayName || entity.entityCode
  if (!window.confirm(t('platform.tenants.structure.actions.confirmDeleteEntity', { entityName }))) {
    return
  }

  structureSaving.value = true
  errorText.value = ''
  try {
    await deleteOperatingEntity(tenantId.value, entity.id)
    await reloadStructureAndAccess()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    structureSaving.value = false
  }
}
~~~

Add to `apiErrorText`:

~~~ts
if (error.response.error.code === 'OPERATING_ENTITY_HAS_STORES') {
  return t('platform.tenants.errors.operatingEntityHasStores')
}
~~~

- [ ] **Step 5: Add aligned locale keys**

Chinese:

~~~ts
operatingEntityHasStores: '该经营主体仍有门店，无法删除'
noEntities: '暂无经营主体，请先新增经营主体。'
deleteEntity: '删除'
confirmDeleteEntity: '删除经营主体 {entityName}？历史记录将保留，删除后不可在当前列表恢复。'
archived: '已归档'
~~~

English:

~~~ts
operatingEntityHasStores: 'This operating entity still has stores and cannot be deleted'
noEntities: 'No operating entities. Add an operating entity first.'
deleteEntity: 'Delete'
confirmDeleteEntity: 'Delete operating entity {entityName}? History is retained and the entity cannot be restored from this list.'
archived: 'Archived'
~~~

Replace the guide `noEntities` text so final-entity deletion does not falsely promise an automatic default.

- [ ] **Step 6: Run green frontend tests/build**

~~~powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts
npm run build
~~~

Expected: both specs and build PASS.

- [ ] **Step 7: Commit client/page slice**

~~~powershell
git add -- src/api/platformApi.ts src/components/platform/platformTenantUi.ts src/components/platform/PlatformTenantStructurePanel.vue src/pages/PlatformTenantFormPage.vue src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts src/i18n/locales/zh-CN.ts src/i18n/locales/en-SG.ts
git commit -m "feat: delete empty operating entities"
~~~

---

### Task 4: Add Source Guard, Release Note, And Final Verification

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`
- Create: `docs/release-notes/2026-07-17-operating-entity-delete.md`

**Interfaces:**
- Consumes: completed backend/frontend slices.
- Produces: source guard, release/rollback record, final evidence.

- [ ] **Step 1: Extend source wiring guard**

Add to `platformTenantEditFormMaintainsTenantAdminStoreAccess`:

~~~java
assertThat(formPageSource)
    .contains("@delete-operating-entity=\"deleteEntity\"")
    .contains("deleteOperatingEntity")
    .contains("async function deleteEntity")
    .contains("confirmDeleteEntity")
    .contains("OPERATING_ENTITY_HAS_STORES");

assertThat(structureSource)
    .contains("deleteOperatingEntity: [entity: PlatformOperatingEntity]")
    .contains("entityHasCurrentStores")
    .contains("v-if=\"!entityHasCurrentStores(entity.id)\"")
    .contains("emit('deleteOperatingEntity', entity)");

assertThat(zh)
    .contains("deleteEntity: '删除'")
    .contains("operatingEntityHasStores: '该经营主体仍有门店，无法删除'");

assertThat(en)
    .contains("deleteEntity: 'Delete'")
    .contains("operatingEntityHasStores: 'This operating entity still has stores and cannot be deleted'");
~~~

- [ ] **Step 2: Run source guard**

~~~powershell
mvn -q "-Dtest=AuthLoginUiValidationTest" test
~~~

Expected: PASS.

- [ ] **Step 3: Write release note**

Create:

~~~markdown
# Operating Entity Delete

## Version / Date

2026-07-17

## Changed

- Platform administrators can delete an operating entity when it has no current stores.
- Soft-deleted historical stores do not block deletion.
- The final no-store operating entity may be deleted; the add action remains available.

## API

- Added `DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}`.
- Current stores return HTTP 409 with `OPERATING_ENTITY_HAS_STORES`.
- Success archives and soft-deletes the entity and writes an audit record.

## Permission And Security

- Requires `platform_admin` and `platform.tenant.manage`.
- Tenant scoping and cross-tenant not-found behaviour are preserved.
- Row locking serializes deletion against store assignment.

## Deployment

- No database migration, dependency, or runtime configuration change is required.
- Backend and frontend may deploy together; the endpoint is additive.

## Validation

- Focused platform tenant API integration tests passed.
- Local runtime security and frontend source guards passed.
- Vue behaviour tests and frontend production build passed.
- Backend package build passed.

## Rollback Notes

- Remove the frontend action and additive endpoint.
- Previously soft-deleted entities remain valid history; no schema/data rollback is required.
~~~

- [ ] **Step 4: Run proportional verification**

~~~powershell
npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts
npm run build
mvn -q "-Dtest=PlatformTenantApiIntegrationTest,PlatformTenantLocalRuntimeSecurityTest,AuthLoginUiValidationTest" test
mvn -q -DskipTests package
git diff --check
git status --short
~~~

Expected: all tests/builds PASS, whitespace check is empty, and only source-guard/release-note changes remain before commit.

- [ ] **Step 5: Run required post-implementation reviews**

Use `tdd-review` against fresh output and require every matrix row to be green. Use `api-review` again and verify contract, permission, error, replay, compatibility, tenant scope, and Vue error states. Use `code-review` over the complete diff and resolve every correctness, security, concurrency, and maintainability finding.

- [ ] **Step 6: Commit release slice**

~~~powershell
git add -- src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java docs/release-notes/2026-07-17-operating-entity-delete.md
git commit -m "docs: release operating entity deletion"
~~~

- [ ] **Step 7: Confirm final state**

~~~powershell
git status --short
git log -6 --oneline
~~~

Expected: clean worktree; latest commits are release/source guard, page/client, panel, backend, plan, and approved design.
