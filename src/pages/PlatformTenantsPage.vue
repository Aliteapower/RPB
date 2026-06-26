<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  PlatformApiError,
  deleteTenant,
  listTenants,
  restoreTenant,
  type PlatformPage,
  type PlatformTenant,
  type TenantListStatus
} from '../api/platformApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import PlatformTenantTable from '../components/platform/PlatformTenantTable.vue'
import type { TenantStatusOption } from '../components/platform/platformTenantUi'
import { useAuthSessionStore } from '../stores/authSession'

const router = useRouter()
const route = useRoute()
const auth = useAuthSessionStore()
const tenants = ref<PlatformTenant[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const filter = ref<TenantListStatus>('all')
const keyword = ref('')
const limit = ref(20)
const offset = ref(0)
const page = ref<PlatformPage>({
  limit: 20,
  offset: 0,
  total: 0
})

const statusOptions: TenantStatusOption[] = [
  { value: 'created', label: '已创建' },
  { value: 'active', label: '启用' },
  { value: 'suspended', label: '停用' },
  { value: 'closed', label: '关闭' }
]

const activeCount = computed(() => tenants.value.filter(tenant => !tenant.deleted).length)
const deletedCount = computed(() => tenants.value.filter(tenant => tenant.deleted).length)
const billingIndexMode = computed(() => route.name === 'platform-billing-subscriptions')
const hasDirtyQuery = computed(() => keyword.value.trim() !== '' || filter.value !== 'all')
const safePage = computed<PlatformPage>(() => page.value ?? {
  limit: limit.value,
  offset: offset.value,
  total: tenants.value.length
})

onMounted(() => {
  void loadTenants()
})

async function loadTenants(nextOffset = offset.value): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await listTenants({
      keyword: keyword.value,
      status: filter.value,
      includeDeleted: true,
      limit: limit.value,
      offset: nextOffset
    })
    const nextPage = response.page || {
      limit: limit.value,
      offset: nextOffset,
      total: response.tenants.length
    }
    tenants.value = response.tenants
    page.value = nextPage
    offset.value = nextPage.offset
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function searchTenants(): void {
  offset.value = 0
  void loadTenants(0)
}

function resetFilters(): void {
  keyword.value = ''
  filter.value = 'all'
  offset.value = 0
  void loadTenants(0)
}

function openCreatePage(): void {
  void router.push({ name: 'platform-tenant-create' })
}

function openEditPage(tenant: PlatformTenant): void {
  void router.push({ name: 'platform-tenant-edit', params: { tenantId: tenant.id } })
}

function openBillingPage(tenant: PlatformTenant): void {
  void router.push({ name: 'platform-tenant-billing', params: { tenantId: tenant.id } })
}

async function deleteSelectedTenant(tenant: PlatformTenant): Promise<void> {
  if (saving.value || !window.confirm(`删除租户 ${tenant.tenantCode}？`)) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    await deleteTenant(tenant.id)
    await loadTenants()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function restoreSelectedTenant(tenant: PlatformTenant): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    await restoreTenant(tenant.id)
    await loadTenants()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有平台后台权限'
  }
  return '操作失败'
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>{{ billingIndexMode ? '计费' : '平台' }}</span>
          <h1>{{ billingIndexMode ? '租户计费' : '租户管理' }}</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        placeholder="代码 / 名称 / 电话 / 地址"
        :loading="loading"
        :has-dirty-query="hasDirtyQuery"
        @search="searchTenants"
        @reset="resetFilters"
        @refresh="loadTenants()"
      >
        <template #filters>
          <div class="segmented-control" aria-label="租户状态筛选">
            <button type="button" :class="{ active: filter === 'all' }" @click="filter = 'all'; searchTenants()">
              全部 {{ safePage.total }}
            </button>
            <button type="button" :class="{ active: filter === 'active' }" @click="filter = 'active'; searchTenants()">
              正常 {{ activeCount }}
            </button>
            <button type="button" :class="{ active: filter === 'deleted' }" @click="filter = 'deleted'; searchTenants()">
              已删除 {{ deletedCount }}
            </button>
          </div>
        </template>
        <template #actions>
          <button v-if="!billingIndexMode" class="primary-button" type="button" @click="openCreatePage">
            新增租户
          </button>
        </template>
      </ErpQueryToolbar>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>

      <PlatformTenantTable
        :tenants="tenants"
        :loading="loading"
        :saving="saving"
        :status-options="statusOptions"
        :billing-only="billingIndexMode"
        @edit="openEditPage"
        @billing="openBillingPage"
        @delete="deleteSelectedTenant"
        @restore="restoreSelectedTenant"
      />

      <ErpPagination
        :limit="safePage.limit"
        :offset="safePage.offset"
        :total="safePage.total"
        :loading="loading"
        @change="loadTenants"
      />
    </section>
  </main>
</template>

<style scoped>
.platform-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.platform-workspace {
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

.segmented-control {
  display: flex;
  padding: 3px;
  border: 1px solid #cfd9e4;
  border-radius: 8px;
  background: #ffffff;
}

.segmented-control button {
  min-height: 34px;
  border: 0;
  border-radius: 6px;
  padding: 0 10px;
  color: #475569;
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.segmented-control button.active {
  color: #ffffff;
  background: #334155;
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

@media (max-width: 980px) {
  .platform-shell {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 700px) {
  .platform-workspace {
    padding: 14px;
  }

  .segmented-control,
  .primary-button {
    width: 100%;
  }

  .segmented-control button {
    flex: 1;
  }
}
</style>
