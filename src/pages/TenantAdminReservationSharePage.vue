<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import { TenantAdminApiError } from '../api/tenantAdminApi'
import {
  getTenantAdminShareProfile,
  previewTenantAdminShareProfile,
  resetTenantAdminShareProfileTemplate,
  updateTenantAdminShareProfile
} from '../api/tenantAdminShareProfileApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  TenantAdminShareProfile,
  TenantAdminShareProfileMutation
} from '../types/tenantAdminShareProfile'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()
const { locale } = useI18n({ useScope: 'global' })

const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const restoring = ref(false)
const errorText = ref('')
const savedText = ref('')
const previewText = ref('')
const availableVariables = ref<string[]>([])

const storeId = computed(() => String(route.params.storeId || ''))
const activeLocale = computed(() => String(locale.value || 'zh-CN'))

const form = reactive({
  storeDisplayName: '',
  shareDisplayName: '',
  googleMapUrl: '',
  shareEmail: '',
  whatsappBusinessPhoneE164: '',
  reservationShareNote: '',
  reservationShareTemplate: ''
})

onMounted(() => {
  void loadShareProfile()
})

watch(activeLocale, () => {
  void loadShareProfile()
})

async function loadShareProfile(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  previewText.value = ''

  try {
    const response = await getTenantAdminShareProfile(storeId.value, activeLocale.value)
    applyShareProfile(response.shareProfile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitShareProfile(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''

  try {
    const response = await updateTenantAdminShareProfile(storeId.value, toShareMutation(), activeLocale.value)
    applyShareProfile(response.shareProfile)
    savedText.value = gt('generated.tenant-admin-reservation-share.024')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function previewShareProfile(): Promise<void> {
  if (previewing.value) {
    return
  }

  previewing.value = true
  errorText.value = ''
  previewText.value = ''

  try {
    const response = await previewTenantAdminShareProfile(storeId.value, toShareMutation(), activeLocale.value)
    previewText.value = response.preview.shareText
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    previewing.value = false
  }
}

async function restoreDefaultTemplate(): Promise<void> {
  if (restoring.value) {
    return
  }

  restoring.value = true
  errorText.value = ''
  savedText.value = ''

  try {
    const response = await resetTenantAdminShareProfileTemplate(storeId.value, activeLocale.value)
    applyShareProfile(response.shareProfile)
    savedText.value = gt('generated.tenant-admin-reservation-share.025')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    restoring.value = false
  }
}

function applyShareProfile(profile: TenantAdminShareProfile): void {
  form.storeDisplayName = profile.storeDisplayName
  form.shareDisplayName = profile.shareDisplayName
  form.googleMapUrl = profile.googleMapUrl
  form.shareEmail = profile.shareEmail
  form.whatsappBusinessPhoneE164 = profile.whatsappBusinessPhoneE164
  form.reservationShareNote = profile.reservationShareNote
  form.reservationShareTemplate = profile.reservationShareTemplate
  availableVariables.value = profile.availableVariables
}

function toShareMutation(): TenantAdminShareProfileMutation {
  return {
    shareDisplayName: nullableText(form.shareDisplayName),
    googleMapUrl: nullableText(form.googleMapUrl),
    shareEmail: nullableText(form.shareEmail),
    whatsappBusinessPhoneE164: nullableText(form.whatsappBusinessPhoneE164),
    reservationShareNote: nullableText(form.reservationShareNote),
    reservationShareTemplate: nullableText(form.reservationShareTemplate)
  }
}

function nullableText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function insertVariable(variable: string): void {
  const token = `{{${variable}}}`
  form.reservationShareTemplate = form.reservationShareTemplate
    ? `${form.reservationShareTemplate}\n${token}`
    : token
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-reservation-share.026')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-reservation-share.027')
  }
  if (error.response.error.code === 'TEMPLATE_UNKNOWN_VARIABLE') {
    return gt('generated.tenant-admin-reservation-share.028')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-reservation-share.029')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-reservation-share.030')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-reservation-share.031')
  }
  return gt('generated.tenant-admin-reservation-share.032')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-reservation-share.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-reservation-share.002') }}</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-reservation-share.003') }}</p>

      <form v-else class="form-panel" @submit.prevent="submitShareProfile">
        <section class="section-panel" :aria-label="gt('generated.tenant-admin-reservation-share.004')">
          <div class="section-heading">
            <h2>{{ gt('generated.tenant-admin-reservation-share.005') }}</h2>
          </div>

          <div class="field-grid">
            <label>
              <span>{{ gt('generated.tenant-admin-reservation-share.006') }}</span>
              <input v-model.trim="form.storeDisplayName" readonly />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-reservation-share.007') }}</span>
              <input v-model.trim="form.shareDisplayName" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-reservation-share.008') }}</span>
              <input v-model.trim="form.googleMapUrl" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-reservation-share.009') }}</span>
              <input v-model.trim="form.shareEmail" type="email" placeholder="booking@example.com" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-reservation-share.010') }}</span>
              <input v-model.trim="form.whatsappBusinessPhoneE164" placeholder="+6588880000" />
            </label>
            <label class="wide-field">
              <span>{{ gt('generated.tenant-admin-reservation-share.011') }}</span>
              <input v-model.trim="form.reservationShareNote" />
            </label>
          </div>
        </section>

        <section class="section-panel" :aria-label="gt('generated.tenant-admin-reservation-share.012')">
          <div class="section-heading">
            <h2>{{ gt('generated.tenant-admin-reservation-share.013') }}</h2>
          </div>

          <section class="template-tools" :aria-label="gt('generated.tenant-admin-reservation-share.014')">
            <p class="template-tools__hint"> {{ gt('generated.tenant-admin-reservation-share.015') }} </p>
            <div class="template-tools__buttons">
              <button
                v-for="variable in availableVariables"
                :key="variable"
                type="button"
                @click="insertVariable(variable)"
              >
                {{ variable }}
              </button>
            </div>
          </section>

          <label>
            <span>{{ gt('generated.tenant-admin-reservation-share.016') }}</span>
            <textarea v-model="form.reservationShareTemplate" rows="16"></textarea>
          </label>
        </section>

        <section v-if="previewText" class="preview-panel form-panel__wide" :aria-label="gt('generated.tenant-admin-reservation-share.017')">
          <pre>{{ previewText }}</pre>
        </section>

        <div class="form-actions">
          <button type="button" :disabled="previewing" @click="previewShareProfile">
            {{ previewing ? gt('generated.tenant-admin-reservation-share.018') : gt('generated.tenant-admin-reservation-share.019') }}
          </button>
          <button type="button" :disabled="restoring" @click="restoreDefaultTemplate">
            {{ restoring ? gt('generated.tenant-admin-reservation-share.020') : gt('generated.tenant-admin-reservation-share.021') }}
          </button>
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? gt('generated.tenant-admin-reservation-share.022') : gt('generated.tenant-admin-reservation-share.023') }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  background: #f3f6f8;
  color: #102033;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: 100dvh;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  color: #0f172a;
  font-size: 24px;
  margin: 0;
}

.error-banner,
.success-banner,
.loading-line {
  border-radius: 6px;
  margin: 0 0 12px;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.success-banner {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.loading-line {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  color: #475569;
}

.form-panel {
  display: grid;
  gap: 16px;
  width: min(100%, 980px);
}

.form-panel__wide {
  grid-column: 1 / -1;
}

.section-panel {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  padding: 18px;
}

.section-heading h2 {
  color: #0f172a;
  font-size: 18px;
  margin: 0;
}

.field-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  gap: 7px;
}

input,
textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 40px;
  padding: 9px 10px;
  width: 100%;
}

textarea {
  line-height: 1.5;
  resize: vertical;
}

input:disabled,
input[readonly] {
  background: #f8fafc;
  color: #64748b;
}

.wide-field {
  grid-column: 1 / -1;
}

.template-tools {
  display: grid;
  gap: 10px;
}

.template-tools__hint {
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
  margin: 0;
}

.template-tools__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-tools__buttons button,
.form-actions button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  font: inherit;
  font-weight: 800;
  min-height: 36px;
  padding: 0 12px;
}

.preview-panel {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 12px;
}

.preview-panel pre {
  color: #0f172a;
  font: inherit;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.form-actions .primary-button {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
}

.form-actions button:disabled {
  opacity: 0.6;
}

@media (max-width: 980px) {
  .tenant-shell,
  .field-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .form-actions {
    display: grid;
  }
}
</style>
