<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  PlatformApiError,
  clearTenantLogo,
  createOperatingEntity,
  createTenant,
  createTenantStore,
  getTenant,
  getTenantAdminStoreAccess,
  listOperatingEntities,
  listTenantStores,
  uploadTenantLogo,
  updateOperatingEntity,
  updateTenant,
  updateTenantStore,
  type PlatformOperatingEntity,
  type PlatformOperatingEntityMutation,
  type PlatformTenantMutation,
  type PlatformTenantOnboardingMode,
  type PlatformTenantStoreAccessStore,
  type PlatformStore,
  type PlatformStoreMutation
} from '../api/platformApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import PlatformTenantForm from '../components/platform/PlatformTenantForm.vue'
import PlatformTenantStructurePanel from '../components/platform/PlatformTenantStructurePanel.vue'
import type {
  PlatformTenantAdminStoreAccessFormModel,
  PlatformOperatingEntityFormModel,
  PlatformStoreFormModel,
  PlatformTenantFormModel,
  TenantStatusOption
} from '../components/platform/platformTenantUi'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const auth = useAuthSessionStore()

const loading = ref(false)
const saving = ref(false)
const structureSaving = ref(false)
const errorText = ref('')
const adminStoreOptions = ref<PlatformTenantStoreAccessStore[]>([])
const operatingEntities = ref<PlatformOperatingEntity[]>([])
const tenantStores = ref<PlatformStore[]>([])

const mode = computed<'create' | 'edit'>(() => (route.name === 'platform-tenant-create' ? 'create' : 'edit'))
const tenantId = computed(() => String(route.params.tenantId || ''))

const statusOptions: TenantStatusOption[] = [
  { value: 'created', labelKey: 'platform.tenants.status.created' },
  { value: 'active', labelKey: 'platform.tenants.status.active' },
  { value: 'suspended', labelKey: 'platform.tenants.status.suspended' },
  { value: 'closed', labelKey: 'platform.tenants.status.closed' }
]

const form = reactive<PlatformTenantFormModel>({
  id: '',
  tenantCode: '',
  displayName: '',
  status: 'active',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  logoMediaUrl: '',
  logoFile: null,
  onboardingMode: normalizeCreateOnboardingMode(route.query.onboardingMode),
  initialPassword: '',
  password: '',
  adminStoreIds: [],
  defaultAdminStoreId: ''
})

const createPageTitle = computed(() => (
  form.onboardingMode === 'group_multi_store'
    ? t('platform.tenants.formPage.createGroupTitle')
    : t('platform.tenants.formPage.createSingleTitle')
))
const pageTitle = computed(() => (
  mode.value === 'create'
    ? createPageTitle.value
    : t('platform.tenants.formPage.editTitle')
))

onMounted(() => {
  if (mode.value === 'edit') {
    void loadTenant()
  }
})

async function loadTenant(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [response, storeAccess] = await Promise.all([
      getTenant(tenantId.value),
      getTenantAdminStoreAccess(tenantId.value)
    ])
    adminStoreOptions.value = storeAccess.stores
    await loadTenantStructure()
    Object.assign(form, {
      id: response.tenant.id,
      tenantCode: response.tenant.tenantCode,
      displayName: response.tenant.displayName,
      status: response.tenant.status,
      defaultLocale: response.tenant.defaultLocale || 'zh-CN',
      contactPhone: response.tenant.contactPhone || '',
      address: response.tenant.address || '',
      principalName: response.tenant.principalName || '',
      logoMediaUrl: response.tenant.logoMediaUrl || '',
      logoFile: null,
      onboardingMode: 'single_store',
      initialPassword: '',
      password: '',
      adminStoreIds: storeAccess.storeIds,
      defaultAdminStoreId: storeAccess.defaultStoreId || ''
    } satisfies PlatformTenantFormModel)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
    void scrollToStructurePanelIfRequested()
  }
}

async function scrollToStructurePanelIfRequested(): Promise<void> {
  if (mode.value !== 'edit' || route.hash !== '#tenant-structure') {
    return
  }
  await nextTick()
  document.getElementById('tenant-structure')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadTenantStructure(): Promise<void> {
  const [entitiesResponse, storesResponse] = await Promise.all([
    listOperatingEntities(tenantId.value),
    listTenantStores(tenantId.value)
  ])
  operatingEntities.value = entitiesResponse.operatingEntities
  tenantStores.value = storesResponse.stores
}

async function reloadStructureAndAccess(): Promise<void> {
  const [storeAccess] = await Promise.all([
    getTenantAdminStoreAccess(tenantId.value),
    loadTenantStructure()
  ])
  adminStoreOptions.value = storeAccess.stores
  form.adminStoreIds = storeAccess.storeIds
  form.defaultAdminStoreId = storeAccess.defaultStoreId || ''
}

async function submitTenantForm(submittedForm: PlatformTenantFormModel): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const payload = toPayload(submittedForm)
    if (mode.value === 'create') {
      const response = await createTenant(payload)
      if (submittedForm.onboardingMode === 'group_multi_store') {
        await router.push({ name: 'platform-tenant-edit', params: { tenantId: response.tenant.id }, hash: '#tenant-structure' })
        return
      }
    } else {
      await updateTenant(submittedForm.id, payload)
    }
    await router.push({ name: 'platform-tenants' })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function submitTenantLogo(file: File): Promise<void> {
  if (saving.value || mode.value !== 'edit') {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const response = await uploadTenantLogo(tenantId.value, file)
    form.logoMediaUrl = response.tenant.logoMediaUrl || ''
    form.logoFile = null
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function removeTenantLogo(): Promise<void> {
  if (saving.value || mode.value !== 'edit') {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const response = await clearTenantLogo(tenantId.value)
    form.logoMediaUrl = response.tenant.logoMediaUrl || ''
    form.logoFile = null
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function toPayload(submittedForm: PlatformTenantFormModel): PlatformTenantMutation {
  return {
    tenantCode: mode.value === 'create' ? submittedForm.tenantCode.trim() : undefined,
    displayName: submittedForm.displayName.trim(),
    status: submittedForm.status,
    defaultLocale: optionalValue(submittedForm.defaultLocale),
    contactPhone: optionalValue(submittedForm.contactPhone),
    address: optionalValue(submittedForm.address),
    principalName: optionalValue(submittedForm.principalName),
    initialPassword: mode.value === 'create' ? submittedForm.initialPassword.trim() : null,
    password: mode.value === 'edit' ? optionalValue(submittedForm.password) : null,
    onboardingMode: submittedForm.onboardingMode
  }
}

async function saveOperatingEntity(submittedForm: PlatformOperatingEntityFormModel): Promise<void> {
  if (structureSaving.value || mode.value !== 'edit') {
    return
  }

  structureSaving.value = true
  errorText.value = ''
  try {
    const payload = operatingEntityPayload(submittedForm)
    if (submittedForm.id) {
      await updateOperatingEntity(tenantId.value, submittedForm.id, payload)
    } else {
      await createOperatingEntity(tenantId.value, payload)
    }
    await reloadStructureAndAccess()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    structureSaving.value = false
  }
}

async function saveStore(submittedForm: PlatformStoreFormModel): Promise<void> {
  if (structureSaving.value || mode.value !== 'edit') {
    return
  }

  structureSaving.value = true
  errorText.value = ''
  try {
    const payload = storePayload(submittedForm)
    if (submittedForm.id) {
      await updateTenantStore(tenantId.value, submittedForm.id, payload)
    } else {
      await createTenantStore(tenantId.value, payload)
    }
    await reloadStructureAndAccess()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    structureSaving.value = false
  }
}

async function saveAdminStoreAccess(submittedForm: PlatformTenantAdminStoreAccessFormModel): Promise<void> {
  if (structureSaving.value || mode.value !== 'edit') {
    return
  }

  structureSaving.value = true
  errorText.value = ''
  try {
    await updateTenant(tenantId.value, {
      adminStoreIds: [...submittedForm.adminStoreIds],
      defaultAdminStoreId: optionalValue(submittedForm.defaultAdminStoreId)
    })
    await reloadStructureAndAccess()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    structureSaving.value = false
  }
}

function operatingEntityPayload(submittedForm: PlatformOperatingEntityFormModel): PlatformOperatingEntityMutation {
  return {
    entityCode: submittedForm.entityCode.trim(),
    displayName: submittedForm.displayName.trim(),
    status: submittedForm.status,
    defaultLocale: optionalValue(submittedForm.defaultLocale),
    contactPhone: optionalValue(submittedForm.contactPhone),
    address: optionalValue(submittedForm.address),
    principalName: optionalValue(submittedForm.principalName)
  }
}

function storePayload(submittedForm: PlatformStoreFormModel): PlatformStoreMutation {
  return {
    operatingEntityId: submittedForm.operatingEntityId,
    storeCode: submittedForm.storeCode.trim(),
    storeName: submittedForm.storeName.trim(),
    status: submittedForm.status,
    timezone: optionalValue(submittedForm.timezone),
    locale: optionalValue(submittedForm.locale),
    dateFormat: optionalValue(submittedForm.dateFormat),
    timeFormat: optionalValue(submittedForm.timeFormat),
    currency: optionalValue(submittedForm.currency),
    adminUsername: optionalValue(submittedForm.adminUsername),
    adminPassword: optionalValue(submittedForm.adminPassword)
  }
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function normalizeCreateOnboardingMode(value: unknown): PlatformTenantOnboardingMode {
  return value === 'group_multi_store' ? 'group_multi_store' : 'single_store'
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
  if (error.response.error.code === 'TENANT_CODE_CONFLICT') {
    return t('platform.tenants.errors.conflict')
  }
  if (error.response.error.code === 'TENANT_NOT_FOUND') {
    return t('platform.tenants.errors.notFound')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return t('platform.tenants.errors.invalid')
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
          <span>{{ $t('platform.tenants.formPage.kicker') }}</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'platform-tenants' })">
          {{ $t('platform.tenants.formPage.backToList') }}
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="loading" class="loading-line">{{ $t('common.actions.loading') }}</p>

      <PlatformTenantForm
        v-else
        :mode="mode"
        :form="form"
        :status-options="statusOptions"
        :saving="saving"
        @submit="submitTenantForm"
        @upload-logo="submitTenantLogo"
        @clear-logo="removeTenantLogo"
      />

      <PlatformTenantStructurePanel
        v-if="!loading && mode === 'edit'"
        :operating-entities="operatingEntities"
        :stores="tenantStores"
        :admin-store-options="adminStoreOptions"
        :admin-store-ids="form.adminStoreIds"
        :default-admin-store-id="form.defaultAdminStoreId"
        :saving="structureSaving"
        @save-operating-entity="saveOperatingEntity"
        @save-store="saveStore"
        @save-admin-store-access="saveAdminStoreAccess"
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

.secondary-button {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 14px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.error-banner,
.loading-line {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

@media (max-width: 980px) {
  .platform-shell {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 620px) {
  .platform-workspace {
    padding: 14px;
  }

  .page-heading {
    grid-template-columns: 1fr;
  }

  .page-heading {
    display: grid;
  }
}
</style>
