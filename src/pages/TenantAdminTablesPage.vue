<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  exportTables,
  importTables,
  listTables,
  TenantAdminApiError,
  type TenantAdminPage,
  type TenantAdminTable,
  type TenantAdminTableMutation,
  updateTable
} from '../api/tenantAdminApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const tables = ref<TenantAdminTable[]>([])
const loading = ref(false)
const sorting = ref(false)
const errorText = ref('')
const successText = ref('')
const keyword = ref('')
const limit = ref(20)
const offset = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const page = ref<TenantAdminPage>({
  limit: 20,
  offset: 0,
  total: 0
})

const storeId = computed(() => String(route.params.storeId || ''))
const hasDirtyQuery = computed(() => keyword.value.trim() !== '')
const enabledCount = computed(() => tables.value.filter(item => item.enabled).length)
const disabledCount = computed(() => tables.value.filter(item => !item.enabled).length)
const safePage = computed<TenantAdminPage>(() => page.value ?? {
  limit: limit.value,
  offset: offset.value,
  total: tables.value.length
})
const actionDisabled = computed(() => loading.value || sorting.value)
const areaSortGroups = computed<AreaSortGroup[]>(() => {
  const groups = new Map<string, AreaSortGroup>()
  tables.value.forEach(item => {
    if (!groups.has(item.areaId)) {
      groups.set(item.areaId, {
        areaId: item.areaId,
        areaName: item.areaName,
        sortOrder: item.areaSortOrder,
        representative: item
      })
    }
  })
  return Array.from(groups.values()).sort(compareAreaGroups)
})

interface AreaSortGroup {
  areaId: string
  areaName: string
  sortOrder: number
  representative: TenantAdminTable
}

type SortDirection = -1 | 1

interface TableSortMutation {
  item: TenantAdminTable
  areaSortOrder: number
  tableSortOrder: number
}

onMounted(() => {
  void loadTables()
})

async function loadTables(nextOffset = offset.value): Promise<void> {
  loading.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const response = await listTables(storeId.value, {
      keyword: keyword.value,
      limit: limit.value,
      offset: nextOffset
    })
    tables.value = response.tables
    page.value = response.page
    offset.value = response.page.offset
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function searchTables(): void {
  offset.value = 0
  void loadTables(0)
}

function resetFilters(): void {
  keyword.value = ''
  offset.value = 0
  void loadTables(0)
}

function openCreatePage(): void {
  void router.push({ name: 'tenant-admin-table-create', params: { storeId: storeId.value } })
}

function openEditPage(item: TenantAdminTable): void {
  void router.push({ name: 'tenant-admin-table-edit', params: { storeId: storeId.value, tableId: item.id } })
}

function canMoveArea(item: TenantAdminTable, direction: SortDirection): boolean {
  if (actionDisabled.value) {
    return false
  }
  const index = areaGroupIndex(item)
  return direction < 0 ? index > 0 : index >= 0 && index < areaSortGroups.value.length - 1
}

function canMoveTable(item: TenantAdminTable, direction: SortDirection): boolean {
  if (actionDisabled.value) {
    return false
  }
  const siblings = sortedAreaTables(item)
  const index = siblings.findIndex(candidate => candidate.id === item.id)
  return direction < 0 ? index > 0 : index >= 0 && index < siblings.length - 1
}

async function moveArea(item: TenantAdminTable, direction: SortDirection): Promise<void> {
  const groups = areaSortGroups.value
  const index = areaGroupIndex(item)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= groups.length || sorting.value) {
    return
  }

  const reordered = groups.slice()
  const [moved] = reordered.splice(index, 1)
  reordered.splice(targetIndex, 0, moved)
  await persistSortMutations(
    reordered.map((group, nextSortOrder) => ({
      item: group.representative,
      areaSortOrder: nextSortOrder,
      tableSortOrder: group.representative.tableSortOrder
    }))
  )
}

async function moveTable(item: TenantAdminTable, direction: SortDirection): Promise<void> {
  const siblings = sortedAreaTables(item)
  const index = siblings.findIndex(candidate => candidate.id === item.id)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= siblings.length || sorting.value) {
    return
  }

  const reordered = siblings.slice()
  const [moved] = reordered.splice(index, 1)
  reordered.splice(targetIndex, 0, moved)
  await persistSortMutations(
    reordered.map((candidate, nextSortOrder) => ({
      item: candidate,
      areaSortOrder: candidate.areaSortOrder,
      tableSortOrder: nextSortOrder
    }))
  )
}

async function downloadTables(): Promise<void> {
  if (actionDisabled.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const blob = await exportTables(storeId.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'tenant-admin-tables.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function pickImportFile(): void {
  fileInput.value?.click()
}

async function uploadTables(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || actionDisabled.value) {
    return
  }

  loading.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const response = await importTables(storeId.value, file)
    const message = `${gt('generated.tenant-admin-tables.032')}${response.imported.created}${gt('generated.tenant-admin-tables.033')}${response.imported.updated}`
    await loadTables(0)
    successText.value = message
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function persistSortMutations(mutations: TableSortMutation[]): Promise<void> {
  const changed = mutations.filter(mutation =>
    mutation.item.areaSortOrder !== mutation.areaSortOrder
      || mutation.item.tableSortOrder !== mutation.tableSortOrder
  )
  if (changed.length === 0) {
    return
  }

  sorting.value = true
  errorText.value = ''
  successText.value = ''
  try {
    for (const mutation of changed) {
      await updateTable(
        storeId.value,
        mutation.item.id,
        tableSortPayload(mutation.item, mutation.areaSortOrder, mutation.tableSortOrder)
      )
    }
    const message = gt('generated.tenant-admin-tables.034')
    await loadTables(offset.value)
    successText.value = message
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    sorting.value = false
  }
}

function tableSortPayload(
  item: TenantAdminTable,
  areaSortOrder: number,
  tableSortOrder: number
): TenantAdminTableMutation {
  return {
    areaName: item.areaName,
    tableCode: item.tableCode,
    capacity: item.capacity,
    enabled: item.enabled,
    areaSortOrder,
    tableSortOrder
  }
}

function areaGroupIndex(item: TenantAdminTable): number {
  return areaSortGroups.value.findIndex(group => group.areaId === item.areaId)
}

function sortedAreaTables(item: TenantAdminTable): TenantAdminTable[] {
  return tables.value
    .filter(candidate => candidate.areaId === item.areaId)
    .slice()
    .sort(compareTablesInArea)
}

function compareAreaGroups(left: AreaSortGroup, right: AreaSortGroup): number {
  return left.sortOrder - right.sortOrder
    || left.areaName.localeCompare(right.areaName, 'zh-Hans-CN')
    || left.areaId.localeCompare(right.areaId)
}

function compareTablesInArea(left: TenantAdminTable, right: TenantAdminTable): number {
  return left.tableSortOrder - right.tableSortOrder
    || left.tableCode.localeCompare(right.tableCode, 'zh-Hans-CN')
    || left.id.localeCompare(right.id)
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-tables.035')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-tables.036')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-tables.037')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-tables.038')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-tables.039')
  }
  if (error.response.error.code === 'TABLE_IN_USE') {
    return gt('generated.tenant-admin-tables.040')
  }
  return gt('generated.tenant-admin-tables.041')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-tables.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-tables.002') }}</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        :placeholder="gt('generated.tenant-admin-tables.003')"
        :loading="actionDisabled"
        :has-dirty-query="hasDirtyQuery"
        @search="searchTables"
        @reset="resetFilters"
        @refresh="loadTables()"
      >
        <template #filters>
          <div class="summary-strip">
            <strong>{{ gt('generated.tenant-admin-tables.004') }} {{ safePage.total }}</strong>
            <span>{{ gt('generated.tenant-admin-tables.005') }} {{ enabledCount }}</span>
            <span>{{ gt('generated.tenant-admin-tables.006') }} {{ disabledCount }}</span>
          </div>
        </template>
        <template #actions>
          <button class="secondary-button" type="button" :disabled="actionDisabled" @click="downloadTables"> {{ gt('generated.tenant-admin-tables.007') }} </button>
          <button class="secondary-button" type="button" :disabled="actionDisabled" @click="pickImportFile"> {{ gt('generated.tenant-admin-tables.008') }} </button>
          <input
            ref="fileInput"
            class="hidden-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            @change="uploadTables"
          />
          <button class="primary-button" type="button" :disabled="actionDisabled" @click="openCreatePage"> {{ gt('generated.tenant-admin-tables.009') }} </button>
        </template>
      </ErpQueryToolbar>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="successText" class="success-banner" role="status">{{ successText }}</p>

      <div class="erp-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ gt('generated.tenant-admin-tables.010') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.011') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.012') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.013') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.014') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.015') }}</th>
              <th>{{ gt('generated.tenant-admin-tables.016') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="empty-cell">{{ gt('generated.tenant-admin-tables.017') }}</td>
            </tr>
            <tr v-else-if="tables.length === 0">
              <td colspan="7" class="empty-cell">{{ gt('generated.tenant-admin-tables.018') }}</td>
            </tr>
            <tr v-for="item in tables" v-else :key="item.id">
              <td>
                <div class="sort-cell">
                  <span class="sort-value">{{ item.areaSortOrder }}</span>
                  <span class="sort-controls" :aria-label="gt('generated.tenant-admin-tables.019')">
                    <button
                      class="sort-icon-button"
                      type="button"
                      :title="gt('generated.tenant-admin-tables.020')"
                      :aria-label="`${item.areaName}${gt('generated.tenant-admin-tables.021')}`"
                      :disabled="!canMoveArea(item, -1)"
                      @click="moveArea(item, -1)"
                    >
                      <span aria-hidden="true">↑</span>
                    </button>
                    <button
                      class="sort-icon-button"
                      type="button"
                      :title="gt('generated.tenant-admin-tables.022')"
                      :aria-label="`${item.areaName}${gt('generated.tenant-admin-tables.023')}`"
                      :disabled="!canMoveArea(item, 1)"
                      @click="moveArea(item, 1)"
                    >
                      <span aria-hidden="true">↓</span>
                    </button>
                  </span>
                </div>
              </td>
              <td>{{ item.areaName }}</td>
              <td>
                <div class="sort-cell">
                  <span class="sort-value">{{ item.tableSortOrder }}</span>
                  <span class="sort-controls" :aria-label="gt('generated.tenant-admin-tables.024')">
                    <button
                      class="sort-icon-button"
                      type="button"
                      :title="gt('generated.tenant-admin-tables.025')"
                      :aria-label="`${item.tableCode}${gt('generated.tenant-admin-tables.026')}`"
                      :disabled="!canMoveTable(item, -1)"
                      @click="moveTable(item, -1)"
                    >
                      <span aria-hidden="true">↑</span>
                    </button>
                    <button
                      class="sort-icon-button"
                      type="button"
                      :title="gt('generated.tenant-admin-tables.027')"
                      :aria-label="`${item.tableCode}${gt('generated.tenant-admin-tables.028')}`"
                      :disabled="!canMoveTable(item, 1)"
                      @click="moveTable(item, 1)"
                    >
                      <span aria-hidden="true">↓</span>
                    </button>
                  </span>
                </div>
              </td>
              <td><strong>{{ item.tableCode }}</strong></td>
              <td>{{ item.capacity }}</td>
              <td>
                <span class="status-pill" :class="{ muted: !item.enabled }">
                  {{ item.enabled ? gt('generated.tenant-admin-tables.029') : gt('generated.tenant-admin-tables.030') }}
                </span>
              </td>
              <td>
                <button type="button" class="link-button" @click="openEditPage(item)">{{ gt('generated.tenant-admin-tables.031') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <ErpPagination
        :limit="safePage.limit"
        :offset="safePage.offset"
        :total="safePage.total"
        :loading="actionDisabled"
        @change="loadTables"
      />
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.summary-strip {
  min-height: 36px;
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid #cfd9e4;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
}

.summary-strip strong {
  color: #0f172a;
}

.secondary-button,
.primary-button {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #0f172a;
  background: #ffffff;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button:disabled,
.primary-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-banner {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fff1f2;
}

.success-banner {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  color: #166534;
  background: #f0fdf4;
}

.hidden-file {
  display: none;
}

.erp-table-wrap {
  overflow-x: auto;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 820px;
}

th,
td {
  padding: 14px 12px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}

th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

tr:last-child td {
  border-bottom: 0;
}

.sort-cell {
  display: inline-flex;
  min-width: 112px;
  align-items: center;
  gap: 8px;
}

.sort-value {
  min-width: 24px;
  font-variant-numeric: tabular-nums;
}

.sort-controls {
  display: inline-flex;
  gap: 4px;
}

.sort-icon-button {
  width: 26px;
  height: 26px;
  display: inline-grid;
  place-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #0f766e;
  background: #ffffff;
  font: inherit;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.sort-icon-button:disabled {
  color: #94a3b8;
  background: #f8fafc;
  cursor: default;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}

.status-pill {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  padding: 0 9px;
  color: #047857;
  background: #dcfce7;
  font-size: 12px;
  font-weight: 800;
}

.status-pill.muted {
  color: #64748b;
  background: #e2e8f0;
}

.link-button {
  border: 0;
  color: #0f766e;
  background: transparent;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

@media (max-width: 980px) {
  .tenant-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .summary-strip,
  .secondary-button,
  .primary-button {
    width: 100%;
  }
}
</style>
