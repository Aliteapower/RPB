<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  getPaymentSession,
  PaymentApiError
} from '../api/paymentApi'
import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentSession } from '../types/payment'
import { extractPayNowQrPayload } from '../utils/paymentQrPayloads'

const route = useRoute()
const auth = useAuthSessionStore()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()
const { gt } = useGeneratedText()

const session = ref<PaymentSession | null>(null)
const loading = ref(false)
const errorText = ref('')
let loadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const sessionNo = computed(() => String(route.params.sessionNo || ''))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-display.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-display.002'))
const qrPayload = computed(() => extractPayNowQrPayload(session.value?.qrPayloadsJson))

watch(
  [storeId, sessionNo],
  () => {
    void loadSession()
  },
  { immediate: true }
)

async function loadSession(): Promise<void> {
  const sequence = ++loadSequence
  session.value = null
  errorText.value = ''

  if (!storeId.value || !sessionNo.value) {
    errorText.value = gt('generated.payment-display.003')
    return
  }

  loading.value = true
  try {
    const response = await getPaymentSession(storeId.value, sessionNo.value)
    if (sequence === loadSequence) {
      session.value = response
    }
  } catch (error) {
    if (sequence === loadSequence) {
      errorText.value = apiErrorText(error)
    }
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.payment-display.004')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.payment-display.005')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.payment-display.006')
  }
  if (error.response.error.code === 'PAYMENT_SESSION_NOT_FOUND') {
    return gt('generated.payment-display.007')
  }
  return gt('generated.payment-display.004')
}
</script>

<template>
  <main class="staff-workbench-shell display-shell">
    <StaffHomeTopBar
      :app-status-label="gt('generated.payment-display.008')"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <button class="refresh-button" type="button" :disabled="loading" @click="loadSession">
          {{ loading ? gt('generated.payment-display.009') : gt('generated.payment-display.010') }}
        </button>
      </template>
    </StaffHomeTopBar>

    <section class="display-body">
      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-else-if="loading" class="loading-line">{{ gt('generated.payment-display.011') }}</p>

      <section v-if="session" class="display-panel" :aria-label="gt('generated.payment-display.012')">
        <header>
          <div>
            <span>{{ gt('generated.payment-display.013') }}</span>
            <h1>#{{ session.displayNumber }}</h1>
          </div>
          <strong>{{ session.status }}</strong>
        </header>

        <DownloadableQrCode
          :description="gt('generated.payment-display.014')"
          :download-label="gt('generated.payment-display.015')"
          :file-name="`${session.sessionNo}.png`"
          :size="320"
          :title="gt('generated.payment-display.016')"
          :value="qrPayload"
        />

        <dl class="session-meta">
          <div>
            <dt>{{ gt('generated.payment-display.017') }}</dt>
            <dd>{{ session.sessionNo }}</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-display.018') }}</dt>
            <dd>{{ session.businessDate }}</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-display.019') }}</dt>
            <dd>{{ session.expiresAt }}</dd>
          </div>
        </dl>

        <RouterLink class="back-link" :to="{ name: 'payment-quick-pay', params: { storeId } }">
          {{ gt('generated.payment-display.020') }}
        </RouterLink>
      </section>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="payment" />
  </main>
</template>

<style scoped>
.display-shell {
  background: #eef6f4;
  color: #102033;
  min-height: 100dvh;
}

.refresh-button,
.back-link {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 900;
  justify-content: center;
  min-height: 34px;
  padding: 0 12px;
}

.refresh-button {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #c2410c;
  cursor: pointer;
}

.refresh-button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.display-body {
  display: grid;
  gap: 14px;
  margin: 0 auto;
  max-width: 620px;
  padding: 12px 14px calc(92px + env(safe-area-inset-bottom));
}

.display-panel {
  background: #ffffff;
  border: 1px solid #d6e4e2;
  border-radius: 8px;
  display: grid;
  gap: 14px;
  padding: 14px;
}

.display-panel header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.display-panel header span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
}

.display-panel h1 {
  color: #0f172a;
  font-size: 2rem;
  font-weight: 950;
  letter-spacing: 0;
  margin: 0;
}

.display-panel header strong {
  background: #ecfdf5;
  border: 1px solid #86efac;
  border-radius: 999px;
  color: #047857;
  font-size: 0.82rem;
  padding: 5px 9px;
}

.session-meta {
  display: grid;
  gap: 8px;
  margin: 0;
}

.session-meta div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  display: grid;
  gap: 4px;
  padding: 9px 10px;
}

.session-meta dt {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 850;
}

.session-meta dd {
  color: #0f172a;
  font-size: 0.9rem;
  font-weight: 900;
  margin: 0;
  overflow-wrap: anywhere;
}

.back-link {
  background: #0f766e;
  color: #ffffff;
  min-height: 42px;
  text-decoration: none;
}

.error-banner,
.loading-line {
  border-radius: 6px;
  margin: 0;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.loading-line {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  color: #475569;
}
</style>
