<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import type { PlatformTenantStoreAccessStore } from '../../api/platformApi'
import CountryPhoneField from '../common/CountryPhoneField.vue'
import PasswordInput from '../common/PasswordInput.vue'
import type { PlatformTenantFormModel, TenantStatusOption } from './platformTenantUi'

const props = defineProps<{
  mode: 'create' | 'edit'
  form: PlatformTenantFormModel
  adminStoreOptions: PlatformTenantStoreAccessStore[]
  statusOptions: TenantStatusOption[]
  saving: boolean
}>()

const emit = defineEmits<{
  submit: [form: PlatformTenantFormModel]
  uploadLogo: [file: File]
  clearLogo: []
}>()

const { t } = useI18n()
const localForm = reactive<PlatformTenantFormModel>({
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
  onboardingMode: 'single_store',
  initialPassword: '',
  password: '',
  adminStoreIds: [],
  defaultAdminStoreId: ''
})

const passwordLabel = computed(() => (
  props.mode === 'create'
    ? t('platform.tenants.form.initialPassword')
    : t('platform.tenants.form.password')
))
const passwordPlaceholder = computed(() => (
  props.mode === 'create'
    ? t('platform.tenants.form.initialPasswordPlaceholder')
    : t('platform.tenants.form.passwordPlaceholder')
))
const selectedAdminStoreOptions = computed(() => {
  const selected = new Set(localForm.adminStoreIds)
  return props.adminStoreOptions.filter(store => selected.has(store.storeId))
})

watch(
  () => props.form,
  value => {
    Object.assign(localForm, value)
    ensureDefaultAdminStore()
  },
  { deep: true, immediate: true }
)

watch(
  () => props.adminStoreOptions,
  () => ensureDefaultAdminStore(),
  { deep: true }
)

function submitForm(): void {
  emit('submit', { ...localForm })
}

function toggleAdminStoreFromEvent(storeId: string, event: Event): void {
  const checked = (event.target as HTMLInputElement | null)?.checked === true
  const selected = new Set(localForm.adminStoreIds)
  if (checked) {
    selected.add(storeId)
  } else {
    selected.delete(storeId)
  }
  localForm.adminStoreIds = Array.from(selected)
  ensureDefaultAdminStore()
}

function ensureDefaultAdminStore(): void {
  if (props.mode !== 'edit') {
    return
  }
  if (localForm.defaultAdminStoreId && localForm.adminStoreIds.includes(localForm.defaultAdminStoreId)) {
    return
  }
  localForm.defaultAdminStoreId = localForm.adminStoreIds[0] || ''
}

function adminStoreDisplayName(store: PlatformTenantStoreAccessStore): string {
  const name = store.storeName || store.storeCode || store.storeId.slice(0, 8)
  const storeName = store.storeCode ? `${name} (${store.storeCode})` : name
  return store.operatingEntityName ? `${storeName} / ${store.operatingEntityName}` : storeName
}

function handleLogoFileChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  localForm.logoFile = input?.files?.[0] ?? null
}

function submitLogo(): void {
  if (localForm.logoFile) {
    emit('uploadLogo', localForm.logoFile)
  }
}

function clearLogo(): void {
  localForm.logoFile = null
  emit('clearLogo')
}
</script>

<template>
  <form class="tenant-form" @submit.prevent="submitForm">
    <section class="form-section" :aria-label="$t('platform.tenants.form.basicInfo')">
      <fieldset v-if="mode === 'create'" class="onboarding-mode span-2">
        <legend>{{ $t('platform.tenants.form.onboardingMode.title') }}</legend>
        <label class="mode-option">
          <input v-model="localForm.onboardingMode" type="radio" value="single_store" />
          <span>
            <strong>{{ $t('platform.tenants.form.onboardingMode.singleStore') }}</strong>
            <small>{{ $t('platform.tenants.form.onboardingMode.singleStoreHint') }}</small>
          </span>
        </label>
        <label class="mode-option">
          <input v-model="localForm.onboardingMode" type="radio" value="group_multi_store" />
          <span>
            <strong>{{ $t('platform.tenants.form.onboardingMode.groupMultiStore') }}</strong>
            <small>{{ $t('platform.tenants.form.onboardingMode.groupMultiStoreHint') }}</small>
          </span>
        </label>
      </fieldset>

      <label>
        <span>{{ $t('platform.tenants.form.tenantCode') }}</span>
        <input
          v-model.trim="localForm.tenantCode"
          required
          maxlength="64"
          autocomplete="off"
          :readonly="mode === 'edit'"
          :class="{ readonly: mode === 'edit' }"
        />
      </label>

      <label>
        <span>{{ $t('platform.tenants.form.name') }}</span>
        <input v-model.trim="localForm.displayName" required maxlength="120" autocomplete="off" />
      </label>

      <label>
        <span>{{ $t('platform.tenants.form.status') }}</span>
        <select v-model="localForm.status">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ $t(option.labelKey) }}
          </option>
        </select>
      </label>

      <label>
        <span>{{ $t('platform.tenants.form.defaultLocale') }}</span>
        <input v-model.trim="localForm.defaultLocale" maxlength="20" autocomplete="off" />
      </label>
    </section>

    <section class="form-section" :aria-label="$t('platform.tenants.form.contactInfo')">
      <label>
        <span>{{ $t('platform.tenants.form.principal') }}</span>
        <input v-model.trim="localForm.principalName" maxlength="80" autocomplete="off" />
      </label>

      <CountryPhoneField
        v-model="localForm.contactPhone"
        :label="$t('platform.tenants.form.phone')"
        model-format="e164"
      />

      <label class="span-2">
        <span>{{ $t('platform.tenants.form.address') }}</span>
        <input v-model.trim="localForm.address" maxlength="240" autocomplete="off" />
      </label>
    </section>

    <section class="form-section" :aria-label="$t('platform.tenants.form.adminAccount')">
      <label>
        <span>{{ passwordLabel }}</span>
        <PasswordInput
          v-if="mode === 'create'"
          v-model.trim="localForm.initialPassword"
          required
          maxlength="6"
          :placeholder="passwordPlaceholder"
          autocomplete="new-password"
        />
        <PasswordInput
          v-else
          v-model.trim="localForm.password"
          maxlength="6"
          :placeholder="passwordPlaceholder"
          autocomplete="new-password"
        />
      </label>

      <div v-if="mode === 'edit'" class="admin-store-access-panel span-2">
        <div class="section-heading">
          <h2>{{ $t('platform.tenants.form.adminStoreAccess.title') }}</h2>
        </div>
        <p v-if="adminStoreOptions.length === 0" class="helper-line">
          {{ $t('platform.tenants.form.adminStoreAccess.empty') }}
        </p>
        <div v-else class="admin-store-grid">
          <label v-for="store in adminStoreOptions" :key="store.storeId" class="admin-store-option">
            <input
              type="checkbox"
              :checked="localForm.adminStoreIds.includes(store.storeId)"
              @change="toggleAdminStoreFromEvent(store.storeId, $event)"
            />
            <span>
              <strong>{{ adminStoreDisplayName(store) }}</strong>
              <small>{{ store.locale || '-' }}</small>
            </span>
          </label>
        </div>
        <label>
          <span>{{ $t('platform.tenants.form.adminStoreAccess.defaultStore') }}</span>
          <select
            v-model="localForm.defaultAdminStoreId"
            :disabled="selectedAdminStoreOptions.length === 0"
            required
          >
            <option v-for="store in selectedAdminStoreOptions" :key="store.storeId" :value="store.storeId">
              {{ adminStoreDisplayName(store) }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section v-if="mode === 'edit'" class="form-section form-section--logo" :aria-label="$t('platform.tenants.form.tenantLogo')">
      <div class="logo-preview" :class="{ empty: !localForm.logoMediaUrl }">
        <img v-if="localForm.logoMediaUrl" :src="localForm.logoMediaUrl" :alt="$t('platform.tenants.form.tenantLogo')" />
        <span v-else>LOGO</span>
      </div>

      <div class="logo-fields">
        <label>
          <span>{{ $t('platform.tenants.form.tenantLogo') }}</span>
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            :aria-label="$t('platform.tenants.form.chooseImage')"
            @change="handleLogoFileChange"
          />
        </label>
        <div class="logo-actions">
          <button class="secondary-button" type="button" :disabled="!localForm.logoFile || saving" @click="submitLogo">
            {{ $t('platform.tenants.form.uploadLogo') }}
          </button>
          <button class="secondary-button" type="button" :disabled="!localForm.logoMediaUrl || saving" @click="clearLogo">
            {{ $t('platform.tenants.form.clearLogo') }}
          </button>
        </div>
      </div>
    </section>

    <div class="form-actions">
      <button class="primary-button" type="submit" :disabled="saving">
        {{ saving ? $t('common.actions.saving') : $t('common.actions.save') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.tenant-form {
  display: grid;
  gap: 18px;
}

.form-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.span-2 {
  grid-column: span 2;
}

.form-section--logo {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.admin-store-access-panel {
  display: grid;
  gap: 12px;
  padding-top: 4px;
}

.section-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.helper-line {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.admin-store-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.admin-store-option {
  min-height: 58px;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #f8fafc;
}

.onboarding-mode {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  border: 0;
  padding: 0;
}

.onboarding-mode legend {
  grid-column: 1 / -1;
  margin-bottom: 2px;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.mode-option {
  min-height: 72px;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #f8fafc;
}

.mode-option input {
  width: 18px;
  height: 18px;
  min-height: 0;
  padding: 0;
}

.mode-option span {
  display: grid;
  gap: 3px;
}

.mode-option small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.admin-store-option input {
  width: 18px;
  height: 18px;
  min-height: 0;
  padding: 0;
}

.admin-store-option span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.admin-store-option strong {
  overflow-wrap: anywhere;
  color: #0f172a;
}

.admin-store-option small {
  color: #64748b;
  font-size: 12px;
}

.logo-preview {
  width: 78px;
  height: 78px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-preview span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.logo-fields {
  display: grid;
  gap: 10px;
}

.logo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

input,
select {
  min-height: 40px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

input.readonly {
  color: #475569;
  background: #f8fafc;
  cursor: default;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  padding: 0 18px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button {
  min-height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 12px;
  color: #1e3a5f;
  background: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 720px) {
  .form-section {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .form-section--logo {
    grid-template-columns: 1fr;
  }

  .admin-store-grid {
    grid-template-columns: 1fr;
  }

  .onboarding-mode {
    grid-template-columns: 1fr;
  }
}
</style>
