<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createPaymentIntent,
  PaymentApiError
} from '../api/paymentApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentIntentCreateResponse } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'
import {
  buildPaymentPresentPayload,
  MAX_PRESENT_PAYMENTS,
  parsePresetAmountText,
  paymentPresentUrl,
  publishPaymentPresentPayload,
  publishPaymentPresentSettings,
  readPaymentPresentRecent,
  readPaymentPresentSettings,
  readQuickPayPresetAmounts,
  saveQuickPayPresetAmounts,
  type PaymentPresentSettings,
  type PaymentPresentRecentItem
} from '../utils/paymentPresentBridge'

const amountKeys = ['7', '8', '9', 'backspace', '4', '5', '6', 'clear', '1', '2', '3', '00', '0', '.']
const presentMaxPaymentOptions = [1, 2, 3, 4] as const
const terminalStorageKey = 'rpb.payment.quickPay.terminalCode'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()
const { gt } = useGeneratedText()

const amountText = ref('')
const noteText = ref('')
const terminalCode = ref('T1')
const creating = ref(false)
const errorText = ref('')
const noticeText = ref('')
const createdResult = ref<PaymentIntentCreateResponse | null>(null)
const amountPresets = ref<string[]>([])
const presetEditorOpen = ref(false)
const presetEditorText = ref('')
const presentSettings = ref<PaymentPresentSettings>({
  maxPayments: 1,
  qrPerPayment: 1,
  primaryQr: 'sgqr'
})
const presentSettingsEditorOpen = ref(false)
const presentSettingsMaxPayments = ref<PaymentPresentSettings['maxPayments']>(1)
const recentItems = ref<PaymentPresentRecentItem[]>([])
let presentWindow: Window | null = null

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-quick-pay.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-quick-pay.002'))
const cashierName = computed(() => auth.user?.username || null)
const numericAmount = computed(() => Number(amountText.value))
const amountDisplay = computed(() => amountText.value || '0.00')
const canCreate = computed(() => Number.isFinite(numericAmount.value) && numericAmount.value > 0 && !creating.value)
const displayNumberText = computed(() => createdResult.value ? String(createdResult.value.nextDisplayNumber) : gt('generated.payment-quick-pay.026'))
const normalizedTerminalCode = computed(() => normalizeOptionalText(terminalCode.value) || 'T1')

onMounted(() => {
  try {
    terminalCode.value = window.localStorage.getItem(terminalStorageKey) || 'T1'
  } catch {
    terminalCode.value = 'T1'
  }
  reloadLocalPaymentState()
})

watch([storeId, normalizedTerminalCode], () => {
  reloadLocalPaymentState()
})

watch(terminalCode, value => {
  try {
    window.localStorage.setItem(terminalStorageKey, value.trim() || 'T1')
  } catch {
    // Ignore storage failures; terminal code still works for the current page.
  }
})

function reloadLocalPaymentState(): void {
  if (!storeId.value) {
    amountPresets.value = []
    recentItems.value = []
    presentSettings.value = {
      maxPayments: 1,
      qrPerPayment: 1,
      primaryQr: 'sgqr'
    }
    return
  }
  amountPresets.value = readQuickPayPresetAmounts(storeId.value)
  presentSettings.value = readPaymentPresentSettings(storeId.value, normalizedTerminalCode.value)
  recentItems.value = readPaymentPresentRecent(storeId.value, normalizedTerminalCode.value)
}

function appendAmountToken(token: string): void {
  if (token === '.') {
    if (!amountText.value.includes('.')) {
      amountText.value = amountText.value ? `${amountText.value}.` : '0.'
    }
    return
  }

  const next = `${amountText.value}${token}`.replace(/^0+(?=\d)/, '')
  amountText.value = limitCurrencyDecimals(next)
}

function backspaceAmount(): void {
  amountText.value = amountText.value.slice(0, -1)
}

function clearAmount(): void {
  amountText.value = ''
}

function amountKeyLabel(key: string): string {
  if (key === 'backspace') {
    return '⌫'
  }
  if (key === 'clear') {
    return 'C'
  }
  return key
}

function pressAmountKey(key: string): void {
  if (key === 'backspace') {
    backspaceAmount()
    return
  }
  if (key === 'clear') {
    clearAmount()
    return
  }
  appendAmountToken(key)
}

function applyPreset(value: string): void {
  amountText.value = value
}

function openPresetEditor(): void {
  presetEditorText.value = amountPresets.value.join(', ')
  presetEditorOpen.value = true
}

function savePresetEditor(): void {
  if (!storeId.value) {
    return
  }
  const parsed = parsePresetAmountText(presetEditorText.value)
  if (!parsed.length) {
    errorText.value = gt('generated.payment-quick-pay.027')
    return
  }
  amountPresets.value = saveQuickPayPresetAmounts(storeId.value, parsed)
  presetEditorOpen.value = false
  errorText.value = ''
}

function openPresentSettingsEditor(): void {
  presentSettingsMaxPayments.value = presentSettings.value.maxPayments
  presentSettingsEditorOpen.value = true
}

function savePresentSettingsEditor(): void {
  if (!storeId.value) {
    return
  }
  presentSettings.value = publishPaymentPresentSettings(storeId.value, normalizedTerminalCode.value, {
    maxPayments: presentSettingsMaxPayments.value,
    qrPerPayment: 1,
    primaryQr: 'sgqr'
  })
  presentSettingsEditorOpen.value = false
}

async function submitQuickPay(): Promise<void> {
  if (!canCreate.value || !storeId.value) {
    return
  }

  openPresentWindow()
  creating.value = true
  errorText.value = ''
  noticeText.value = ''
  createdResult.value = null

  try {
    const response = await createPaymentIntent(storeId.value, {
      idempotencyKey: createIdempotencyKey(),
      sourceType: 'quick_pay',
      sourceId: null,
      method: 'paynow',
      amount: numericAmount.value.toFixed(2),
      currency: 'SGD',
      terminalCode: normalizedTerminalCode.value,
      cashierName: cashierName.value,
      requestedDisplayNumber: null,
      metadataJson: JSON.stringify({
        note: normalizeOptionalText(noteText.value),
        channel: 'staff_quick_pay'
      })
    })
    createdResult.value = response
    publishPaymentPresentPayload(buildPaymentPresentPayload(response, storeId.value, normalizedTerminalCode.value))
    recentItems.value = readPaymentPresentRecent(storeId.value, normalizedTerminalCode.value)
    noticeText.value = gt('generated.payment-quick-pay.028', { displayNumber: response.session.displayNumber })
    amountText.value = ''
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    creating.value = false
  }
}

function openPresentWindow(): void {
  if (!storeId.value) {
    return
  }
  const href = router.resolve({
    name: 'payment-present',
    params: {
      storeId: storeId.value,
      terminalCode: normalizedTerminalCode.value
    }
  }).href || paymentPresentUrl(storeId.value, normalizedTerminalCode.value)
  const target = `rpb-paynow-present-${storeId.value}-${normalizedTerminalCode.value}`
  presentWindow = window.open(href, target, 'popup=yes,width=1280,height=760') || presentWindow
  try {
    presentWindow?.focus()
  } catch {
    // Some browsers block focusing named windows; the payment flow can continue.
  }
}

function limitCurrencyDecimals(value: string): string {
  const cleaned = value.replace(/[^\d.]/g, '')
  const firstDot = cleaned.indexOf('.')
  if (firstDot < 0) {
    return cleaned.slice(0, 8)
  }
  const whole = cleaned.slice(0, firstDot).replace(/\./g, '').slice(0, 8) || '0'
  const decimals = cleaned.slice(firstDot + 1).replace(/\./g, '').slice(0, 2)
  return `${whole}.${decimals}`
}

function createIdempotencyKey(): string {
  return `quick-pay-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function normalizeOptionalText(value: string): string | null {
  const normalized = value.trim()
  return normalized || null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.payment-quick-pay.003')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.payment-quick-pay.004')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.payment-quick-pay.005')
  }
  if (error.response.error.code === 'PERMISSION_DENIED' || error.response.error.messageKey === 'appgate.permission_denied') {
    return formatAppGateErrorMessage(error.response.error, gt('generated.payment-quick-pay.005'))
  }
  if (error.response.error.code === 'PAYMENT_PROFILE_NOT_FOUND') {
    return gt('generated.payment-quick-pay.006')
  }
  if (error.response.error.code === 'PAYMENT_PROFILE_DISABLED') {
    return gt('generated.payment-quick-pay.007')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.payment-quick-pay.008')
  }
  return gt('generated.payment-quick-pay.003')
}
</script>

<template>
  <main class="staff-workbench-shell payment-shell">
    <StaffHomeTopBar
      :app-status-label="gt('generated.payment-quick-pay.009')"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <div class="topbar-actions">
          <button class="display-button" type="button" @click="openPresentWindow">
            {{ gt('generated.payment-quick-pay.029') }}
          </button>
          <button class="display-button" type="button" @click="openPresentSettingsEditor">
            {{ gt('generated.payment-quick-pay.041') }}
          </button>
        </div>
      </template>
    </StaffHomeTopBar>

    <section class="payment-body">
      <div class="terminal-meta">
        <div>
          <span>{{ gt('generated.payment-quick-pay.014') }}</span>
          <strong>{{ normalizedTerminalCode }}</strong>
        </div>
        <label>
          <span>{{ gt('generated.payment-quick-pay.030') }}</span>
          <input v-model.trim="terminalCode" maxlength="32" />
        </label>
      </div>

      <form class="calculator-panel" @submit.prevent="submitQuickPay">
        <section class="business-day-card">
          <div>
            <strong>{{ gt('generated.payment-quick-pay.031') }}</strong>
            <span>{{ currentBusinessDate }} · {{ gt('generated.payment-quick-pay.032') }}</span>
          </div>
        </section>

        <div class="display-no">
          <span>{{ gt('generated.payment-quick-pay.033') }}</span>
          <strong>{{ displayNumberText }}</strong>
        </div>

        <output class="calc-display">{{ amountDisplay }}</output>

        <div class="keypad-grid" :aria-label="gt('generated.payment-quick-pay.034')">
          <button
            v-for="key in amountKeys"
            :key="key"
            :class="{ 'key-zero': key === '0', 'key-function': key === 'backspace' || key === 'clear' }"
            type="button"
            @click="pressAmountKey(key)"
          >
            {{ amountKeyLabel(key) }}
          </button>
        </div>

        <section class="preset-section">
          <header>
            <strong>{{ gt('generated.payment-quick-pay.013') }}</strong>
            <button type="button" @click="openPresetEditor">{{ gt('generated.payment-quick-pay.035') }}</button>
          </header>
          <div class="preset-grid">
            <button
              v-for="preset in amountPresets"
              :key="preset"
              type="button"
              @click="applyPreset(preset)"
            >
              {{ preset }}
            </button>
          </div>
        </section>

        <div class="detail-grid">
          <label>
            <span>{{ gt('generated.payment-quick-pay.015') }}</span>
            <input v-model.trim="noteText" maxlength="120" />
          </label>
        </div>

        <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
        <p v-if="noticeText" class="notice-banner">{{ noticeText }}</p>

        <button class="primary-button" type="submit" :disabled="!canCreate">
          {{ creating ? gt('generated.payment-quick-pay.016') : gt('generated.payment-quick-pay.017') }}
        </button>
      </form>

      <section class="recent-panel">
        <h2>{{ gt('generated.payment-quick-pay.036') }}</h2>
        <div v-if="recentItems.length" class="recent-grid">
          <article v-for="item in recentItems" :key="item.sessionNo" class="recent-card">
            <strong>{{ item.displayNumber }}</strong>
            <div>
              <span>{{ item.currency }} {{ item.amount }}</span>
              <small>{{ item.status }}</small>
            </div>
          </article>
        </div>
        <p v-else class="recent-empty">-</p>
      </section>
    </section>

    <div v-if="presetEditorOpen" class="modal-backdrop" role="presentation" @click.self="presetEditorOpen = false">
      <section class="preset-editor" role="dialog" :aria-label="gt('generated.payment-quick-pay.037')">
        <h2>{{ gt('generated.payment-quick-pay.037') }}</h2>
        <label>
          <span>{{ gt('generated.payment-quick-pay.038') }}</span>
          <textarea v-model="presetEditorText" rows="3" placeholder="5, 10, 20, 50, 100, 200"></textarea>
        </label>
        <div class="editor-actions">
          <button type="button" class="secondary-button" @click="presetEditorOpen = false">
            {{ gt('generated.payment-quick-pay.039') }}
          </button>
          <button type="button" class="primary-button" @click="savePresetEditor">
            {{ gt('generated.payment-quick-pay.040') }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="presentSettingsEditorOpen" class="modal-backdrop" role="presentation" @click.self="presentSettingsEditorOpen = false">
      <section class="preset-editor present-settings-editor" role="dialog" :aria-label="gt('generated.payment-quick-pay.042')">
        <h2>{{ gt('generated.payment-quick-pay.042') }}</h2>
        <p class="settings-help">{{ gt('generated.payment-quick-pay.043', { max: MAX_PRESENT_PAYMENTS }) }}</p>

        <fieldset class="radio-group">
          <legend>{{ gt('generated.payment-quick-pay.044') }}</legend>
          <label v-for="option in presentMaxPaymentOptions" :key="option" class="radio-option">
            <input v-model.number="presentSettingsMaxPayments" type="radio" name="max-present-payments" :value="option" />
            <span>{{ option }}</span>
          </label>
        </fieldset>

        <dl class="fixed-qr-settings">
          <div>
            <dt>{{ gt('generated.payment-quick-pay.045') }}</dt>
            <dd>1</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-quick-pay.046') }}</dt>
            <dd>SGQR</dd>
          </div>
        </dl>

        <div class="editor-actions">
          <button type="button" class="secondary-button" @click="presentSettingsEditorOpen = false">
            {{ gt('generated.payment-quick-pay.039') }}
          </button>
          <button type="button" class="primary-button" @click="savePresentSettingsEditor">
            {{ gt('generated.payment-quick-pay.040') }}
          </button>
        </div>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="payment" />
  </main>
</template>

<style scoped>
.payment-shell {
  background: #eef6f4;
  color: #102033;
  min-height: 100dvh;
}

.display-button,
.preset-section header button,
.secondary-button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #0f172a;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 10px;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.payment-body {
  display: grid;
  gap: 10px;
  margin: 0 auto;
  max-width: 720px;
  padding: 12px 10px calc(92px + env(safe-area-inset-bottom));
}

.terminal-meta,
.calculator-panel,
.recent-panel,
.preset-editor {
  background: #ffffff;
  border: 1px solid #d6e4e2;
  border-radius: 8px;
}

.terminal-meta {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(190px, 260px);
  padding: 10px;
}

.terminal-meta > div,
label {
  display: grid;
  gap: 6px;
}

.terminal-meta span,
label span,
.display-no span,
.business-day-card span,
.recent-card small {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 850;
}

.terminal-meta strong {
  font-size: 0.95rem;
  font-weight: 950;
}

.calculator-panel {
  display: grid;
  gap: 8px;
  padding: 10px;
}

.business-day-card {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 10px;
}

.business-day-card div {
  display: grid;
  gap: 2px;
}

.display-no {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.display-no strong {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  min-width: 84px;
  padding: 7px 12px;
  text-align: right;
}

.calc-display {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  display: flex;
  font-size: 2rem;
  font-weight: 950;
  height: 66px;
  justify-content: flex-end;
  padding: 0 14px;
}

.keypad-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.keypad-grid button,
.preset-grid button {
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  color: #0f172a;
  cursor: pointer;
  font: inherit;
  font-weight: 950;
  min-height: 40px;
}

.keypad-grid .key-function {
  background: #f3f4f6;
}

.keypad-grid .key-zero {
  grid-column: span 2;
}

.preset-section {
  border-top: 1px solid #e5e7eb;
  display: grid;
  gap: 8px;
  padding-top: 10px;
}

.preset-section header {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.preset-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detail-grid {
  display: grid;
  gap: 10px;
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
  padding: 8px 10px;
  width: 100%;
}

textarea {
  resize: vertical;
}

.primary-button {
  background: #0f766e;
  border: 0;
  border-radius: 8px;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-weight: 950;
  min-height: 48px;
}

.primary-button:disabled {
  cursor: default;
  opacity: 0.58;
}

.error-banner,
.notice-banner {
  border-radius: 6px;
  margin: 0;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.notice-banner {
  background: #ecfdf5;
  border: 1px solid #bbf7d0;
  color: #047857;
  font-weight: 850;
}

.recent-panel {
  display: grid;
  gap: 10px;
  padding: 10px;
}

.recent-panel h2,
.preset-editor h2 {
  font-size: 0.92rem;
  font-weight: 950;
  margin: 0;
}

.recent-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.recent-card {
  align-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  display: flex;
  gap: 10px;
  min-height: 58px;
  min-width: 0;
  padding: 9px 10px;
}

.recent-card > strong {
  flex: 0 0 38px;
  font-size: 1.35rem;
  font-weight: 950;
  text-align: center;
}

.recent-card div {
  display: grid;
  min-width: 0;
}

.recent-card span {
  font-size: 0.84rem;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-empty {
  color: #94a3b8;
  font-weight: 900;
  margin: 0;
}

.modal-backdrop {
  align-items: end;
  background: rgba(15, 23, 42, 0.42);
  display: flex;
  inset: 0;
  padding: 16px;
  position: fixed;
  z-index: 30;
}

.preset-editor {
  display: grid;
  gap: 12px;
  margin: 0 auto;
  max-width: 520px;
  padding: 14px;
  width: 100%;
}

.editor-actions {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.settings-help {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
  margin: 0;
}

.radio-group {
  border: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  padding: 0;
}

.radio-group legend {
  color: #0f172a;
  flex: 0 0 100%;
  font-size: 0.82rem;
  font-weight: 950;
  margin-bottom: 2px;
}

.radio-option {
  align-items: center;
  display: inline-flex;
  gap: 6px;
  min-height: 28px;
}

.radio-option input {
  min-height: 0;
  width: auto;
}

.radio-option span {
  color: #0f172a;
  font-size: 0.86rem;
  font-weight: 900;
}

.fixed-qr-settings {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 10px;
}

.fixed-qr-settings div {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.fixed-qr-settings dt,
.fixed-qr-settings dd {
  font-size: 0.8rem;
  font-weight: 900;
  margin: 0;
}

.fixed-qr-settings dt {
  color: #64748b;
}

@media (max-width: 560px) {
  .terminal-meta,
  .recent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
