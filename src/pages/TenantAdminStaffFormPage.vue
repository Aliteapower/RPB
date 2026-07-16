<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  clearTenantProfileLogo,
  createStaff,
  getCurrentTenantAdminStaff,
  getStaff,
  getTenantProfile,
  TenantAdminApiError,
  updateCurrentTenantAdminStaff,
  updateStaff,
  updateTenantProfile,
  uploadTenantProfileLogo,
  type TenantAdminProfile,
  type TenantAdminProfileMutation,
  type TenantAdminStaff,
  type TenantAdminStaffMutation
} from '../api/tenantAdminApi'
import CountryPhoneField from '../components/common/CountryPhoneField.vue'
import PasswordInput from '../components/common/PasswordInput.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type { AuthStoreAccess } from '../types/auth'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()
const { t } = useI18n()

interface TenantProfileForm {
  tenantCode: string
  displayName: string
  status: string
  defaultLocale: string
  contactPhone: string
  address: string
  principalName: string
  logoMediaUrl: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const logoSaving = ref(false)
const errorText = ref('')
const savedText = ref('')
const storeAccessLoading = ref(false)
const logoFile = ref<File | null>(null)
const logoFileInput = ref<HTMLInputElement | null>(null)
const localLogoPreviewUrl = ref('')

const mode = computed<'create' | 'edit' | 'self'>(() => {
  if (route.name === 'tenant-admin-staff-create') {
    return 'create'
  }
  return route.name === 'tenant-admin-staff-self-edit' ? 'self' : 'edit'
})
const pageTitle = computed(() => {
  if (mode.value === 'create') {
    return gt('generated.tenant-admin-staff-form.038')
  }
  return mode.value === 'self' ? gt('generated.tenant-admin-staff-form.039') : gt('generated.tenant-admin-staff-form.040')
})
const statusFieldVisible = computed(() => mode.value !== 'self')
const storeId = computed(() => String(route.params.storeId || ''))
const staffId = computed(() => String(route.params.staffId || ''))
const logoPreviewUrl = computed(() => localLogoPreviewUrl.value || tenantProfileForm.logoMediaUrl)
const assignableStores = computed<AuthStoreAccess[]>(() => {
  if (auth.authorizedStores.length > 0) {
    return auth.authorizedStores
  }
  return (auth.user?.storeIds ?? []).map(storeId => ({
    tenantId: auth.user?.tenantId || '',
    tenantCode: '',
    operatingEntityId: null,
    operatingEntityName: null,
    storeId,
    storeCode: '',
    storeName: storeFallbackLabel(storeId),
    status: 'active',
    locale: '',
    defaultStore: storeId === auth.user?.defaultStoreId
  }))
})
const storeChoices = computed<AuthStoreAccess[]>(() => {
  const byId = new Map(assignableStores.value.map(store => [store.storeId, store]))
  selectedStoreIds.value.forEach(storeId => {
    if (!byId.has(storeId)) {
      byId.set(storeId, {
        tenantId: auth.user?.tenantId || '',
        tenantCode: '',
        operatingEntityId: null,
        operatingEntityName: null,
        storeId,
        storeCode: storeId.slice(0, 8),
        storeName: storeFallbackLabel(storeId),
        status: 'active',
        locale: '',
        defaultStore: storeId === defaultStoreId.value
      })
    }
  })
  return Array.from(byId.values())
})
const selectedStoreOptions = computed(() =>
  storeChoices.value.filter(store => selectedStoreIds.value.includes(store.storeId))
)
const readonlyStoreAccessSummary = computed(() => {
  const labels = selectedStoreIds.value.map(storeDisplayNameById)
  return labels.length > 0 ? labels.join(' / ') : '-'
})
const readonlyDefaultStoreSummary = computed(() =>
  defaultStoreId.value ? storeDisplayNameById(defaultStoreId.value) : '-'
)

const tenantProfileForm = reactive<TenantProfileForm>({
  tenantCode: '',
  displayName: '',
  status: '',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  logoMediaUrl: ''
})

const form = reactive({
  employeeNo: '',
  name: '',
  phone: '',
  email: '',
  status: 'active' as TenantAdminStaffMutation['status'],
  password: ''
})
const selectedStoreIds = ref<string[]>([])
const defaultStoreId = ref('')

onMounted(() => {
  void loadAssignableStores()
  if (mode.value !== 'create') {
    void loadStaff()
  } else {
    initializeCreateStoreAccess()
  }
})

onUnmounted(() => {
  revokeLocalLogoPreview()
})

async function loadStaff(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    if (mode.value === 'self') {
      const [profileResponse, staffResponse] = await Promise.all([
        getTenantProfile(storeId.value),
        getCurrentTenantAdminStaff(storeId.value)
      ])
      applyTenantProfile(profileResponse.profile)
      applyAdminAccount(staffResponse.staff)
      return
    }

    const response = await getStaff(storeId.value, staffId.value)
    applyAdminAccount(response.staff)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function loadAssignableStores(): Promise<void> {
  storeAccessLoading.value = true
  try {
    await auth.ensureAuthorizedStores()
    if (mode.value === 'create') {
      initializeCreateStoreAccess()
    }
  } finally {
    storeAccessLoading.value = false
  }
}

async function submitStaff(): Promise<void> {
  if (saving.value) {
    return
  }
  if (mode.value === 'self' && !validateSelfForm()) {
    return
  }
  if (mode.value !== 'self' && !validateStoreAccessForm()) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    if (mode.value === 'create') {
      await createStaff(storeId.value, toPayload())
    } else if (mode.value === 'self') {
      const [profileResponse, staffResponse] = await Promise.all([
        updateTenantProfile(storeId.value, tenantProfilePayload()),
        updateCurrentTenantAdminStaff(storeId.value, adminAccountPayload())
      ])
      applyTenantProfile(profileResponse.profile)
      applyAdminAccount(staffResponse.staff)
      savedText.value = gt('generated.tenant-admin-staff-form.041')
      if (logoFile.value) {
        await uploadSelectedLogo(gt('generated.tenant-admin-staff-form.042'))
      }
    } else {
      await updateStaff(storeId.value, staffId.value, toPayload())
    }
    await router.push({ name: 'tenant-admin-staff', params: { storeId: storeId.value } })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function submitLogo(): Promise<void> {
  if (logoSaving.value || saving.value || !logoFile.value) {
    return
  }

  errorText.value = ''
  savedText.value = ''
  try {
    await uploadSelectedLogo(gt('generated.tenant-admin-staff-form.043'))
  } catch (error) {
    errorText.value = apiErrorText(error)
  }
}

async function uploadSelectedLogo(successMessage: string): Promise<void> {
  if (!logoFile.value) {
    return
  }

  logoSaving.value = true
  try {
    const response = await uploadTenantProfileLogo(storeId.value, logoFile.value)
    applyTenantProfile(response.profile)
    clearSelectedLogo()
    savedText.value = successMessage
  } finally {
    logoSaving.value = false
  }
}

async function removeLogo(): Promise<void> {
  if (logoSaving.value || saving.value) {
    return
  }

  logoSaving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await clearTenantProfileLogo(storeId.value)
    applyTenantProfile(response.profile)
    clearSelectedLogo()
    savedText.value = gt('generated.tenant-admin-staff-form.044')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    logoSaving.value = false
  }
}

function handleLogoFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0] || null
  logoFile.value = file
  revokeLocalLogoPreview()
  if (file) {
    localLogoPreviewUrl.value = URL.createObjectURL(file)
  }
}

function clearSelectedLogo(): void {
  logoFile.value = null
  revokeLocalLogoPreview()
  if (logoFileInput.value) {
    logoFileInput.value.value = ''
  }
}

function revokeLocalLogoPreview(): void {
  if (localLogoPreviewUrl.value) {
    URL.revokeObjectURL(localLogoPreviewUrl.value)
    localLogoPreviewUrl.value = ''
  }
}

function applyTenantProfile(profile: TenantAdminProfile): void {
  Object.assign(tenantProfileForm, {
    tenantCode: profile.storeCode || profile.tenantCode,
    displayName: profile.displayName,
    status: profile.status,
    defaultLocale: profile.defaultLocale || 'zh-CN',
    contactPhone: profile.contactPhone || '',
    address: profile.address || '',
    principalName: profile.principalName || '',
    logoMediaUrl: profile.logoMediaUrl || ''
  } satisfies TenantProfileForm)
}

function applyAdminAccount(staff: TenantAdminStaff): void {
  Object.assign(form, {
    employeeNo: staff.employeeNo,
    name: staff.name,
    phone: staff.phone || '',
    email: staff.email || '',
    status: staff.status,
    password: ''
  })
  selectedStoreIds.value = normalizeSelectedStoreIds(staff.storeIds.length > 0 ? staff.storeIds : [storeId.value])
  defaultStoreId.value = staff.defaultStoreId && selectedStoreIds.value.includes(staff.defaultStoreId)
    ? staff.defaultStoreId
    : selectedStoreIds.value[0] || ''
}

function tenantProfilePayload(): TenantAdminProfileMutation {
  return {
    displayName: tenantProfileForm.displayName.trim(),
    defaultLocale: optionalValue(String(tenantProfileForm.defaultLocale || '')),
    contactPhone: optionalValue(String(tenantProfileForm.contactPhone || '')),
    address: optionalValue(String(tenantProfileForm.address || '')),
    principalName: optionalValue(String(tenantProfileForm.principalName || ''))
  }
}

function adminAccountPayload(): TenantAdminStaffMutation {
  return {
    name: form.name.trim(),
    email: optionalValue(form.email),
    password: optionalValue(form.password)
  }
}

function validateSelfForm(): boolean {
  errorText.value = ''
  savedText.value = ''
  if (!tenantProfileForm.displayName.trim() || !tenantProfileForm.defaultLocale.trim()) {
    errorText.value = gt('generated.tenant-admin-staff-form.045')
    return false
  }
  if (form.password.trim() && !/^[A-Za-z0-9]{6}$/.test(form.password.trim())) {
    errorText.value = gt('generated.tenant-admin-staff-form.046')
    return false
  }
  return true
}

function validateStoreAccessForm(): boolean {
  errorText.value = ''
  savedText.value = ''
  if (selectedStoreIds.value.length === 0 || !defaultStoreId.value) {
    errorText.value = t('tenant.staffForm.errors.storeRequired')
    return false
  }
  if (!selectedStoreIds.value.includes(defaultStoreId.value)) {
    errorText.value = t('tenant.staffForm.errors.defaultStoreRequired')
    return false
  }
  return true
}

function toPayload(): TenantAdminStaffMutation {
  return {
    employeeNo: mode.value === 'create' ? form.employeeNo.trim() : undefined,
    name: form.name.trim(),
    phone: optionalValue(form.phone),
    email: optionalValue(form.email),
    status: form.status,
    password: mode.value === 'create' ? form.password.trim() : optionalValue(form.password),
    storeIds: selectedStoreIds.value,
    defaultStoreId: defaultStoreId.value || null
  }
}

function toggleStore(storeId: string, checked: boolean): void {
  const current = new Set(selectedStoreIds.value)
  if (checked) {
    current.add(storeId)
  } else {
    current.delete(storeId)
  }
  selectedStoreIds.value = normalizeSelectedStoreIds(Array.from(current))
  if (!defaultStoreId.value || !selectedStoreIds.value.includes(defaultStoreId.value)) {
    defaultStoreId.value = selectedStoreIds.value[0] || ''
  }
}

function toggleStoreFromEvent(storeId: string, event: Event): void {
  toggleStore(storeId, (event.target as HTMLInputElement).checked)
}

function initializeCreateStoreAccess(): void {
  if (selectedStoreIds.value.length > 0) {
    return
  }
  const candidate =
    currentStoreAllowed(storeId.value)
      ? storeId.value
      : auth.user?.defaultStoreId && currentStoreAllowed(auth.user.defaultStoreId)
        ? auth.user.defaultStoreId
        : assignableStores.value[0]?.storeId
  selectedStoreIds.value = candidate ? [candidate] : []
  defaultStoreId.value = selectedStoreIds.value[0] || ''
}

function normalizeSelectedStoreIds(storeIds: string[]): string[] {
  return Array.from(new Set(storeIds)).filter(candidate => candidate.trim())
}

function currentStoreAllowed(candidate: string): boolean {
  return assignableStores.value.some(store => store.storeId === candidate)
}

function storeDisplayName(store: AuthStoreAccess): string {
  const storeName = store.storeName || store.storeCode || storeFallbackLabel(store.storeId)
  return store.operatingEntityName ? `${storeName} / ${store.operatingEntityName}` : storeName
}

function storeDisplayNameById(storeId: string): string {
  const store = storeChoices.value.find(candidate => candidate.storeId === storeId)
  return store ? storeDisplayName(store) : storeFallbackLabel(storeId)
}

function storeFallbackLabel(storeId: string): string {
  return t('staffHome.store.label', { shortId: storeId.slice(0, 8) })
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    created: gt('generated.tenant-admin-staff-form.047'),
    active: gt('generated.tenant-admin-staff-form.048'),
    suspended: gt('generated.tenant-admin-staff-form.049'),
    closed: gt('generated.tenant-admin-staff-form.050')
  }
  return labels[status] || status || '-'
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-staff-form.051')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-staff-form.052')
  }
  if (error.response.error.code === 'STAFF_CODE_CONFLICT') {
    return gt('generated.tenant-admin-staff-form.053')
  }
  if (error.response.error.code === 'STAFF_NOT_FOUND') {
    return gt('generated.tenant-admin-staff-form.054')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return mode.value === 'self' ? gt('generated.tenant-admin-staff-form.055') : gt('generated.tenant-admin-staff-form.056')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-staff-form.057')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-staff-form.058')
  }
  if (error.response.error.code === 'TENANT_PROFILE_NOT_FOUND') {
    return gt('generated.tenant-admin-staff-form.059')
  }
  return gt('generated.tenant-admin-staff-form.060')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-staff-form.001') }}</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'tenant-admin-staff', params: { storeId } })"> {{ gt('generated.tenant-admin-staff-form.002') }} </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-staff-form.003') }}</p>

      <form v-else class="form-panel" :class="{ 'self-form': mode === 'self' }" @submit.prevent="submitStaff">
        <template v-if="mode === 'self'">
          <section class="section-panel">
            <div class="section-heading">
              <h2>{{ gt('generated.tenant-admin-staff-form.004') }}</h2>
            </div>

            <div class="field-grid">
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.005') }}</span>
                <input :value="tenantProfileForm.tenantCode" readonly />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.006') }}</span>
                <input v-model.trim="tenantProfileForm.displayName" required />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.007') }}</span>
                <input :value="statusLabel(tenantProfileForm.status)" readonly />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.008') }}</span>
                <input v-model.trim="tenantProfileForm.defaultLocale" required />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.009') }}</span>
                <input v-model.trim="tenantProfileForm.principalName" />
              </label>
              <CountryPhoneField
                v-model="tenantProfileForm.contactPhone"
                :label="gt('generated.tenant-admin-staff-form.010')"
                model-format="e164"
              />
              <label class="wide-field">
                <span>{{ gt('generated.tenant-admin-staff-form.011') }}</span>
                <input v-model.trim="tenantProfileForm.address" />
              </label>
            </div>
          </section>

          <section class="section-panel logo-panel">
            <div class="logo-preview" :aria-label="gt('generated.tenant-admin-staff-form.012')">
              <img v-if="logoPreviewUrl" :src="logoPreviewUrl" :alt="gt('generated.tenant-admin-staff-form.013')" />
              <span v-else>LOGO</span>
            </div>

            <div class="logo-control">
              <div class="section-heading compact-heading">
                <h2>{{ gt('generated.tenant-admin-staff-form.014') }}</h2>
              </div>
              <input
                ref="logoFileInput"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                @change="handleLogoFileChange"
              />
              <div class="button-row">
                <button type="button" class="secondary-button" :disabled="!logoFile || logoSaving || saving" @click="submitLogo">
                  {{ logoSaving && logoFile ? gt('generated.tenant-admin-staff-form.015') : gt('generated.tenant-admin-staff-form.016') }}
                </button>
                <button type="button" class="ghost-button" :disabled="logoSaving || saving" @click="removeLogo">{{ gt('generated.tenant-admin-staff-form.017') }}</button>
              </div>
            </div>
          </section>

          <section class="section-panel">
            <div class="section-heading">
              <h2>{{ gt('generated.tenant-admin-staff-form.018') }}</h2>
            </div>

            <div class="field-grid">
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.019') }}</span>
                <input v-model.trim="form.employeeNo" readonly required />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.020') }}</span>
                <input v-model.trim="form.name" required />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.021') }}</span>
                <input v-model.trim="form.email" type="email" />
              </label>
              <label>
                <span>{{ gt('generated.tenant-admin-staff-form.022') }}</span>
                <PasswordInput
                  v-model="form.password"
                  maxlength="6"
                  pattern="[A-Za-z0-9]{6}"
                  autocomplete="new-password"
                />
                <small>{{ gt('generated.tenant-admin-staff-form.023') }}</small>
              </label>
            </div>
          </section>

          <section class="section-panel store-access-readonly-panel">
            <div class="section-heading">
              <h2>{{ t('tenant.staffForm.storeAccess.title') }}</h2>
            </div>
            <p v-if="storeAccessLoading" class="loading-line">{{ t('common.actions.loading') }}</p>
            <div v-else class="readonly-store-access">
              <div>
                <span>{{ t('tenant.staffForm.storeAccess.title') }}</span>
                <strong>{{ readonlyStoreAccessSummary }}</strong>
              </div>
              <div>
                <span>{{ t('tenant.staffForm.storeAccess.defaultStore') }}</span>
                <strong>{{ readonlyDefaultStoreSummary }}</strong>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <label>
            <span>{{ gt('generated.tenant-admin-staff-form.024') }}</span>
            <input v-model.trim="form.employeeNo" :readonly="mode !== 'create'" required />
          </label>
          <label>
            <span>{{ gt('generated.tenant-admin-staff-form.025') }}</span>
            <input v-model.trim="form.name" required />
          </label>
          <CountryPhoneField
            v-model="form.phone"
            :label="gt('generated.tenant-admin-staff-form.026')"
            model-format="e164"
          />
          <label>
            <span>{{ gt('generated.tenant-admin-staff-form.027') }}</span>
            <input v-model.trim="form.email" type="email" />
          </label>
          <label v-if="statusFieldVisible">
            <span>{{ gt('generated.tenant-admin-staff-form.028') }}</span>
            <select v-model="form.status">
              <option value="active">{{ gt('generated.tenant-admin-staff-form.029') }}</option>
              <option value="disabled">{{ gt('generated.tenant-admin-staff-form.030') }}</option>
              <option value="locked">{{ gt('generated.tenant-admin-staff-form.031') }}</option>
            </select>
          </label>
          <label>
            <span>{{ mode === 'create' ? gt('generated.tenant-admin-staff-form.032') : gt('generated.tenant-admin-staff-form.033') }}</span>
            <PasswordInput
              v-model="form.password"
              maxlength="6"
              :required="mode === 'create'"
              autocomplete="new-password"
            />
            <small>{{ gt('generated.tenant-admin-staff-form.034') }}</small>
          </label>
          <div class="store-access-edit-panel wide-field">
            <div class="section-heading compact-heading">
              <h2>{{ t('tenant.staffForm.storeAccess.title') }}</h2>
            </div>
            <p v-if="storeAccessLoading" class="loading-line">{{ t('common.actions.loading') }}</p>
            <p v-else-if="storeChoices.length === 0" class="loading-line">{{ t('tenant.staffForm.storeAccess.empty') }}</p>
            <div v-else class="store-option-grid">
              <label v-for="store in storeChoices" :key="store.storeId" class="store-option">
                <input
                  type="checkbox"
                  :checked="selectedStoreIds.includes(store.storeId)"
                  @change="toggleStoreFromEvent(store.storeId, $event)"
                />
                <span>
                  <strong>{{ storeDisplayName(store) }}</strong>
                  <small>{{ store.storeCode || store.storeId.slice(0, 8) }}</small>
                </span>
              </label>
            </div>
            <label class="wide-field">
              <span>{{ t('tenant.staffForm.storeAccess.defaultStore') }}</span>
              <select v-model="defaultStoreId" :disabled="selectedStoreOptions.length === 0" required>
                <option v-for="store in selectedStoreOptions" :key="store.storeId" :value="store.storeId">
                  {{ storeDisplayName(store) }}
                </option>
              </select>
            </label>
          </div>
        </template>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving || logoSaving">
            {{ saving ? gt('generated.tenant-admin-staff-form.035') : mode === 'self' ? gt('generated.tenant-admin-staff-form.036') : gt('generated.tenant-admin-staff-form.037') }}
          </button>
        </div>
      </form>
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

.secondary-button,
.primary-button,
.ghost-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
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

.ghost-button {
  border: 1px solid #dbe3ea;
  color: #64748b;
  background: #f8fafc;
}

.primary-button:disabled,
.secondary-button:disabled,
.ghost-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-banner,
.success-banner,
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

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.form-panel {
  width: min(100%, 760px);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.form-panel.self-form {
  width: min(100%, 960px);
  grid-template-columns: 1fr;
  padding: 0;
  border: 0;
  background: transparent;
}

.section-panel {
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  display: grid;
  gap: 4px;
  margin-bottom: 16px;
}

.section-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.compact-heading {
  margin-bottom: 4px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

input,
select {
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

input[readonly] {
  color: #64748b;
  background: #f8fafc;
}

input[type='file'] {
  padding: 7px 10px;
}

small {
  color: #64748b;
  font-weight: 500;
}

.wide-field {
  grid-column: 1 / -1;
}

.store-access-edit-panel,
.store-access-readonly-panel {
  display: grid;
  gap: 12px;
}

.store-access-edit-panel {
  padding: 14px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #f8fafc;
}

.store-access-readonly-panel {
  align-content: start;
}

.readonly-store-access {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.readonly-store-access div {
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #f8fafc;
}

.readonly-store-access span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.readonly-store-access strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.store-option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.store-option {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  min-height: 44px;
  padding: 9px 10px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #f8fafc;
}

.store-option input {
  width: 18px;
  min-height: 18px;
  padding: 0;
}

.store-option span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.store-option strong,
.store-option small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logo-panel {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.logo-preview {
  width: 84px;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #ffffff;
}

.logo-control {
  display: grid;
  gap: 10px;
}

.button-row,
.form-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-actions {
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.self-form .form-actions {
  padding-top: 0;
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

  .page-heading,
  .form-panel,
  .field-grid,
  .store-option-grid,
  .readonly-store-access,
  .logo-panel {
    display: grid;
    grid-template-columns: 1fr;
  }

  .logo-preview {
    width: 76px;
  }

  .form-actions {
    justify-content: stretch;
  }

  .secondary-button,
  .primary-button,
  .ghost-button {
    width: 100%;
  }
}
</style>
