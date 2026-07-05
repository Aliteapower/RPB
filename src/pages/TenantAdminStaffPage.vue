<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getCurrentTenantAdminStaff,
  getTenantProfile,
  listStaff,
  TenantAdminApiError,
  type TenantAdminPage,
  type TenantAdminStaff
} from '../api/tenantAdminApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const staff = ref<TenantAdminStaff[]>([])
const selfAdminStaff = ref<TenantAdminStaff | null>(null)
const tenantContactPhone = ref('')
const loading = ref(false)
const errorText = ref('')
const keyword = ref('')
const limit = ref(20)
const offset = ref(0)
const page = ref<TenantAdminPage>({
  limit: 20,
  offset: 0,
  total: 0
})

const storeId = computed(() => String(route.params.storeId || ''))
const hasDirtyQuery = computed(() => keyword.value.trim() !== '')
const visibleStaff = computed(() => (selfAdminStaff.value ? [selfAdminStaff.value, ...staff.value] : staff.value))
const enabledCount = computed(() => visibleStaff.value.filter(item => item.status === 'active').length)
const disabledCount = computed(() => visibleStaff.value.filter(item => item.status !== 'active').length)
const safePage = computed<TenantAdminPage>(() => page.value ?? {
  limit: limit.value,
  offset: offset.value,
  total: staff.value.length
})

onMounted(() => {
  void loadStaff()
})

async function loadStaff(nextOffset = offset.value): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [selfResponse, profileResponse, response] = await Promise.all([
      getCurrentTenantAdminStaff(storeId.value),
      getTenantProfile(storeId.value),
      listStaff(storeId.value, {
        keyword: keyword.value,
        limit: limit.value,
        offset: nextOffset
      })
    ])
    selfAdminStaff.value = selfResponse.staff
    tenantContactPhone.value = profileResponse.profile.contactPhone || ''
    staff.value = response.staff
    page.value = response.page
    offset.value = response.page.offset
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function searchStaff(): void {
  offset.value = 0
  void loadStaff(0)
}

function resetFilters(): void {
  keyword.value = ''
  offset.value = 0
  void loadStaff(0)
}

function openCreatePage(): void {
  void router.push({ name: 'tenant-admin-staff-create', params: { storeId: storeId.value } })
}

function openEditPage(item: TenantAdminStaff): void {
  if (item.accountType === 'tenant_admin' && item.self) {
    void router.push({ name: 'tenant-admin-staff-self-edit', params: { storeId: storeId.value } })
    return
  }
  void router.push({ name: 'tenant-admin-staff-edit', params: { storeId: storeId.value, staffId: item.id } })
}

function statusLabel(status: TenantAdminStaff['status']): string {
  if (status === 'active') {
    return '启用'
  }
  if (status === 'locked') {
    return '锁定'
  }
  return '停用'
}

function displayStaffPhone(item: TenantAdminStaff): string {
  const phone = item.accountType === 'tenant_admin'
    ? tenantContactPhone.value || item.phone
    : item.phone
  return phone || '-'
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
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
          <h1>员工管理</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        placeholder="员工号 / 姓名 / 电话 / 电邮"
        :loading="loading"
        :has-dirty-query="hasDirtyQuery"
        @search="searchStaff"
        @reset="resetFilters"
        @refresh="loadStaff()"
      >
        <template #filters>
          <div class="summary-strip">
            <strong>员工 {{ safePage.total }}</strong>
            <span v-if="selfAdminStaff">管理员 1</span>
            <span>启用 {{ enabledCount }}</span>
            <span>停用 {{ disabledCount }}</span>
          </div>
        </template>
        <template #actions>
          <button class="primary-button" type="button" @click="openCreatePage">
            新增员工
          </button>
        </template>
      </ErpQueryToolbar>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>

      <div class="erp-table-wrap">
        <table>
          <thead>
            <tr>
              <th>员工号</th>
              <th>姓名</th>
              <th>电话</th>
              <th>电邮</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="empty-cell">加载中</td>
            </tr>
            <tr v-else-if="visibleStaff.length === 0">
              <td colspan="6" class="empty-cell">暂无员工</td>
            </tr>
            <tr v-for="item in visibleStaff" v-else :key="item.id" :class="{ 'protected-row': item.accountType === 'tenant_admin' }">
              <td><strong>{{ item.employeeNo }}</strong></td>
              <td>
                <span class="name-cell">
                  {{ item.name }}
                  <span v-if="item.accountType === 'tenant_admin'" class="role-badge">管理员</span>
                </span>
              </td>
              <td>{{ displayStaffPhone(item) }}</td>
              <td>{{ item.email || '-' }}</td>
              <td>
                <span class="status-pill" :class="{ muted: item.status !== 'active' }">
                  {{ statusLabel(item.status) }}
                </span>
              </td>
              <td>
                <button v-if="item.editable" type="button" class="link-button" @click="openEditPage(item)">编辑</button>
                <span v-else>-</span>
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
        @change="loadStaff"
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

.primary-button {
  min-height: 36px;
  border: 0;
  border-radius: 6px;
  padding: 0 14px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.error-banner {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fff1f2;
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
  min-width: 760px;
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

td {
  color: #102033;
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

.protected-row td {
  background: #f0fdfa;
}

.name-cell {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.role-badge {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #99f6e4;
  border-radius: 999px;
  padding: 0 8px;
  color: #0f766e;
  background: #ccfbf1;
  font-size: 12px;
  font-weight: 800;
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
  .primary-button {
    width: 100%;
  }
}
</style>
