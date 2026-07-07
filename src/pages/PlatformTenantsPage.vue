<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  PlatformApiError,
  deleteTenant,
  listTenants,
  restoreTenant,
  type PlatformPage,
  type PlatformTenant,
  type PlatformTenantOnboardingMode,
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
const { t } = useI18n()
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
  { value: 'created', labelKey: 'platform.tenants.status.created' },
  { value: 'active', labelKey: 'platform.tenants.status.active' },
  { value: 'suspended', labelKey: 'platform.tenants.status.suspended' },
  { value: 'closed', labelKey: 'platform.tenants.status.closed' }
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

function openCreatePage(onboardingMode: PlatformTenantOnboardingMode): void {
  void router.push({ name: 'platform-tenant-create', query: { onboardingMode } })
}

function openEditPage(tenant: PlatformTenant): void {
  void router.push({ name: 'platform-tenant-edit', params: { tenantId: tenant.id } })
}

function openStructurePage(tenant: PlatformTenant): void {
  void router.push({
    name: 'platform-tenant-edit',
    params: { tenantId: tenant.id },
    hash: '#tenant-structure'
  })
}

function openBillingPage(tenant: PlatformTenant): void {
  void router.push({ name: 'platform-tenant-billing', params: { tenantId: tenant.id } })
}

async function deleteSelectedTenant(tenant: PlatformTenant): Promise<void> {
  if (saving.value || !window.confirm(t('platform.tenants.list.confirmDelete', { tenantCode: tenant.tenantCode }))) {
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
    return t('platform.tenants.errors.operationFailed')
  }
  if (error.status === 401) {
    auth.clear()
    return t('platform.tenants.errors.sessionExpired')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return t('platform.tenants.errors.forbidden')
  }
  return t('platform.tenants.errors.operationFailed')
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>{{ billingIndexMode ? $t('platform.tenants.list.kickerBilling') : $t('platform.tenants.list.kickerPlatform') }}</span>
          <h1>{{ billingIndexMode ? $t('platform.tenants.list.billingTitle') : $t('platform.tenants.list.title') }}</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        :placeholder="$t('platform.tenants.list.keywordPlaceholder')"
        :loading="loading"
        :has-dirty-query="hasDirtyQuery"
        @search="searchTenants"
        @reset="resetFilters"
        @refresh="loadTenants()"
      >
        <template #filters>
          <div class="segmented-control" :aria-label="$t('platform.tenants.list.statusFilterAria')">
            <button type="button" :class="{ active: filter === 'all' }" @click="filter = 'all'; searchTenants()">
              {{ $t('platform.tenants.list.allFilter', { count: safePage.total }) }}
            </button>
            <button type="button" :class="{ active: filter === 'active' }" @click="filter = 'active'; searchTenants()">
              {{ $t('platform.tenants.list.activeFilter', { count: activeCount }) }}
            </button>
            <button type="button" :class="{ active: filter === 'deleted' }" @click="filter = 'deleted'; searchTenants()">
              {{ $t('platform.tenants.list.deletedFilter', { count: deletedCount }) }}
            </button>
          </div>
        </template>
        <template #actions>
          <div v-if="!billingIndexMode" class="create-actions">
            <button class="primary-button" type="button" @click="openCreatePage('group_multi_store')">
              {{ $t('platform.tenants.list.createGroup') }}
            </button>
            <button class="secondary-button" type="button" @click="openCreatePage('single_store')">
              {{ $t('platform.tenants.list.createSingle') }}
            </button>
          </div>
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
        @structure="openStructurePage"
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

.create-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.primary-button,
.secondary-button {
  min-height: 36px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  background: #ffffff;
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
  .create-actions {
    width: 100%;
  }

  .segmented-control button {
    flex: 1;
  }

  .create-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}
</style>
