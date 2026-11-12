<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  generatePaymentProfileTestQr,
  getPaymentProfile,
  PaymentApiError,
  updatePaymentProfile
} from '../api/paymentApi'
import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import type { PaymentProfileMutation, PaymentProfileTestQr } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'
import {
  mergeQuickPayConfigIntoProfileConfigJson,
  parsePresetAmountText,
  readQuickPayConfigFromProfileConfigJson
} from '../utils/paymentPresentBridge'

const route = useRoute()
const auth = useAuthSessionStore()
const { gt } = useGeneratedText()

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const profileExists = ref(false)
const testingQr = ref(false)
const testQr = ref<PaymentProfileTestQr | null>(null)

const storeId = computed(() => String(route.params.storeId || ''))

const form = reactive<PaymentProfileMutation>({
  method: 'paynow',
  status: 'disabled',
  paynowType: 'uen',
  paynowMobile: '',
  paynowUen: '',
  merchantName: '',
  currency: 'SGD',
  configJson: '{}',
  version: null
})
const quickPayForm = reactive({
  referencePrefix: 'QP',
  dailyStartNumber: 0,
  presetAmounts: ['5', '10', '20', '50', '100', '200']
})

const activeIdentifierLabel = computed(() =>
  form.paynowType === 'mobile'
    ? gt('generated.tenant-admin-payment-settings.014')
    : gt('generated.tenant-admin-payment-settings.015')
)

onMounted(() => {
  void loadProfile()
})

async function loadProfile(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await getPaymentProfile(storeId.value)
    profileExists.value = true
    Object.assign(form, {
      method: response.profile.method,
      status: response.profile.status,
      paynowType: response.profile.paynowType,
      paynowMobile: response.profile.paynowMobile ?? '',
      paynowUen: response.profile.paynowUen ?? '',
      merchantName: response.profile.merchantName,
      currency: response.profile.currency,
      configJson: response.profile.configJson || '{}',
      version: response.profile.version
    })
    syncQuickPayFormFromConfig()
  } catch (error) {
    if (error instanceof PaymentApiError && error.response.error.code === 'PAYMENT_PROFILE_NOT_FOUND') {
      profileExists.value = false
      Object.assign(form, defaultProfile())
      return
    }
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitProfile(): Promise<void> {
  if (saving.value) {
    return
  }

  const localError = localValidationText()
  if (localError) {
    errorText.value = localError
    savedText.value = ''
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updatePaymentProfile(storeId.value, normalizedRequest())
    profileExists.value = true
    Object.assign(form, {
      method: response.profile.method,
      status: response.profile.status,
      paynowType: response.profile.paynowType,
      paynowMobile: response.profile.paynowMobile ?? '',
      paynowUen: response.profile.paynowUen ?? '',
      merchantName: response.profile.merchantName,
      currency: response.profile.currency,
      configJson: response.profile.configJson || '{}',
      version: response.profile.version
    })
    syncQuickPayFormFromConfig()
    savedText.value = gt('generated.tenant-admin-payment-settings.020')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function generateTestQr(): Promise<void> {
  if (testingQr.value || !storeId.value) {
    return
  }
  testingQr.value = true
  errorText.value = ''
  savedText.value = ''
  testQr.value = null
  try {
    const response = await generatePaymentProfileTestQr(storeId.value)
    testQr.value = response.testQr
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    testingQr.value = false
  }
}

function defaultProfile(): PaymentProfileMutation {
  return {
    method: 'paynow',
    status: 'disabled',
    paynowType: 'uen',
    paynowMobile: '',
    paynowUen: '',
    merchantName: '',
    currency: 'SGD',
    configJson: '{}',
    version: null
  }
}

function normalizedRequest(): PaymentProfileMutation {
  const configJson = mergeQuickPayConfigIntoProfileConfigJson(form.configJson, {
    referencePrefix: quickPayForm.referencePrefix,
    dailyStartNumber: quickPayForm.dailyStartNumber,
    presetAmounts: quickPayForm.presetAmounts
  })
  return {
    method: 'paynow',
    status: form.status,
    paynowType: form.paynowType,
    paynowMobile: form.paynowType === 'mobile' ? normalizeSingaporeMobile(form.paynowMobile) : normalizeOptionalText(form.paynowMobile),
    paynowUen: normalizeOptionalText(form.paynowUen),
    merchantName: form.merchantName.trim(),
    currency: 'SGD',
    configJson,
    version: form.version ?? null
  }
}

function localValidationText(): string {
  if (!isValidJson(form.configJson)) {
    return gt('generated.tenant-admin-payment-settings.021')
  }
  if (!quickPayForm.referencePrefix.trim() || quickPayForm.referencePrefix.trim().length > 3) {
    return gt('generated.tenant-admin-payment-settings.035')
  }
  if (!parsePresetAmountText(quickPayForm.presetAmounts.join(',')).length) {
    return gt('generated.tenant-admin-payment-settings.036')
  }
  if (form.status !== 'active') {
    return ''
  }
  if (!form.merchantName.trim()) {
    return gt('generated.tenant-admin-payment-settings.022')
  }
  if (form.paynowType === 'mobile' && !String(form.paynowMobile ?? '').trim()) {
    return gt('generated.tenant-admin-payment-settings.023')
  }
  if (form.paynowType === 'mobile' && !normalizeSingaporeMobile(form.paynowMobile)) {
    return gt('generated.tenant-admin-payment-settings.037')
  }
  if (form.paynowType === 'uen' && !String(form.paynowUen ?? '').trim()) {
    return gt('generated.tenant-admin-payment-settings.024')
  }
  return ''
}

function syncQuickPayFormFromConfig(): void {
  const config = readQuickPayConfigFromProfileConfigJson(form.configJson)
  quickPayForm.referencePrefix = config.referencePrefix
  quickPayForm.dailyStartNumber = config.dailyStartNumber
  quickPayForm.presetAmounts = [...config.presetAmounts]
}

function normalizeSingaporeMobile(value: string | null | undefined): string | null {
  const compact = String(value ?? '').trim().replace(/[\s-]/g, '')
  if (!compact) {
    return null
  }
  const withoutCountry = compact.startsWith('+65')
    ? compact.slice(3)
    : compact.startsWith('65') && compact.length === 10
      ? compact.slice(2)
      : compact
  return /^[689]\d{7}$/.test(withoutCountry) ? `+65${withoutCountry}` : null
}

function isValidJson(value: string): boolean {
  try {
    JSON.parse(value.trim() || '{}')
    return true
  } catch {
    return false
  }
}

function normalizeOptionalText(value: string | null | undefined): string | null {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.tenant-admin-payment-settings.025')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-payment-settings.026')
  }
  if (isAppGateError(error.response.error.code, error.response.error.messageKey)) {
    return formatAppGateErrorMessage(error.response.error, gt('generated.tenant-admin-payment-settings.025'))
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-payment-settings.027')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-payment-settings.028')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return gt('generated.tenant-admin-payment-settings.029')
  }
  return gt('generated.tenant-admin-payment-settings.025')
}

function isAppGateError(code: string, messageKey: string): boolean {
  return messageKey.startsWith('appgate.') || code === 'PERMISSION_DENIED' || messageKey === 'appgate.permission_denied'
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-payment-settings.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-payment-settings.002') }}</h1>
        </div>
        <div class="page-actions">
          <RouterLink class="quick-pay-link" :to="{ name: 'tenant-admin-payment-records', params: { storeId } }">
            {{ gt('generated.tenant-admin-payment-settings.045') }}
          </RouterLink>
          <RouterLink class="quick-pay-link" :to="{ name: 'tenant-admin-payment-proof-templates', params: { storeId } }">
            {{ gt('generated.tenant-admin-payment-proof-templates.001') }}
          </RouterLink>
          <RouterLink class="quick-pay-link" :to="{ name: 'payment-quick-pay', params: { storeId } }">
            {{ gt('generated.tenant-admin-payment-settings.003') }}
          </RouterLink>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-payment-settings.004') }}</p>

      <form v-else class="payment-form" @submit.prevent="submitProfile">
        <section class="form-section" :aria-label="gt('generated.tenant-admin-payment-settings.005')">
          <header>
            <h2>{{ gt('generated.tenant-admin-payment-settings.005') }}</h2>
            <span>{{ profileExists ? gt('generated.tenant-admin-payment-settings.006') : gt('generated.tenant-admin-payment-settings.007') }}</span>
          </header>

          <div class="field-grid">
            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.008') }}</span>
              <select v-model="form.status">
                <option value="disabled">{{ gt('generated.tenant-admin-payment-settings.009') }}</option>
                <option value="active">{{ gt('generated.tenant-admin-payment-settings.010') }}</option>
              </select>
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.011') }}</span>
              <input value="PayNow" disabled />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.012') }}</span>
              <input v-model.trim="form.currency" disabled />
            </label>
          </div>
        </section>

        <section class="form-section" :aria-label="gt('generated.tenant-admin-payment-settings.013')">
          <header>
            <h2>{{ gt('generated.tenant-admin-payment-settings.013') }}</h2>
            <span>{{ activeIdentifierLabel }}</span>
          </header>

          <div class="field-grid">
            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.016') }}</span>
              <select v-model="form.paynowType">
                <option value="uen">{{ gt('generated.tenant-admin-payment-settings.015') }}</option>
                <option value="mobile">{{ gt('generated.tenant-admin-payment-settings.014') }}</option>
              </select>
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.017') }}</span>
              <input v-model.trim="form.merchantName" maxlength="80" />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.014') }}</span>
              <input v-model.trim="form.paynowMobile" inputmode="tel" maxlength="32" />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.015') }}</span>
              <input v-model.trim="form.paynowUen" maxlength="32" />
            </label>
          </div>
        </section>

        <section class="form-section form-section--wide" :aria-label="gt('generated.tenant-admin-payment-settings.018')">
          <header>
            <h2>{{ gt('generated.tenant-admin-payment-settings.018') }}</h2>
            <span>{{ gt('generated.tenant-admin-payment-settings.019') }}</span>
          </header>
          <textarea v-model.trim="form.configJson" rows="5" spellcheck="false"></textarea>
        </section>

        <section class="form-section form-section--wide" :aria-label="gt('generated.tenant-admin-payment-settings.032')">
          <header>
            <h2>{{ gt('generated.tenant-admin-payment-settings.032') }}</h2>
            <span>{{ gt('generated.tenant-admin-payment-settings.033') }}</span>
          </header>

          <div class="field-grid">
            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.034') }}</span>
              <input v-model.trim="quickPayForm.referencePrefix" maxlength="3" />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-payment-settings.038') }}</span>
              <input v-model.number="quickPayForm.dailyStartNumber" inputmode="numeric" min="0" max="9999" type="number" />
            </label>
          </div>

          <div class="preset-settings">
            <strong>{{ gt('generated.tenant-admin-payment-settings.039') }}</strong>
            <div class="preset-input-grid">
              <label v-for="(_, index) in quickPayForm.presetAmounts" :key="index">
                <span>{{ index + 1 }}</span>
                <input v-model.trim="quickPayForm.presetAmounts[index]" inputmode="decimal" />
              </label>
            </div>
          </div>

          <div class="test-qr-actions">
            <button class="secondary-button" type="button" :disabled="testingQr" @click="generateTestQr">
              {{ testingQr ? gt('generated.tenant-admin-payment-settings.040') : gt('generated.tenant-admin-payment-settings.041') }}
            </button>
          </div>

          <article v-if="testQr" class="test-qr-panel">
            <div>
              <strong>{{ testQr.currency }} {{ testQr.amount }}</strong>
              <span>{{ testQr.paymentReference }}</span>
            </div>
            <DownloadableQrCode
              :description="gt('generated.tenant-admin-payment-settings.042')"
              :download-label="gt('generated.tenant-admin-payment-settings.043')"
              file-name="paynow-test-010.png"
              :size="180"
              :title="gt('generated.tenant-admin-payment-settings.044')"
              :value="testQr.qrPayload"
            />
          </article>
        </section>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? gt('generated.tenant-admin-payment-settings.030') : gt('generated.tenant-admin-payment-settings.031') }}
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

.page-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.page-heading span,
.form-section header span {
  color: #64748b;
  font-size: 13px;
  font-weight: 750;
}

.page-heading h1,
.form-section h2 {
  color: #0f172a;
  margin: 0;
}

.page-heading h1 {
  font-size: 24px;
}

.quick-pay-link,
.primary-button,
.secondary-button {
  align-items: center;
  border-radius: 6px;
  display: inline-flex;
  font: inherit;
  font-weight: 850;
  min-height: 38px;
  padding: 0 14px;
}

.quick-pay-link {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  text-decoration: none;
}

.secondary-button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #0f172a;
  cursor: pointer;
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

.payment-form {
  display: grid;
  gap: 14px;
  max-width: 980px;
}

.form-section {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 14px;
  padding: 18px;
}

.form-section header {
  align-items: start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.form-section h2 {
  font-size: 17px;
}

.field-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 750;
  gap: 7px;
}

input,
select,
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

input:disabled {
  background: #f8fafc;
  color: #64748b;
}

textarea {
  line-height: 1.45;
  resize: vertical;
}

.preset-settings {
  display: grid;
  gap: 10px;
}

.preset-settings > strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.preset-input-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.preset-input-grid label {
  gap: 5px;
}

.preset-input-grid label span {
  color: #64748b;
  font-size: 12px;
}

.test-qr-actions {
  display: flex;
  justify-content: flex-start;
}

.test-qr-panel {
  align-items: start;
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 12px;
}

.test-qr-panel > div {
  display: grid;
  gap: 6px;
}

.test-qr-panel strong {
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
}

.test-qr-panel span {
  color: #475569;
  font-size: 13px;
  font-weight: 850;
  overflow-wrap: anywhere;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  background: #0f766e;
  border: 0;
  color: #ffffff;
  cursor: pointer;
}

.primary-button:disabled {
  cursor: default;
  opacity: 0.6;
}

@media (max-width: 980px) {
  .tenant-shell,
  .field-grid,
  .preset-input-grid,
  .test-qr-panel {
    grid-template-columns: 1fr;
  }

  .form-section header,
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .page-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .quick-pay-link,
  .primary-button {
    justify-content: center;
    width: 100%;
  }

  .page-actions {
    display: grid;
  }
}
</style>
