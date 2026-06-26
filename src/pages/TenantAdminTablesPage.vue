<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  exportTables,
  importTables,
  listTables,
  TenantAdminApiError,
  type TenantAdminPage,
  type TenantAdminTable
} from '../api/tenantAdminApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const tables = ref<TenantAdminTable[]>([])
const loading = ref(false)
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

async function downloadTables(): Promise<void> {
  if (loading.value) {
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
  if (!file || loading.value) {
    return
  }

  loading.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const response = await importTables(storeId.value, file)
    const message = `导入完成：新增 ${response.imported.created}，覆盖 ${response.imported.updated}`
    await loadTables(0)
    successText.value = message
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查 Excel 表头、分区组、桌号、人数和启用状态'
  }
  return '操作失败'
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>租户</span>
          <h1>桌号管理</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        placeholder="分区组 / 桌号"
        :loading="loading"
        :has-dirty-query="hasDirtyQuery"
        @search="searchTables"
        @reset="resetFilters"
        @refresh="loadTables()"
      >
        <template #filters>
          <div class="summary-strip">
            <strong>全部 {{ safePage.total }}</strong>
            <span>启用 {{ enabledCount }}</span>
            <span>停用 {{ disabledCount }}</span>
          </div>
        </template>
        <template #actions>
          <button class="secondary-button" type="button" :disabled="loading" @click="downloadTables">
            导出 Excel
          </button>
          <button class="secondary-button" type="button" :disabled="loading" @click="pickImportFile">
            导入 Excel
          </button>
          <input
            ref="fileInput"
            class="hidden-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            @change="uploadTables"
          />
          <button class="primary-button" type="button" @click="openCreatePage">
            新增桌号
          </button>
        </template>
      </ErpQueryToolbar>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="successText" class="success-banner" role="status">{{ successText }}</p>

      <div class="erp-table-wrap">
        <table>
          <thead>
            <tr>
              <th>大类排序</th>
              <th>分区组</th>
              <th>桌号排序</th>
              <th>桌号</th>
              <th>人数</th>
              <th>启用</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="empty-cell">加载中</td>
            </tr>
            <tr v-else-if="tables.length === 0">
              <td colspan="7" class="empty-cell">暂无桌号</td>
            </tr>
            <tr v-for="item in tables" v-else :key="item.id">
              <td>{{ item.areaSortOrder }}</td>
              <td>{{ item.areaName }}</td>
              <td>{{ item.tableSortOrder }}</td>
              <td><strong>{{ item.tableCode }}</strong></td>
              <td>{{ item.capacity }}</td>
              <td>
                <span class="status-pill" :class="{ muted: !item.enabled }">
                  {{ item.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <button type="button" class="link-button" @click="openEditPage(item)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <ErpPagination
        :limit="safePage.limit"
        :offset="safePage.offset"
        :total="safePage.total"
        :loading="loading"
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

.secondary-button:disabled {
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
