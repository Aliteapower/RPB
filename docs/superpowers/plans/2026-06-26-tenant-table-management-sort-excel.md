# Tenant Table Management Sort Excel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist area and table sort order for tenant table management and add reusable Excel import/export with overwrite-by-table-code behavior.

**Architecture:** Add a focused `common.excel` package for workbook mechanics, keep tenant table overwrite and normalization in tenant-admin application services, and keep all SQL store-scoped in tenant-admin persistence. Frontend changes stay in the existing tenant admin table API module and pages.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/Flyway, Apache POI for `.xlsx`, Vue 3, TypeScript, Vite.

---

## File Structure

- Create `src/main/resources/db/migration/V011__tenant_table_sort_order.sql` for `dining_tables.sort_order`.
- Modify `pom.xml` to add Apache POI.
- Create `src/main/java/com/rpb/reservation/common/excel/ExcelColumn.java`.
- Create `src/main/java/com/rpb/reservation/common/excel/ExcelRow.java`.
- Create `src/main/java/com/rpb/reservation/common/excel/ExcelWorkbookService.java`.
- Create `src/main/java/com/rpb/reservation/common/excel/PoiExcelWorkbookService.java`.
- Modify tenant-admin DTOs, command records, service, and repository for sort fields.
- Create tenant-admin import/export response and summary records.
- Modify `TenantAdminController` for import/export endpoints.
- Modify `src/api/tenantAdminApi.ts`, `TenantAdminTablesPage.vue`, and `TenantAdminTableFormPage.vue`.
- Update `docs/api/TENANT_ADMIN_ERP_API_CONTRACT.md`.

### Task 1: Backend Failing Tests

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`

- [ ] **Step 1: Add tests for sorted list, export, and import overwrite**

Add tests that call:

```text
POST /api/v1/stores/{storeId}/tenant-admin/tables
GET  /api/v1/stores/{storeId}/tenant-admin/tables
GET  /api/v1/stores/{storeId}/tenant-admin/tables/export
POST /api/v1/stores/{storeId}/tenant-admin/tables/import
```

The tests must assert:

```text
sorted list returns areaSortOrder and tableSortOrder
export response has xlsx content type and attachment filename
import updates existing CX01 and creates CX99
```

- [ ] **Step 2: Run backend test and verify RED**

Run:

```powershell
.\mvnw -Dtest=TenantAdminApiIntegrationTest test
```

Expected: compilation fails because new request fields, DTO fields, endpoints, and Excel helpers do not exist.

### Task 2: Database and Backend Green

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V011__tenant_table_sort_order.sql`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/entity/DiningTableEntity.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTable.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTableMutationCommand.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminTableMutationRequest.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminTableItemResponse.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/persistence/TenantAdminTableRepository.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTableService.java`

- [ ] **Step 1: Add `sort_order` schema**

Migration content:

```sql
alter table dining_tables
    add column if not exists sort_order integer not null default 0;

create index if not exists ix_dining_tables_area_sort
    on dining_tables (tenant_id, store_id, area_id, sort_order, table_code)
    where deleted_at is null;
```

- [ ] **Step 2: Add sort fields through DTOs and repository SQL**

Propagate:

```text
areaId
areaSortOrder
tableSortOrder
```

Repository ordering:

```sql
order by area.sort_order, area.display_name, table_record.sort_order, table_record.table_code
```

- [ ] **Step 3: Run backend test and verify sorted-list part is GREEN**

Run:

```powershell
.\mvnw -Dtest=TenantAdminApiIntegrationTest test
```

Expected: export/import tests still fail because endpoints are missing, sorted list assertions pass once endpoint compilation is complete.

### Task 3: Common Excel Module and Import/Export Endpoints

**Files:**
- Create: `src/main/java/com/rpb/reservation/common/excel/ExcelColumn.java`
- Create: `src/main/java/com/rpb/reservation/common/excel/ExcelRow.java`
- Create: `src/main/java/com/rpb/reservation/common/excel/ExcelWorkbookService.java`
- Create: `src/main/java/com/rpb/reservation/common/excel/PoiExcelWorkbookService.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTableImportSummary.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTableExcelRow.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminTableImportResponse.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminTableImportSummaryResponse.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminTableService.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/persistence/TenantAdminTableRepository.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java`

- [ ] **Step 1: Implement reusable workbook write/read**

Use Apache POI to write one sheet with headers and read rows by header name.

- [ ] **Step 2: Implement export**

Export current sorted list with columns:

```text
大类排序, 桌号排序, 分区组, 桌号, 人数, 启用
```

- [ ] **Step 3: Implement import overwrite**

For each row, normalize values and call repository upsert by `(tenant_id, store_id, table_code)`.

- [ ] **Step 4: Run backend test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=TenantAdminApiIntegrationTest test
```

Expected: all tenant admin API tests pass.

### Task 4: Frontend API and UI

**Files:**
- Modify: `src/api/tenantAdminApi.ts`
- Modify: `src/pages/TenantAdminTablesPage.vue`
- Modify: `src/pages/TenantAdminTableFormPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/TemporaryTableGroupUiValidationTest.java` or add a focused UI validation test.

- [ ] **Step 1: Add TypeScript API methods**

Add:

```ts
exportTables(storeId: string): Promise<Blob>
importTables(storeId: string, file: File): Promise<TenantAdminTableImportResponse>
```

- [ ] **Step 2: Add list actions and sort columns**

Show export/import buttons and columns:

```text
大类排序
桌号排序
```

- [ ] **Step 3: Add form sort inputs**

Add numeric inputs bound to:

```text
areaSortOrder
tableSortOrder
```

- [ ] **Step 4: Run frontend build**

Run:

```powershell
npm run build
```

Expected: TypeScript and Vite build pass.

### Task 5: Documentation and Reviews

**Files:**
- Modify: `docs/api/TENANT_ADMIN_ERP_API_CONTRACT.md`

- [ ] **Step 1: Update API contract**

Document new sort fields, export endpoint, import endpoint, Excel columns, and overwrite rule.

- [ ] **Step 2: Run full verification**

Run:

```powershell
.\mvnw test
npm run build
```

Expected: backend tests and frontend build pass.

- [ ] **Step 3: Complete required reviews**

Use RPB database-review, api-review, tdd-review, code-review, ui-review, and release-note checklists before final response.

## Self-Review

Spec coverage: The plan covers persisted sorting, API surface, Excel import/export, frontend integration, documentation, and verification.

Placeholder scan: No task contains undefined endpoints, unspecified files, or open behavior.

Type consistency: Sort field names are consistently `areaSortOrder` and `tableSortOrder`; import identity is consistently `tableCode`.
