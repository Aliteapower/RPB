<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createPaymentIntent,
  endPaymentBusinessDay,
  getPaymentBusinessDay,
  getQuickPayRecords,
  getQuickPayTerminalConfig,
  manualConfirmQuickPay,
  openPaymentBusinessDay,
  PaymentApiError
} from '../api/paymentApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentBusinessDayStatus, PaymentIntentCreateResponse, QuickPayRecordSummary } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'
import {
  buildPaymentPresentPayload,
  confirmPaymentPresentPayment,
  isPaymentPresentPayloadActive,
  isPaymentPresentRecentPending,
  MAX_PRESENT_PAYMENTS,
  parsePresetAmountText,
  paymentPresentUrl,
  publishPaymentPresentPayload,
  publishPaymentPresentSettings,
  readPaymentPresentPayloads,
  readPaymentPresentRecent,
  readPaymentPresentSettings,
  readQuickPayPresetAmounts,
  saveQuickPayPresetAmounts,
  subscribePaymentPresentPayloads,
  type PaymentPresentPayload,
  type PaymentPresentSettings,
  type PaymentPresentRecentItem
} from '../utils/paymentPresentBridge'

const amountKeys = ['7', '8', '9', 'backspace', '4', '5', '6', 'clear', '1', '2', '3', '00', '0', '.']
const presentMaxPaymentOptions = [1, 2, 3, 4, 5, 6] as const
const terminalStorageKey = 'rpb.payment.quickPay.terminalCode'
type PaymentReportMode = 'mine' | 'terminal'

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
const paymentOptionsOpen = ref(false)
const presentSettings = ref<PaymentPresentSettings>({
  maxPayments: 1,
  qrPerPayment: 1,
  primaryQr: 'sgqr',
  recentExpiredHoldSeconds: 20
})
const presentSettingsEditorOpen = ref(false)
const presentSettingsMaxPayments = ref<PaymentPresentSettings['maxPayments']>(1)
const presentSettingsRecentExpiredHoldSeconds = ref(20)
const recentItems = ref<PaymentPresentRecentItem[]>([])
const manuallyConfirmingSessionNo = ref('')
const activePresentPayloadCount = ref(0)
const reportMode = ref<PaymentReportMode>('mine')
const reportLoading = ref(false)
const reportErrorText = ref('')
const reportSummary = ref<QuickPayRecordSummary>(emptyReportSummary())
const businessDayLoading = ref(false)
const openingBusinessDay = ref(false)
const endingBusinessDay = ref(false)
const businessDayStatus = ref<PaymentBusinessDayStatus>('not_open')
const openedBusinessDate = ref('')
let presentWindow: Window | null = null
let proofReviewWindow: Window | null = null
let unsubscribePresentPayloads: (() => void) | null = null
let presentCapacityTimer: number | null = null
let profileLoadSequence = 0
let businessDayLoadSequence = 0
let reportLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-quick-pay.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-quick-pay.002'))
const cashierName = computed(() => auth.user?.username || null)
const numericAmount = computed(() => Number(amountText.value))
const amountDisplay = computed(() => amountText.value || '0.00')
const presentCapacityFull = computed(() => activePresentPayloadCount.value >= presentSettings.value.maxPayments)
const canCreate = computed(() => Number.isFinite(numericAmount.value) && numericAmount.value > 0 && !creating.value && !presentCapacityFull.value)
const displayNumberText = computed(() => createdResult.value ? String(createdResult.value.nextDisplayNumber) : gt('generated.payment-quick-pay.026'))
const normalizedTerminalCode = computed(() => normalizeOptionalText(terminalCode.value) || 'T1')
const displayedBusinessDate = computed(() => openedBusinessDate.value || currentBusinessDate.value)
const businessDayOpen = computed(() => businessDayStatus.value === 'open')
const businessDayStatusLabel = computed(() => {
  if (businessDayStatus.value === 'open') {
    return gt('generated.payment-quick-pay.032')
  }
  if (businessDayStatus.value === 'closed') {
    return gt('generated.payment-quick-pay.050')
  }
  return gt('generated.payment-quick-pay.049')
})
const reportScopeLabel = computed(() => (
  reportMode.value === 'mine'
    ? gt('generated.payment-quick-pay.066')
    : gt('generated.payment-quick-pay.067', { terminal: normalizedTerminalCode.value })
))
const reportContextText = computed(() => gt('generated.payment-quick-pay.072', {
  terminal: normalizedTerminalCode.value,
  businessDate: displayedBusinessDate.value,
  scope: reportScopeLabel.value
}))
const showOpenTodayButton = computed(() => !businessDayOpen.value)
const showEndDayButton = computed(() => businessDayOpen.value)

onMounted(() => {
  try {
    terminalCode.value = window.localStorage.getItem(terminalStorageKey) || 'T1'
  } catch {
    terminalCode.value = 'T1'
  }
  loadPersistedReportMode()
  reloadLocalPaymentState()
  resetPresentCapacityTracking()
  presentCapacityTimer = window.setInterval(() => {
    refreshPresentCapacity()
    refreshRecent()
  }, 1000)
  void loadPaymentBusinessDay()
  void loadQuickPayProfileDefaults()
  void loadPaymentReport()
})

onBeforeUnmount(() => {
  unsubscribePresentPayloads?.()
  unsubscribePresentPayloads = null
  if (presentCapacityTimer) {
    window.clearInterval(presentCapacityTimer)
    presentCapacityTimer = null
  }
})

watch([storeId, normalizedTerminalCode], () => {
  loadPersistedReportMode()
  reloadLocalPaymentState()
  resetPresentCapacityTracking()
  void loadPaymentReport()
})

watch(storeId, () => {
  void loadPaymentBusinessDay()
  void loadQuickPayProfileDefaults()
})

watch(displayedBusinessDate, () => {
  void loadPaymentReport()
})

watch(cashierName, () => {
  if (reportMode.value === 'mine') {
    void loadPaymentReport()
  }
})

watch(reportMode, () => {
  savePersistedReportMode()
  void loadPaymentReport()
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
      primaryQr: 'sgqr',
      recentExpiredHoldSeconds: 20
    }
    return
  }
  amountPresets.value = readQuickPayPresetAmounts(storeId.value)
  presentSettings.value = readPaymentPresentSettings(storeId.value, normalizedTerminalCode.value)
  refreshRecent()
  refreshPresentCapacity()
}

async function loadQuickPayProfileDefaults(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++profileLoadSequence
  if (!currentStoreId) {
    return
  }
  try {
    const response = await getQuickPayTerminalConfig(currentStoreId)
    if (sequence !== profileLoadSequence) {
      return
    }
    const config = response.terminalConfig
    amountPresets.value = readQuickPayPresetAmounts(currentStoreId, config.presetAmounts)
  } catch {
    // Keep the terminal usable with local/default presets if settings are unavailable.
  }
}

async function loadPaymentBusinessDay(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++businessDayLoadSequence
  if (!currentStoreId) {
    businessDayStatus.value = 'not_open'
    openedBusinessDate.value = ''
    return
  }

  businessDayLoading.value = true
  try {
    const response = await getPaymentBusinessDay(currentStoreId)
    if (sequence !== businessDayLoadSequence) {
      return
    }
    businessDayStatus.value = response.status
    openedBusinessDate.value = response.businessDate
  } catch (error) {
    if (error instanceof PaymentApiError && (error.status === 401 || error.status === 403 || error.response.error.code === 'PERMISSION_DENIED')) {
      errorText.value = apiErrorText(error)
    }
  } finally {
    if (sequence === businessDayLoadSequence) {
      businessDayLoading.value = false
    }
  }
}

async function loadPaymentReport(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++reportLoadSequence
  if (!currentStoreId) {
    reportSummary.value = emptyReportSummary()
    return
  }
  if (reportMode.value === 'mine' && !cashierName.value) {
    reportSummary.value = emptyReportSummary()
    reportErrorText.value = ''
    return
  }

  reportLoading.value = true
  reportErrorText.value = ''
  try {
    const response = await getQuickPayRecords(currentStoreId, {
      businessDate: displayedBusinessDate.value,
      terminalCode: normalizedTerminalCode.value,
      cashierName: reportMode.value === 'mine' ? cashierName.value || undefined : undefined,
      limit: 200
    })
    if (sequence !== reportLoadSequence) {
      return
    }
    reportSummary.value = normalizeReportSummary(response.summary)
  } catch (error) {
    if (sequence === reportLoadSequence) {
      reportSummary.value = emptyReportSummary()
      reportErrorText.value = reportApiErrorText(error)
    }
  } finally {
    if (sequence === reportLoadSequence) {
      reportLoading.value = false
    }
  }
}

async function openBusinessDayToday(): Promise<void> {
  if (!storeId.value || openingBusinessDay.value) {
    return
  }
  openingBusinessDay.value = true
  errorText.value = ''
  noticeText.value = ''
  try {
    const response = await openPaymentBusinessDay(storeId.value)
    businessDayStatus.value = response.status
    openedBusinessDate.value = response.businessDate
    noticeText.value = gt('generated.payment-quick-pay.052', { businessDate: response.businessDate })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    openingBusinessDay.value = false
  }
}

async function endBusinessDay(): Promise<void> {
  if (!storeId.value || endingBusinessDay.value) {
    return
  }
  endingBusinessDay.value = true
  errorText.value = ''
  noticeText.value = ''
  try {
    const response = await endPaymentBusinessDay(storeId.value)
    businessDayStatus.value = response.status
    openedBusinessDate.value = response.businessDate
    noticeText.value = gt('generated.payment-quick-pay.054', { businessDate: response.businessDate })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    endingBusinessDay.value = false
  }
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
  presentSettingsRecentExpiredHoldSeconds.value = presentSettings.value.recentExpiredHoldSeconds
  presentSettingsEditorOpen.value = true
}

function savePresentSettingsEditor(): void {
  if (!storeId.value) {
    return
  }
  presentSettings.value = publishPaymentPresentSettings(storeId.value, normalizedTerminalCode.value, {
    maxPayments: presentSettingsMaxPayments.value,
    qrPerPayment: 1,
    primaryQr: 'sgqr',
    recentExpiredHoldSeconds: Number(presentSettingsRecentExpiredHoldSeconds.value)
  })
  presentSettingsEditorOpen.value = false
  refreshRecent()
  refreshPresentCapacity()
}

async function submitQuickPay(): Promise<void> {
  refreshPresentCapacity()
  if (presentCapacityFull.value) {
    errorText.value = ''
    noticeText.value = gt('generated.payment-quick-pay.055')
    return
  }
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
    businessDayStatus.value = 'open'
    openedBusinessDate.value = response.session.businessDate
    publishPaymentPresentPayload(buildPaymentPresentPayload(response, storeId.value, normalizedTerminalCode.value))
    refreshRecent()
    refreshPresentCapacity()
    void loadPaymentReport()
    noticeText.value = gt('generated.payment-quick-pay.028', { displayNumber: response.session.displayNumber })
    amountText.value = ''
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    creating.value = false
  }
}

async function confirmRecentPayment(item: PaymentPresentRecentItem): Promise<void> {
  if (!storeId.value || manuallyConfirmingSessionNo.value || !isManualConfirmableRecent(item)) {
    return
  }
  if (!window.confirm(gt('generated.payment-quick-pay.062'))) {
    return
  }
  manuallyConfirmingSessionNo.value = item.sessionNo
  errorText.value = ''
  noticeText.value = ''
  try {
    await manualConfirmQuickPay(storeId.value, item.sessionNo, {
      idempotencyKey: createManualConfirmIdempotencyKey(item.sessionNo),
      terminalCode: normalizedTerminalCode.value
    })
    recentItems.value = confirmPaymentPresentPayment(storeId.value, normalizedTerminalCode.value, item.sessionNo)
    refreshPresentCapacity()
    void loadPaymentReport()
    noticeText.value = gt('generated.payment-quick-pay.063', { displayNumber: item.displayNumber })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    manuallyConfirmingSessionNo.value = ''
  }
}

function isManualConfirmableRecent(item: PaymentPresentRecentItem): boolean {
  return isPaymentPresentRecentPending(item)
}

function resetPresentCapacityTracking(): void {
  unsubscribePresentPayloads?.()
  unsubscribePresentPayloads = null
  activePresentPayloadCount.value = 0
  if (!storeId.value) {
    return
  }
  unsubscribePresentPayloads = subscribePaymentPresentPayloads(
    storeId.value,
    normalizedTerminalCode.value,
    applyPresentPayloadCapacity
  )
  refreshPresentCapacity()
}

function refreshPresentCapacity(): void {
  if (!storeId.value) {
    activePresentPayloadCount.value = 0
    return
  }
  applyPresentPayloadCapacity(readPaymentPresentPayloads(storeId.value, normalizedTerminalCode.value))
}

function refreshRecent(): void {
  recentItems.value = storeId.value ? readPaymentPresentRecent(storeId.value, normalizedTerminalCode.value) : []
}

function applyPresentPayloadCapacity(payloads: PaymentPresentPayload[]): void {
  activePresentPayloadCount.value = payloads
    .filter(payload => isPaymentPresentPayloadActive(payload))
    .slice(0, presentSettings.value.maxPayments)
    .length
  if (!presentCapacityFull.value && noticeText.value === gt('generated.payment-quick-pay.055')) {
    noticeText.value = ''
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

function openProofReviewWindow(): void {
  if (!storeId.value) {
    return
  }
  const href = router.resolve({
    name: 'payment-proof-review',
    params: {
      storeId: storeId.value
    }
  }).href || `/stores/${storeId.value}/payments/proof-review`
  const target = `rpb-paynow-proof-review-${storeId.value}`
  proofReviewWindow = window.open(href, target, 'popup=yes,width=520,height=900') || proofReviewWindow
  try {
    proofReviewWindow?.focus()
  } catch {
    // Some browsers block focusing named windows; the review page remains available.
  }
}

function setReportMode(mode: PaymentReportMode): void {
  reportMode.value = mode
}

function loadPersistedReportMode(): void {
  try {
    const stored = window.localStorage.getItem(reportModeStorageKey())
    reportMode.value = stored === 'terminal' ? 'terminal' : 'mine'
  } catch {
    reportMode.value = 'mine'
  }
}

function savePersistedReportMode(): void {
  try {
    window.localStorage.setItem(reportModeStorageKey(), reportMode.value)
  } catch {
    // Report mode is a convenience preference; the report still works without persistence.
  }
}

function reportModeStorageKey(): string {
  return `rpb.payment.quickPay.reportMode.${storeId.value || 'unknown'}.${normalizedTerminalCode.value}`
}

function emptyReportSummary(): QuickPayRecordSummary {
  return {
    count: 0,
    pendingCount: 0,
    awaitingVerificationCount: 0,
    paidCount: 0,
    totalAmount: '0',
    pendingAmount: '0',
    awaitingVerificationAmount: '0',
    paidAmount: '0',
    currency: 'SGD'
  }
}

function normalizeReportSummary(summary: QuickPayRecordSummary): QuickPayRecordSummary {
  return {
    count: Number(summary.count || 0),
    pendingCount: Number(summary.pendingCount || 0),
    awaitingVerificationCount: Number(summary.awaitingVerificationCount || 0),
    paidCount: Number(summary.paidCount || 0),
    totalAmount: summary.totalAmount || '0',
    pendingAmount: summary.pendingAmount || '0',
    awaitingVerificationAmount: summary.awaitingVerificationAmount || '0',
    paidAmount: summary.paidAmount || '0',
    currency: summary.currency || 'SGD'
  }
}

function reportMoney(amount: string | number | null | undefined, currency = 'SGD'): string {
  const value = Number(amount ?? 0)
  const safeValue = Number.isFinite(value) ? value : 0
  return `${currency || 'SGD'} ${safeValue.toFixed(2)}`
}

function reportApiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.payment-quick-pay.073')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.payment-quick-pay.004')
  }
  if (error.response.error.code === 'PERMISSION_DENIED' || error.response.error.messageKey === 'appgate.permission_denied') {
    return formatAppGateErrorMessage(error.response.error, gt('generated.payment-quick-pay.073'))
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.payment-quick-pay.005')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.payment-quick-pay.008')
  }
  return gt('generated.payment-quick-pay.073')
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

function createManualConfirmIdempotencyKey(sessionNo: string): string {
  return `manual-confirm-${sessionNo}-${Date.now()}-${Math.random().toString(16).slice(2)}`
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
  if (error.response.error.code === 'PAYMENT_INTENT_STATE_CONFLICT') {
    return gt('generated.payment-quick-pay.064')
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
          <button class="display-button" type="button" @click="openProofReviewWindow">
            {{ gt('generated.payment-quick-pay.057') }}
          </button>
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
      <section class="payment-options-shell">
        <button
          class="payment-options-toggle"
          type="button"
          :aria-expanded="paymentOptionsOpen"
          @click="paymentOptionsOpen = !paymentOptionsOpen"
        >
          <span>{{ gt('generated.payment-quick-pay.058') }}</span>
          <strong>{{ normalizedTerminalCode }} · {{ displayedBusinessDate }} · {{ businessDayStatusLabel }}</strong>
          <em>{{ paymentOptionsOpen ? gt('generated.payment-quick-pay.060') : gt('generated.payment-quick-pay.059') }}</em>
        </button>

        <div v-if="paymentOptionsOpen" class="payment-options-panel">
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

          <section class="business-day-card" :class="{ 'business-day-card--not-open': !businessDayOpen }">
            <div>
              <strong>{{ gt('generated.payment-quick-pay.031') }}</strong>
              <span>{{ displayedBusinessDate }} · {{ businessDayStatusLabel }}</span>
            </div>
            <div class="business-day-actions">
              <button type="button" class="display-button" :disabled="businessDayLoading" @click="loadPaymentBusinessDay">
                {{ businessDayLoading ? gt('generated.payment-quick-pay.051') : gt('generated.payment-quick-pay.047') }}
              </button>
              <button
                v-if="showOpenTodayButton"
                type="button"
                class="display-button business-day-open-button"
                :disabled="openingBusinessDay"
                @click="openBusinessDayToday"
              >
                {{ openingBusinessDay ? gt('generated.payment-quick-pay.051') : gt('generated.payment-quick-pay.048') }}
              </button>
              <button
                v-if="showEndDayButton"
                type="button"
                class="display-button business-day-end-button"
                :disabled="endingBusinessDay"
                @click="endBusinessDay"
              >
                {{ endingBusinessDay ? gt('generated.payment-quick-pay.051') : gt('generated.payment-quick-pay.053') }}
              </button>
            </div>
          </section>
        </div>
      </section>

      <section class="daily-report-panel" :aria-label="gt('generated.payment-quick-pay.065')">
        <header class="daily-report-header">
          <div>
            <h2>{{ gt('generated.payment-quick-pay.065') }}</h2>
            <span>{{ reportContextText }}</span>
          </div>
          <button class="display-button" type="button" :disabled="reportLoading" @click="loadPaymentReport">
            {{ reportLoading ? gt('generated.payment-quick-pay.074') : gt('generated.payment-quick-pay.075') }}
          </button>
        </header>

        <div class="report-mode-tabs" role="group" :aria-label="gt('generated.payment-quick-pay.065')">
          <button
            class="report-mode-button"
            :class="{ active: reportMode === 'mine' }"
            type="button"
            :aria-pressed="reportMode === 'mine'"
            @click="setReportMode('mine')"
          >
            {{ gt('generated.payment-quick-pay.066') }}
          </button>
          <button
            class="report-mode-button"
            :class="{ active: reportMode === 'terminal' }"
            type="button"
            :aria-pressed="reportMode === 'terminal'"
            @click="setReportMode('terminal')"
          >
            {{ gt('generated.payment-quick-pay.067', { terminal: normalizedTerminalCode }) }}
          </button>
        </div>

        <p v-if="reportErrorText" class="error-banner" role="alert">{{ reportErrorText }}</p>

        <div class="report-card-grid" aria-live="polite">
          <article class="report-card report-card--paid">
            <span>{{ gt('generated.payment-quick-pay.068') }}</span>
            <strong>{{ reportMoney(reportSummary.paidAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.paidCount }) }}</em>
          </article>
          <article class="report-card report-card--pending">
            <span>{{ gt('generated.payment-quick-pay.069') }}</span>
            <strong>{{ reportMoney(reportSummary.pendingAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.pendingCount }) }}</em>
          </article>
          <article class="report-card report-card--review">
            <span>{{ gt('generated.payment-quick-pay.070') }}</span>
            <strong>{{ reportMoney(reportSummary.awaitingVerificationAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.awaitingVerificationCount }) }}</em>
          </article>
        </div>
      </section>

      <form class="calculator-panel" @submit.prevent="submitQuickPay">
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
        <p v-if="presentCapacityFull" class="notice-banner">{{ gt('generated.payment-quick-pay.055') }}</p>
        <p v-if="noticeText && noticeText !== gt('generated.payment-quick-pay.055')" class="notice-banner">{{ noticeText }}</p>

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
              <small class="recent-reference" :title="item.paymentReference">Ref {{ item.paymentReference }}</small>
            </div>
            <button
              v-if="isManualConfirmableRecent(item)"
              class="recent-confirm-button"
              type="button"
              :disabled="item.sessionNo === manuallyConfirmingSessionNo"
              @click="confirmRecentPayment(item)"
            >
              {{ gt('generated.payment-quick-pay.061') }}
            </button>
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

        <label class="settings-number-field">
          <span>{{ gt('generated.payment-quick-pay.056') }}</span>
          <input
            v-model.number="presentSettingsRecentExpiredHoldSeconds"
            inputmode="numeric"
            max="3600"
            min="0"
            step="1"
            type="number"
          />
        </label>

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
.payment-options-shell,
.recent-panel,
.preset-editor {
  background: #ffffff;
  border: 1px solid #d6e4e2;
  border-radius: 8px;
}

.payment-options-shell {
  display: grid;
  gap: 8px;
  padding: 8px;
}

.daily-report-panel {
  background: #ffffff;
  border: 1px solid #d6e4e2;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 10px;
}

.daily-report-header {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.daily-report-header > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.daily-report-header h2 {
  color: #0f172a;
  font-size: 0.95rem;
  font-weight: 950;
  letter-spacing: 0;
  margin: 0;
}

.daily-report-header span {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-mode-tabs {
  background: #f1f5f9;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 4px;
}

.report-mode-button {
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: #475569;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 950;
  min-height: 34px;
  padding: 0 8px;
}

.report-mode-button.active {
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
  color: #0f172a;
}

.report-card-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.report-card {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 5px;
  min-height: 82px;
  min-width: 0;
  padding: 10px;
}

.report-card span {
  color: #475569;
  font-size: 0.72rem;
  font-weight: 900;
  line-height: 1.2;
}

.report-card strong {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 950;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.report-card em {
  color: #64748b;
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 850;
}

.report-card--paid {
  background: #ecfdf5;
  border-color: #86efac;
}

.report-card--pending {
  background: #fffbeb;
  border-color: #fcd34d;
}

.report-card--review {
  background: #eff6ff;
  border-color: #93c5fd;
}

.payment-options-toggle {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe6ef;
  border-radius: 6px;
  color: #0f172a;
  cursor: pointer;
  display: grid;
  gap: 3px;
  grid-template-columns: minmax(0, auto) minmax(0, 1fr) auto;
  min-height: 40px;
  padding: 7px 10px;
  text-align: left;
  width: 100%;
}

.payment-options-toggle span,
.payment-options-toggle em {
  color: #64748b;
  font-size: 0.74rem;
  font-style: normal;
  font-weight: 900;
}

.payment-options-toggle strong {
  font-size: 0.82rem;
  font-weight: 950;
  min-width: 0;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payment-options-panel {
  display: grid;
  gap: 8px;
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
  align-items: center;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding: 10px;
}

.business-day-card > div:first-child {
  display: grid;
  gap: 2px;
}

.business-day-card--not-open {
  background: #fff7ed;
  border-color: #fed7aa;
}

.business-day-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.business-day-actions .display-button {
  min-width: 78px;
}

.business-day-open-button {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
}

.business-day-end-button {
  background: #b91c1c;
  border-color: #b91c1c;
  color: #ffffff;
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

.recent-reference {
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 0.68rem;
  letter-spacing: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-confirm-button {
  background: #0f766e;
  border: 0;
  border-radius: 7px;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 950;
  margin-left: auto;
  min-height: 34px;
  min-width: 62px;
  padding: 6px 10px;
  white-space: nowrap;
}

.recent-confirm-button:disabled {
  cursor: default;
  opacity: 0.58;
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
  overflow-y: auto;
  padding: 16px 16px calc(112px + env(safe-area-inset-bottom));
  position: fixed;
  z-index: 50;
}

.preset-editor {
  display: grid;
  gap: 12px;
  margin: 0 auto;
  max-height: calc(100dvh - 150px);
  max-width: 520px;
  overflow-y: auto;
  padding: 14px;
  width: 100%;
}

.editor-actions {
  background: #ffffff;
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  position: sticky;
  bottom: 0;
  padding-top: 4px;
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

.settings-number-field {
  max-width: 220px;
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
  .payment-options-toggle,
  .recent-grid {
    grid-template-columns: 1fr;
  }

  .payment-options-toggle strong {
    text-align: left;
  }

  .business-day-card {
    align-items: stretch;
    flex-direction: column;
  }

  .business-day-actions {
    justify-content: stretch;
  }

  .business-day-actions .display-button {
    flex: 1 1 0;
  }
}

@media (max-width: 460px) {
  .daily-report-header {
    align-items: stretch;
    flex-direction: column;
  }

  .daily-report-header span {
    white-space: normal;
  }

  .daily-report-header .display-button {
    width: 100%;
  }

  .report-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
