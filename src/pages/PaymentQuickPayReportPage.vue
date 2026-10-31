<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getPaymentBusinessDay, getQuickPayRecords, PaymentApiError } from '../api/paymentApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentBusinessDayStatus, QuickPayRecord, QuickPayRecordSummary } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'

type PaymentReportMode = 'mine' | 'terminal'
type ReportDetailStatus = 'paid' | 'pending' | 'awaiting_verification'

const route = useRoute()
const auth = useAuthSessionStore()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()
const { gt } = useGeneratedText()

const reportMode = ref<PaymentReportMode>('mine')
const reportLoading = ref(false)
const detailLoading = ref(false)
const reportErrorText = ref('')
const detailRecords = ref<QuickPayRecord[]>([])
const reportSummary = ref<QuickPayRecordSummary>(emptyReportSummary())
const selectedDetailStatus = ref<ReportDetailStatus>('paid')
const businessDayLoading = ref(false)
const businessDayStatus = ref<PaymentBusinessDayStatus>('not_open')
const openedBusinessDate = ref('')
let businessDayLoadSequence = 0
let reportLoadSequence = 0
let detailLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-quick-pay.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-quick-pay.002'))
const cashierName = computed(() => auth.user?.username || null)
const normalizedTerminalCode = computed(() => normalizeOptionalText(String(route.params.terminalCode || '')) || 'T1')
const displayedBusinessDate = computed(() => openedBusinessDate.value || currentBusinessDate.value)
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
const detailTitle = computed(() => detailStatusLabel(selectedDetailStatus.value))

onMounted(() => {
  loadPersistedReportMode()
  window.addEventListener('focus', refreshReportOnFocus)
  void loadPaymentBusinessDay()
  void loadPaymentReport()
  void loadReportDetails()
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', refreshReportOnFocus)
})

watch([storeId, normalizedTerminalCode], () => {
  loadPersistedReportMode()
  void loadPaymentBusinessDay()
  void loadPaymentReport()
  void loadReportDetails()
})

watch(displayedBusinessDate, () => {
  void loadPaymentReport()
  void loadReportDetails()
})

watch(cashierName, () => {
  if (reportMode.value === 'mine') {
    void loadPaymentReport()
    void loadReportDetails()
  }
})

watch(reportMode, () => {
  savePersistedReportMode()
  void loadPaymentReport()
  void loadReportDetails()
})

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
    if (sequence === businessDayLoadSequence && error instanceof PaymentApiError && (error.status === 401 || error.status === 403 || error.response.error.code === 'PERMISSION_DENIED')) {
      reportErrorText.value = reportApiErrorText(error)
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

async function loadReportDetails(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++detailLoadSequence
  if (!currentStoreId) {
    detailRecords.value = []
    return
  }
  if (reportMode.value === 'mine' && !cashierName.value) {
    detailRecords.value = []
    reportErrorText.value = ''
    return
  }

  detailLoading.value = true
  try {
    const response = await getQuickPayRecords(currentStoreId, {
      businessDate: displayedBusinessDate.value,
      status: selectedDetailStatus.value,
      terminalCode: normalizedTerminalCode.value,
      cashierName: reportMode.value === 'mine' ? cashierName.value || undefined : undefined,
      limit: 200
    })
    if (sequence !== detailLoadSequence) {
      return
    }
    detailRecords.value = response.records
  } catch (error) {
    if (sequence === detailLoadSequence) {
      detailRecords.value = []
      reportErrorText.value = reportApiErrorText(error)
    }
  } finally {
    if (sequence === detailLoadSequence) {
      detailLoading.value = false
    }
  }
}

function refreshReportOnFocus(): void {
  void loadPaymentBusinessDay()
  void loadPaymentReport()
  void loadReportDetails()
}

function setReportMode(mode: PaymentReportMode): void {
  reportMode.value = mode
}

function selectDetailStatus(status: ReportDetailStatus): void {
  selectedDetailStatus.value = status
  void loadReportDetails()
}

function detailStatusLabel(status: ReportDetailStatus): string {
  if (status === 'paid') {
    return gt('generated.payment-quick-pay.068')
  }
  if (status === 'pending') {
    return gt('generated.payment-quick-pay.069')
  }
  return gt('generated.payment-quick-pay.070')
}

function dateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return '-'
  }
  return new Intl.DateTimeFormat(undefined, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(parsed)
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

function normalizeOptionalText(value: string): string | null {
  const normalized = value.trim()
  return normalized || null
}
</script>

<template>
  <main class="staff-workbench-shell payment-report-shell">
    <StaffHomeTopBar
      :app-status-label="gt('generated.payment-quick-pay.065')"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <RouterLink class="display-button" :to="{ name: 'payment-quick-pay', params: { storeId } }">
          {{ gt('generated.payment-quick-pay.009') }}
        </RouterLink>
      </template>
    </StaffHomeTopBar>

    <section class="report-body">
      <section class="daily-report-panel" :aria-label="gt('generated.payment-quick-pay.065')">
        <header class="daily-report-header">
          <div>
            <h1>{{ gt('generated.payment-quick-pay.065') }}</h1>
            <span>{{ reportContextText }} · {{ businessDayStatusLabel }}</span>
          </div>
          <button class="display-button" type="button" :disabled="reportLoading || businessDayLoading" @click="refreshReportOnFocus">
            {{ reportLoading || businessDayLoading ? gt('generated.payment-quick-pay.074') : gt('generated.payment-quick-pay.075') }}
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
          <button
            class="report-card report-card--paid"
            :class="{ active: selectedDetailStatus === 'paid' }"
            type="button"
            :aria-pressed="selectedDetailStatus === 'paid'"
            @click="selectDetailStatus('paid')"
          >
            <span>{{ gt('generated.payment-quick-pay.068') }}</span>
            <strong>{{ reportMoney(reportSummary.paidAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.paidCount }) }}</em>
            <small>{{ gt('generated.payment-quick-pay.076') }}</small>
          </button>
          <button
            class="report-card report-card--pending"
            :class="{ active: selectedDetailStatus === 'pending' }"
            type="button"
            :aria-pressed="selectedDetailStatus === 'pending'"
            @click="selectDetailStatus('pending')"
          >
            <span>{{ gt('generated.payment-quick-pay.069') }}</span>
            <strong>{{ reportMoney(reportSummary.pendingAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.pendingCount }) }}</em>
            <small>{{ gt('generated.payment-quick-pay.076') }}</small>
          </button>
          <button
            class="report-card report-card--review"
            :class="{ active: selectedDetailStatus === 'awaiting_verification' }"
            type="button"
            :aria-pressed="selectedDetailStatus === 'awaiting_verification'"
            @click="selectDetailStatus('awaiting_verification')"
          >
            <span>{{ gt('generated.payment-quick-pay.070') }}</span>
            <strong>{{ reportMoney(reportSummary.awaitingVerificationAmount, reportSummary.currency) }}</strong>
            <em>{{ gt('generated.payment-quick-pay.071', { count: reportSummary.awaitingVerificationCount }) }}</em>
            <small>{{ gt('generated.payment-quick-pay.076') }}</small>
          </button>
        </div>

        <section class="detail-panel" :aria-label="gt('generated.payment-quick-pay.077', { status: detailTitle })">
          <header>
            <h2>{{ gt('generated.payment-quick-pay.077', { status: detailTitle }) }}</h2>
            <span>{{ gt('generated.payment-quick-pay.071', { count: detailRecords.length }) }}</span>
          </header>

          <p v-if="detailLoading" class="detail-empty">{{ gt('generated.payment-quick-pay.074') }}</p>
          <p v-else-if="detailRecords.length === 0" class="detail-empty">{{ gt('generated.payment-quick-pay.078') }}</p>

          <div v-else class="detail-list">
            <article v-for="record in detailRecords" :key="record.sessionId" class="detail-row">
              <div class="detail-row-main">
                <strong>#{{ record.displayNumber }}</strong>
                <span>{{ reportMoney(record.amount, record.currency) }}</span>
              </div>
              <dl>
                <div>
                  <dt>{{ gt('generated.payment-quick-pay.079') }}</dt>
                  <dd>{{ record.paymentReference || '-' }}</dd>
                </div>
                <div>
                  <dt>{{ gt('generated.payment-quick-pay.080') }}</dt>
                  <dd>{{ record.cashierName || '-' }}</dd>
                </div>
                <div>
                  <dt>{{ gt('generated.payment-quick-pay.081') }}</dt>
                  <dd>{{ dateTime(record.createdAt) }}</dd>
                </div>
                <div>
                  <dt>{{ gt('generated.payment-quick-pay.082') }}</dt>
                  <dd>{{ record.sessionNo }}</dd>
                </div>
              </dl>
            </article>
          </div>
        </section>
      </section>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="payment" />
  </main>
</template>

<style scoped>
.payment-report-shell {
  background: #eef6f4;
  color: #102033;
  min-height: 100dvh;
}

.display-button {
  align-items: center;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #0f172a;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 900;
  justify-content: center;
  min-height: 34px;
  padding: 0 10px;
  text-decoration: none;
}

.report-body {
  display: grid;
  gap: 10px;
  margin: 0 auto;
  max-width: 720px;
  padding: 12px 10px calc(92px + env(safe-area-inset-bottom));
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

.daily-report-header h1 {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
  letter-spacing: 0;
  margin: 0;
}

.daily-report-header span {
  color: #64748b;
  font-size: 0.74rem;
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
  min-height: 40px;
  padding: 0 8px;
}

.report-mode-button.active {
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
  color: #0f172a;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  margin: 0;
  padding: 10px 12px;
}

.report-card-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.report-card {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  gap: 6px;
  font: inherit;
  min-height: 92px;
  min-width: 0;
  padding: 12px;
  text-align: left;
}

.report-card span {
  color: #475569;
  font-size: 0.75rem;
  font-weight: 900;
  line-height: 1.2;
}

.report-card strong {
  color: #0f172a;
  font-size: 1.35rem;
  font-weight: 950;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.report-card em {
  color: #64748b;
  font-size: 0.76rem;
  font-style: normal;
  font-weight: 850;
}

.report-card small {
  color: #0f766e;
  font-size: 0.68rem;
  font-weight: 900;
}

.report-card.active {
  box-shadow: 0 0 0 2px rgba(15, 118, 110, 0.28);
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

.detail-panel {
  border-top: 1px solid #e2e8f0;
  display: grid;
  gap: 10px;
  padding-top: 10px;
}

.detail-panel header {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.detail-panel h2 {
  color: #0f172a;
  font-size: 0.9rem;
  font-weight: 950;
  margin: 0;
}

.detail-panel header span {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 850;
}

.detail-empty {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  color: #64748b;
  font-size: 0.8rem;
  font-weight: 850;
  margin: 0;
  padding: 10px 12px;
}

.detail-list {
  display: grid;
  gap: 8px;
}

.detail-row {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  padding: 10px;
}

.detail-row-main {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.detail-row-main strong {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
}

.detail-row-main span {
  color: #0f766e;
  font-size: 0.95rem;
  font-weight: 950;
}

.detail-row dl {
  display: grid;
  gap: 6px;
  margin: 0;
}

.detail-row dl div {
  align-items: start;
  display: grid;
  gap: 8px;
  grid-template-columns: 72px minmax(0, 1fr);
}

.detail-row dt,
.detail-row dd {
  font-size: 0.72rem;
  font-weight: 850;
  margin: 0;
}

.detail-row dt {
  color: #64748b;
}

.detail-row dd {
  color: #0f172a;
  min-width: 0;
  overflow-wrap: anywhere;
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
