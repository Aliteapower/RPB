<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createPaymentIntent,
  PaymentApiError
} from '../api/paymentApi'
import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentIntentCreateResponse } from '../types/payment'
import { extractPayNowQrPayload } from '../utils/paymentQrPayloads'

const amountPresets = ['5.00', '10.00', '18.80', '20.00', '50.00', '100.00']

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()
const { gt } = useGeneratedText()

const amountText = ref('')
const noteText = ref('')
const terminalCode = ref('')
const creating = ref(false)
const errorText = ref('')
const createdResult = ref<PaymentIntentCreateResponse | null>(null)

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-quick-pay.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-quick-pay.002'))
const cashierName = computed(() => auth.user?.username || null)
const numericAmount = computed(() => Number(amountText.value))
const canCreate = computed(() => Number.isFinite(numericAmount.value) && numericAmount.value > 0 && !creating.value)
const qrPayload = computed(() => extractPayNowQrPayload(createdResult.value?.session.qrPayloadsJson))
const displayRoute = computed(() => {
  const sessionNo = createdResult.value?.session.sessionNo
  return sessionNo
    ? { name: 'payment-display', params: { storeId: storeId.value, sessionNo } }
    : null
})

function applyPreset(value: string): void {
  amountText.value = value
}

async function submitQuickPay(): Promise<void> {
  if (!canCreate.value || !storeId.value) {
    return
  }

  creating.value = true
  errorText.value = ''
  createdResult.value = null
  try {
    createdResult.value = await createPaymentIntent(storeId.value, {
      idempotencyKey: createIdempotencyKey(),
      sourceType: 'quick_pay',
      sourceId: null,
      method: 'paynow',
      amount: numericAmount.value.toFixed(2),
      currency: 'SGD',
      terminalCode: normalizeOptionalText(terminalCode.value),
      cashierName: cashierName.value,
      requestedDisplayNumber: null,
      metadataJson: JSON.stringify({
        note: normalizeOptionalText(noteText.value),
        channel: 'staff_quick_pay'
      })
    })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    creating.value = false
  }
}

async function openDisplayPage(): Promise<void> {
  if (displayRoute.value) {
    await router.push(displayRoute.value)
  }
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
    />

    <section class="payment-body">
      <form class="calculator-panel" @submit.prevent="submitQuickPay">
        <header>
          <div>
            <span>{{ gt('generated.payment-quick-pay.010') }}</span>
            <h1>{{ gt('generated.payment-quick-pay.011') }}</h1>
          </div>
          <strong>SGD</strong>
        </header>

        <label class="amount-field">
          <span>{{ gt('generated.payment-quick-pay.012') }}</span>
          <input
            v-model.trim="amountText"
            inputmode="decimal"
            min="0.01"
            placeholder="0.00"
            step="0.01"
            type="number"
          />
        </label>

        <div class="preset-grid" :aria-label="gt('generated.payment-quick-pay.013')">
          <button
            v-for="preset in amountPresets"
            :key="preset"
            type="button"
            @click="applyPreset(preset)"
          >
            {{ preset }}
          </button>
        </div>

        <div class="detail-grid">
          <label>
            <span>{{ gt('generated.payment-quick-pay.014') }}</span>
            <input v-model.trim="terminalCode" maxlength="32" />
          </label>
          <label>
            <span>{{ gt('generated.payment-quick-pay.015') }}</span>
            <input v-model.trim="noteText" maxlength="120" />
          </label>
        </div>

        <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>

        <button class="primary-button" type="submit" :disabled="!canCreate">
          {{ creating ? gt('generated.payment-quick-pay.016') : gt('generated.payment-quick-pay.017') }}
        </button>
      </form>

      <section v-if="createdResult" class="result-panel" :aria-label="gt('generated.payment-quick-pay.018')">
        <header>
          <div>
            <span>{{ gt('generated.payment-quick-pay.018') }}</span>
            <h2>{{ createdResult.intent.intentNo }}</h2>
          </div>
          <strong>#{{ createdResult.session.displayNumber }}</strong>
        </header>

        <div class="summary-grid">
          <div>
            <span>{{ gt('generated.payment-quick-pay.019') }}</span>
            <strong>{{ createdResult.intent.amount }} {{ createdResult.intent.currency }}</strong>
          </div>
          <div>
            <span>{{ gt('generated.payment-quick-pay.020') }}</span>
            <strong>{{ createdResult.session.sessionNo }}</strong>
          </div>
          <div>
            <span>{{ gt('generated.payment-quick-pay.021') }}</span>
            <strong>{{ createdResult.session.expiresAt }}</strong>
          </div>
        </div>

        <DownloadableQrCode
          :description="gt('generated.payment-quick-pay.022')"
          :download-label="gt('generated.payment-quick-pay.023')"
          :file-name="`${createdResult.session.sessionNo}.png`"
          :size="260"
          :title="gt('generated.payment-quick-pay.024')"
          :value="qrPayload"
        />

        <button class="secondary-button" type="button" :disabled="!displayRoute" @click="openDisplayPage">
          {{ gt('generated.payment-quick-pay.025') }}
        </button>
      </section>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="payment" />
  </main>
</template>

<style scoped>
.payment-shell {
  background: #eef6f4;
  color: #102033;
  min-height: 100dvh;
}

.payment-body {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr);
  padding: 12px 14px calc(92px + env(safe-area-inset-bottom));
}

.calculator-panel,
.result-panel {
  background: #ffffff;
  border: 1px solid #d6e4e2;
  border-radius: 8px;
  display: grid;
  gap: 14px;
  padding: 14px;
}

.calculator-panel header,
.result-panel header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.calculator-panel header span,
.result-panel header span,
.summary-grid span,
label span {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 850;
}

h1,
h2 {
  color: #0f172a;
  font-weight: 950;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.35rem;
}

h2 {
  font-size: 1rem;
}

.calculator-panel header strong,
.result-panel header strong {
  background: #ecfdf5;
  border: 1px solid #86efac;
  border-radius: 999px;
  color: #047857;
  font-size: 0.82rem;
  padding: 5px 9px;
}

label {
  display: grid;
  gap: 7px;
}

input {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 42px;
  padding: 9px 10px;
  width: 100%;
}

.amount-field input {
  font-size: 2rem;
  font-weight: 950;
  height: 70px;
}

.preset-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.preset-grid button,
.secondary-button {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  cursor: pointer;
  font: inherit;
  font-weight: 900;
  min-height: 42px;
}

.detail-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  margin: 0;
  padding: 10px 12px;
}

.primary-button {
  background: #0f766e;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-weight: 950;
  min-height: 48px;
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: default;
  opacity: 0.58;
}

.summary-grid {
  display: grid;
  gap: 8px;
}

.summary-grid div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 9px 10px;
}

.summary-grid strong {
  color: #0f172a;
  font-size: 0.86rem;
  overflow-wrap: anywhere;
}

@media (min-width: 760px) {
  .payment-body {
    grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
    margin: 0 auto;
    max-width: 980px;
  }
}

@media (max-width: 420px) {
  .preset-grid,
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
