<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useStoreContextStore } from '../stores/storeContext'
import {
  clearPaymentPresentPayload,
  isPaymentPresentPayloadActive,
  MAX_PRESENT_PAYMENTS,
  paymentPresentSecondsRemaining,
  PAYMENT_PRESENT_TTL_SECONDS,
  readPaymentPresentPayloads,
  readPaymentPresentRecent,
  readPaymentPresentSettings,
  subscribePaymentPresentPayloads,
  subscribePaymentPresentSettings,
  type PaymentPresentPayload,
  type PaymentPresentRecentItem,
  type PaymentPresentSettings
} from '../utils/paymentPresentBridge'

const route = useRoute()
const storeContext = useStoreContextStore()
const { gt } = useGeneratedText()

const activePayloads = ref<PaymentPresentPayload[]>([])
const presentSettings = ref<PaymentPresentSettings>({
  maxPayments: 1,
  qrPerPayment: 1,
  primaryQr: 'sgqr',
  recentExpiredHoldSeconds: 20
})
const recentItems = ref<PaymentPresentRecentItem[]>([])
const nowMs = ref(Date.now())
let unsubscribePayloads: (() => void) | null = null
let unsubscribeSettings: (() => void) | null = null
let timer: number | null = null

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const terminalCode = computed(() => String(route.params.terminalCode || 'T1').trim() || 'T1')
const storeLabel = computed(() => storeId.value ? gt('generated.payment-present.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-present.002'))
const waitingForPaymentText = computed(() => gt('generated.payment-present.014') || 'Waiting for new payment')
const activePayloadGridClass = computed(() => `cards-${Math.min(activePayloads.value.length || presentSettings.value.maxPayments, MAX_PRESENT_PAYMENTS)}`)

onMounted(() => {
  refreshRecent()
  presentSettings.value = storeId.value ? readPaymentPresentSettings(storeId.value, terminalCode.value) : presentSettings.value
  const saved = storeId.value ? readPaymentPresentPayloads(storeId.value, terminalCode.value) : []
  applyIncomingPayloads(saved)
  if (storeId.value) {
    unsubscribePayloads = subscribePaymentPresentPayloads(storeId.value, terminalCode.value, applyIncomingPayloads)
    unsubscribeSettings = subscribePaymentPresentSettings(storeId.value, terminalCode.value, applyIncomingSettings)
  }
  timer = window.setInterval(() => {
    nowMs.value = Date.now()
    filterExpiredActivePayloads()
    refreshRecent()
  }, 1000)
})

onBeforeUnmount(() => {
  unsubscribePayloads?.()
  unsubscribePayloads = null
  unsubscribeSettings?.()
  unsubscribeSettings = null
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
})

function applyIncomingPayloads(payloads: PaymentPresentPayload[]): void {
  const next = payloads
    .filter(payload => isPaymentPresentPayloadActive(payload, nowMs.value))
    .slice(0, presentSettings.value.maxPayments)
  if (!next.length) {
    if (payloads.length) {
      clearPaymentPresentPayload(payloads[0].storeId, payloads[0].terminalCode)
    }
    activePayloads.value = []
    refreshRecent()
    return
  }
  activePayloads.value = next
  refreshRecent()
}

function clearActivePayload(): void {
  if (storeId.value) {
    clearPaymentPresentPayload(storeId.value, terminalCode.value)
  }
  activePayloads.value = []
  refreshRecent()
}

function applyIncomingSettings(settings: PaymentPresentSettings): void {
  presentSettings.value = settings
  activePayloads.value = activePayloads.value.slice(0, settings.maxPayments)
  refreshRecent()
}

function filterExpiredActivePayloads(): void {
  const next = activePayloads.value.filter(payload => isPaymentPresentPayloadActive(payload, nowMs.value))
  if (next.length === activePayloads.value.length) {
    return
  }
  if (!next.length) {
    clearActivePayload()
    return
  }
  activePayloads.value = next
}

function refreshRecent(): void {
  recentItems.value = storeId.value ? readPaymentPresentRecent(storeId.value, terminalCode.value) : []
}

function formatCountdown(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  const minutes = String(Math.floor(safe / 60)).padStart(2, '0')
  const rest = String(safe % 60).padStart(2, '0')
  return `${minutes}:${rest}`
}

function activeCountdownText(payload: PaymentPresentPayload): string {
  return formatCountdown(paymentPresentSecondsRemaining(payload, nowMs.value))
}

function recentSecondsText(item: PaymentPresentRecentItem): string {
  const remaining = Math.max(0, Math.ceil((item.createdAtMs + PAYMENT_PRESENT_TTL_SECONDS * 1000 - nowMs.value) / 1000))
  return remaining > 0 ? `${remaining}s` : gt('generated.payment-present.015')
}

function isPendingRecent(item: PaymentPresentRecentItem): boolean {
  return item.status === 'pending' || item.status === 'awaiting_verification'
}
</script>

<template>
  <main class="present-shell">
    <header class="present-topbar">
      <div>
        <span class="brand-mark">RPB</span>
        <strong>{{ gt('generated.payment-present.003') }}</strong>
        <small>{{ gt('generated.payment-present.004') }}</small>
      </div>
      <div class="terminal-meta">
        <span>{{ storeLabel }}</span>
        <strong>{{ terminalCode }}</strong>
      </div>
    </header>

    <section class="present-body">
      <section v-if="activePayloads.length" class="present-grid" :class="activePayloadGridClass">
        <article v-for="payload in activePayloads" :key="payload.sessionNo" class="present-card">
          <div class="present-index">{{ payload.displayNumber }}</div>
          <header class="payment-head">
            <div class="present-ref">
              <span>{{ gt('generated.payment-present.005') }}</span>
              <strong>{{ payload.paymentReference }}</strong>
            </div>
            <div class="present-amount">
              <span>{{ payload.currency }}</span>
              <strong>{{ payload.amount }}</strong>
            </div>
          </header>

          <dl class="payment-meta">
            <div>
              <dt>{{ gt('generated.payment-present.006') }}</dt>
              <dd>{{ payload.status }}</dd>
            </div>
            <div>
              <dt>{{ gt('generated.payment-present.007') }}</dt>
              <dd>{{ activeCountdownText(payload) }}</dd>
            </div>
          </dl>

          <DownloadableQrCode
            :description="gt('generated.payment-present.008')"
            :show-download="false"
            :size="320"
            :title="gt('generated.payment-present.010')"
            :value="payload.qrPayload"
          />
        </article>
      </section>

      <section v-else class="waiting-panel">
        <strong>{{ waitingForPaymentText }}</strong>
      </section>

      <section class="recent-panel">
        <h2>{{ gt('generated.payment-present.011') }}</h2>
        <div v-if="recentItems.length" class="recent-grid">
          <article v-for="item in recentItems" :key="item.sessionNo" class="recent-card">
            <strong>{{ item.displayNumber }}</strong>
            <div>
              <span>{{ item.currency }} {{ item.amount }}</span>
              <small>{{ item.status }}</small>
              <small v-if="isPendingRecent(item)">{{ recentSecondsText(item) }}</small>
            </div>
          </article>
        </div>
        <p v-else class="recent-empty">-</p>
      </section>
    </section>
  </main>
</template>

<style scoped>
.present-shell {
  background: #f6f7fb;
  color: #0f172a;
  min-height: 100dvh;
}

.present-topbar {
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-height: 64px;
  padding: 10px 18px;
}

.present-topbar > div {
  display: grid;
  gap: 2px;
}

.brand-mark,
.present-topbar small,
.terminal-meta span,
.present-ref span,
.present-amount span,
.payment-meta dt,
.recent-card small {
  color: #64748b;
  font-size: 0.75rem;
  font-weight: 850;
}

.present-topbar strong {
  font-size: 1rem;
  font-weight: 950;
}

.terminal-meta {
  justify-items: end;
}

.terminal-meta strong {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  color: #1d4ed8;
  padding: 5px 10px;
}

.present-body {
  display: grid;
  gap: 14px;
  padding: 12px;
}

.present-card,
.waiting-panel,
.recent-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.present-grid {
  display: grid;
  gap: 12px;
}

.present-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  position: relative;
}

.present-index {
  align-items: center;
  background: #111827;
  border-radius: 999px;
  color: #ffffff;
  display: flex;
  font-size: 1.15rem;
  font-weight: 950;
  height: 36px;
  justify-content: center;
  left: 14px;
  position: absolute;
  top: 14px;
  width: 36px;
}

.payment-head {
  align-items: start;
  display: flex;
  gap: 14px;
  justify-content: space-between;
  padding-left: 48px;
}

.present-ref,
.present-amount {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.present-ref strong {
  overflow-wrap: anywhere;
}

.present-amount {
  flex-shrink: 0;
  justify-items: end;
}

.present-amount strong {
  font-size: clamp(1.25rem, 4vw, 2.2rem);
  font-weight: 950;
}

.payment-meta {
  display: grid;
  gap: 4px;
  margin: 0;
}

.payment-meta div {
  display: flex;
  gap: 6px;
}

.payment-meta dd {
  font-weight: 900;
  margin: 0;
}

.waiting-panel {
  padding: 16px;
}

.waiting-panel strong {
  font-size: 0.95rem;
  font-weight: 950;
}

.recent-panel {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.recent-panel h2 {
  font-size: 0.9rem;
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
  min-height: 56px;
  min-width: 0;
  padding: 9px 10px;
}

.recent-card > strong {
  flex: 0 0 42px;
  font-size: 1.35rem;
  font-weight: 950;
  text-align: center;
}

.recent-card div {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.recent-card span {
  font-size: 0.82rem;
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

@media (min-width: 900px) {
  .present-body {
    grid-template-columns: minmax(0, 1fr);
    max-width: 1440px;
  }

  .present-grid {
    grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  }

  .present-grid.cards-1 {
    grid-template-columns: minmax(0, 1fr);
  }

  .recent-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .present-grid {
    grid-template-columns: 1fr;
  }

  .payment-head {
    align-items: stretch;
    flex-direction: column;
  }

  .present-amount {
    justify-items: start;
  }

  .recent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
