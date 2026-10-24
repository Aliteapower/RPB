<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getQuickPayRecords, PaymentApiError } from '../api/paymentApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import type { QuickPayRecord, QuickPayRecordSummary } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'

const route = useRoute()
const auth = useAuthSessionStore()
const { gt } = useGeneratedText()

const loading = ref(false)
const errorText = ref('')
const records = ref<QuickPayRecord[]>([])
const summary = ref<QuickPayRecordSummary>({
  count: 0,
  pendingCount: 0,
  awaitingVerificationCount: 0,
  paidCount: 0,
  totalAmount: '0',
  pendingAmount: '0',
  awaitingVerificationAmount: '0',
  paidAmount: '0',
  currency: 'SGD'
})

const filters = reactive({
  businessDate: todayText(),
  status: '',
  terminalCode: '',
  q: '',
  limit: 80
})

const storeId = computed(() => String(route.params.storeId || ''))

const statusOptions = [
  { value: '', labelKey: 'generated.tenant-admin-payment-records.011' },
  { value: 'pending', labelKey: 'generated.tenant-admin-payment-records.012' },
  { value: 'awaiting_verification', labelKey: 'generated.tenant-admin-payment-records.013' },
  { value: 'paid', labelKey: 'generated.tenant-admin-payment-records.014' },
  { value: 'expired', labelKey: 'generated.tenant-admin-payment-records.015' },
  { value: 'cancelled', labelKey: 'generated.tenant-admin-payment-records.016' },
  { value: 'failed', labelKey: 'generated.tenant-admin-payment-records.017' }
]

onMounted(() => {
  void loadRecords()
})

async function loadRecords(): Promise<void> {
  if (!storeId.value || loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const response = await getQuickPayRecords(storeId.value, {
      businessDate: filters.businessDate,
      status: filters.status,
      terminalCode: filters.terminalCode,
      q: filters.q,
      limit: filters.limit
    })
    records.value = response.records
    summary.value = response.summary
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function resetFilters(): void {
  filters.businessDate = todayText()
  filters.status = ''
  filters.terminalCode = ''
  filters.q = ''
  filters.limit = 80
  void loadRecords()
}

function statusLabel(status: string): string {
  const option = statusOptions.find(item => item.value === status)
  return option ? gt(option.labelKey) : (status || '-')
}

function statusClass(status: string): string {
  if (status === 'paid') {
    return 'status-pill--good'
  }
  if (status === 'awaiting_verification' || status === 'pending') {
    return 'status-pill--warn'
  }
  if (status === 'expired' || status === 'cancelled' || status === 'failed') {
    return 'status-pill--bad'
  }
  return ''
}

function money(amount: string | number | null | undefined, currency = 'SGD'): string {
  const value = Number(amount ?? 0)
  const safeValue = Number.isFinite(value) ? value : 0
  return `${currency || 'SGD'} ${safeValue.toFixed(2)}`
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
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(parsed)
}

function todayText(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.tenant-admin-payment-records.018')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-payment-records.019')
  }
  if (isAppGateError(error.response.error.code, error.response.error.messageKey)) {
    return formatAppGateErrorMessage(error.response.error, gt('generated.tenant-admin-payment-records.018'))
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-payment-records.020')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-payment-records.021')
  }
  return gt('generated.tenant-admin-payment-records.018')
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
          <span>{{ gt('generated.tenant-admin-payment-records.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-payment-records.002') }}</h1>
        </div>
        <RouterLink class="secondary-link" :to="{ name: 'tenant-admin-payment-settings', params: { storeId } }">
          {{ gt('generated.tenant-admin-payment-records.003') }}
        </RouterLink>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>

      <section class="summary-grid" :aria-label="gt('generated.tenant-admin-payment-records.004')">
        <article>
          <span>{{ gt('generated.tenant-admin-payment-records.005') }}</span>
          <strong>{{ summary.count }}</strong>
        </article>
        <article>
          <span>{{ gt('generated.tenant-admin-payment-records.006') }}</span>
          <strong>{{ summary.pendingCount }}</strong>
        </article>
        <article>
          <span>{{ gt('generated.tenant-admin-payment-records.007') }}</span>
          <strong>{{ summary.paidCount }}</strong>
        </article>
        <article>
          <span>{{ gt('generated.tenant-admin-payment-records.008') }}</span>
          <strong>{{ money(summary.totalAmount, summary.currency) }}</strong>
        </article>
      </section>

      <form class="filter-panel" role="search" @submit.prevent="loadRecords">
        <label>
          <span>{{ gt('generated.tenant-admin-payment-records.009') }}</span>
          <input v-model="filters.businessDate" type="date" />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-payment-records.010') }}</span>
          <select v-model="filters.status">
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">
              {{ gt(item.labelKey) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-payment-records.022') }}</span>
          <input v-model.trim="filters.terminalCode" maxlength="40" placeholder="T1" />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-payment-records.023') }}</span>
          <input v-model.trim="filters.q" maxlength="80" />
        </label>
        <div class="filter-actions">
          <button class="secondary-button" type="button" @click="resetFilters">
            {{ gt('generated.tenant-admin-payment-records.024') }}
          </button>
          <button class="primary-button" type="submit" :disabled="loading">
            {{ loading ? gt('generated.tenant-admin-payment-records.025') : gt('generated.tenant-admin-payment-records.026') }}
          </button>
        </div>
      </form>

      <section class="records-section" :aria-label="gt('generated.tenant-admin-payment-records.002')">
        <header>
          <h2>{{ gt('generated.tenant-admin-payment-records.027') }}</h2>
          <span>{{ records.length }} {{ gt('generated.tenant-admin-payment-records.028') }}</span>
        </header>

        <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-payment-records.029') }}</p>
        <p v-else-if="records.length === 0" class="empty-line">{{ gt('generated.tenant-admin-payment-records.030') }}</p>

        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ gt('generated.tenant-admin-payment-records.031') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.032') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.033') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.034') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.035') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.036') }}</th>
                <th>{{ gt('generated.tenant-admin-payment-records.037') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in records" :key="record.sessionId">
                <td>
                  <strong>#{{ record.displayNumber }}</strong>
                  <small>{{ record.businessDate }}</small>
                </td>
                <td>
                  <strong>{{ record.paymentReference }}</strong>
                  <small>{{ record.intentNo }}</small>
                </td>
                <td>{{ money(record.amount, record.currency) }}</td>
                <td>
                  <strong>{{ record.terminalCode || '-' }}</strong>
                  <small>{{ record.cashierName || '-' }}</small>
                </td>
                <td><span class="status-pill" :class="statusClass(record.intentStatus)">{{ statusLabel(record.intentStatus) }}</span></td>
                <td>{{ dateTime(record.createdAt) }}</td>
                <td>
                  <strong>{{ dateTime(record.expiresAt) }}</strong>
                  <small>{{ record.sessionNo }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
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

.page-heading span,
.summary-grid span,
.records-section header span,
small,
label span {
  color: #64748b;
  font-size: 13px;
  font-weight: 750;
}

.page-heading h1,
.records-section h2 {
  color: #0f172a;
  margin: 0;
}

.page-heading h1 {
  font-size: 24px;
}

.secondary-link,
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

.secondary-link,
.secondary-button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  text-decoration: none;
}

.primary-button {
  background: #0f766e;
  border: 0;
  color: #ffffff;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: default;
  opacity: 0.6;
}

.error-banner,
.loading-line,
.empty-line {
  border-radius: 6px;
  margin: 0 0 12px;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.loading-line,
.empty-line {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  color: #475569;
}

.summary-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
}

.summary-grid article {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  min-height: 78px;
  padding: 14px;
}

.summary-grid strong {
  color: #0f172a;
  font-size: 22px;
  font-weight: 950;
}

.filter-panel {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  grid-template-columns: 170px 210px 170px minmax(220px, 1fr) auto;
  margin-bottom: 14px;
  padding: 14px;
}

label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

input,
select {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 38px;
  padding: 8px 10px;
  width: 100%;
}

.filter-actions {
  align-items: end;
  display: flex;
  gap: 8px;
}

.records-section {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 14px;
}

.records-section header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.records-section h2 {
  font-size: 17px;
}

.table-wrap {
  overflow-x: auto;
  width: 100%;
}

table {
  border-collapse: collapse;
  min-width: 980px;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #e6edf3;
  padding: 11px 12px;
  text-align: left;
  vertical-align: top;
}

th {
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 850;
}

td {
  color: #0f172a;
  font-size: 14px;
}

td strong,
td small {
  display: block;
}

td small {
  margin-top: 4px;
  overflow-wrap: anywhere;
}

.status-pill {
  align-items: center;
  background: #eef2f6;
  border-radius: 999px;
  color: #344054;
  display: inline-flex;
  font-size: 12px;
  font-weight: 850;
  min-height: 24px;
  padding: 0 9px;
}

.status-pill--good {
  background: #dcfce7;
  color: #166534;
}

.status-pill--warn {
  background: #fef3c7;
  color: #92400e;
}

.status-pill--bad {
  background: #fee2e2;
  color: #991b1b;
}

@media (max-width: 1180px) {
  .filter-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 980px) {
  .tenant-shell,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .page-heading,
  .records-section header {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-panel {
    grid-template-columns: 1fr;
  }

  .filter-actions,
  .secondary-link,
  .primary-button,
  .secondary-button {
    justify-content: center;
    width: 100%;
  }
}
</style>
