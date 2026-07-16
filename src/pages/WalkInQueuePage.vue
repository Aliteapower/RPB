<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { queueWalkIn, WalkInQueueApiError } from '../api/walkInQueueApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffGuestContactLookup from '../components/staff/StaffGuestContactLookup.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../components/staff/staffGuestContact'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import StaffHomeWorkflowStrip from '../components/staff-home/StaffHomeWorkflowStrip.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  QueueWalkInRequest,
  WalkInQueueApiErrorResponse
} from '../types/walkInQueue'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const router = useRouter()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const form = reactive({
  partySize: 2,
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  note: ''
})

const isSubmitting = ref(false)
const apiError = ref<WalkInQueueApiErrorResponse | null>(null)

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isSubmitting.value ? gt('generated.walk-in-queue.016') : gt('generated.walk-in-queue.017')))
const queueTicketListRoute = computed(() => ({
  name: 'queue-ticket-list',
  params: {
    storeId: storeId.value || ''
  }
}))
const canSubmit = computed(
  () => !isSubmitting.value && !!storeId.value && Number.isInteger(form.partySize) && form.partySize > 0
)

async function submitWalkInQueue(): Promise<void> {
  apiError.value = validateForm()

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  isSubmitting.value = true

  try {
    await queueWalkIn(storeId.value, toRequest(), idempotencyKey)
    await router.push(queueTicketListRoute.value)
  } catch (error) {
    apiError.value =
      error instanceof WalkInQueueApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'walkin.queue.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): WalkInQueueApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'walkin.queue.store_scope_mismatch')
  }

  if (!Number.isInteger(form.partySize) || form.partySize <= 0) {
    return createLocalError('INVALID_PARTY_SIZE', 'walkin.queue.invalid_party_size')
  }

  if (form.phoneLocal.trim() && !isValidSingaporeLocalPhone(form.phoneLocal)) {
    return createLocalError('INVALID_CUSTOMER_IDENTITY', 'walkin.queue.invalid_customer_identity')
  }

  return null
}

function toRequest(): QueueWalkInRequest {
  return {
    partySize: form.partySize,
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerSalutation),
    phoneE164: toSingaporePhoneE164(form.phoneLocal),
    note: optionalValue(form.note)
  }
}

function updatePartySize(delta: number): void {
  form.partySize = Math.max(1, Math.min(99, form.partySize + delta))
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(): string {
  const prefix = 'walkin:queue'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): WalkInQueueApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return gt('generated.walk-in-queue.018')
  }

  return `${gt('generated.walk-in-queue.015')}${value.slice(0, 8)}`
}
</script>

<template>
  <main class="staff-workbench-shell walk-in-queue-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="walk-in-queue-body">
      <StaffHomeWorkflowStrip />

      <section class="quick-ticket-panel">
        <div>
          <p>{{ gt('generated.walk-in-queue.001') }}</p>
          <h1>{{ gt('generated.walk-in-queue.002') }}</h1>
        </div>
        <strong>{{ gt('generated.walk-in-queue.003') }}</strong>
      </section>

      <form class="walk-in-queue-form" @submit.prevent="submitWalkInQueue">
        <section class="party-size-panel" :aria-label="gt('generated.walk-in-queue.004')">
          <div>
            <span>{{ gt('generated.walk-in-queue.005') }}</span>
            <strong>{{ form.partySize }}{{ gt('generated.walk-in-queue.006') }}</strong>
          </div>
          <div class="party-size-stepper">
            <button type="button" :disabled="form.partySize <= 1" @click="updatePartySize(-1)">-</button>
            <input v-model.number="form.partySize" inputmode="numeric" min="1" name="partySize" type="number" />
            <button type="button" @click="updatePartySize(1)">+</button>
          </div>
        </section>

        <details class="optional-customer-panel">
          <summary>
            <span>{{ gt('generated.walk-in-queue.007') }}</span>
            <strong>{{ gt('generated.walk-in-queue.008') }}</strong>
          </summary>

          <div class="optional-customer-fields">
            <StaffGuestContactLookup
              :store-id="storeId || ''"
              v-model:customer-id="form.customerId"
              v-model:customer-name="form.customerName"
              v-model:salutation="form.customerSalutation"
              v-model:phone-local="form.phoneLocal"
              :disabled="isSubmitting"
            />

            <label class="note-field">
              <span>{{ gt('generated.walk-in-queue.009') }}</span>
              <textarea v-model="form.note" name="note" rows="3" />
            </label>
          </div>
        </details>

        <button class="submit-button" :disabled="!canSubmit" type="submit">
          {{ isSubmitting ? gt('generated.walk-in-queue.010') : gt('generated.walk-in-queue.011') }}
        </button>
      </form>

      <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
        <h2>{{ gt('generated.walk-in-queue.012') }}</h2>
        <p class="error-code">{{ gt('generated.walk-in-queue.013') }}{{ apiError.error.code }}</p>
        <p class="message-key">{{ gt('generated.walk-in-queue.014') }}{{ apiError.error.messageKey }}</p>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="queue" />
  </main>
</template>

<style scoped>
.walk-in-queue-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.quick-ticket-panel,
.walk-in-queue-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
}

.quick-ticket-panel {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 14px;
}

.quick-ticket-panel p,
.party-size-panel span,
.optional-customer-panel summary span,
.note-field span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
  line-height: 1.25;
  margin: 0;
}

.quick-ticket-panel h1,
.result-panel h2 {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

.quick-ticket-panel h1 {
  font-size: 1.24rem;
  line-height: 1.15;
}

.quick-ticket-panel > strong {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  flex: 0 0 auto;
  font-size: 0.78rem;
  line-height: 1;
  padding: 8px 10px;
}

.walk-in-queue-form {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.party-size-panel {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.party-size-panel > div:first-child {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.party-size-panel strong {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
}

.party-size-stepper {
  display: grid;
  gap: 8px;
  grid-template-columns: 48px minmax(0, 1fr) 48px;
}

.party-size-stepper button,
.submit-button {
  border-radius: 10px;
  font-weight: 950;
  min-height: 48px;
}

.party-size-stepper button {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #c2410c;
  font-size: 1.2rem;
}

.party-size-stepper button:disabled,
.submit-button:disabled {
  background: #cbd5e1;
  border-color: #cbd5e1;
  color: #64748b;
  cursor: not-allowed;
}

input,
textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  min-height: 44px;
  outline: none;
  padding: 10px 11px;
  width: 100%;
}

input {
  font-size: 1.2rem;
  font-weight: 950;
  text-align: center;
}

input:focus,
textarea:focus,
button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.26);
  outline-offset: 2px;
}

.optional-customer-panel {
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  padding: 0;
}

.optional-customer-panel summary {
  align-items: center;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  min-height: 46px;
  padding: 0 12px;
}

.optional-customer-panel summary strong {
  background: #eef2ff;
  border-radius: 999px;
  color: #4338ca;
  font-size: 0.76rem;
  padding: 6px 8px;
}

.optional-customer-fields {
  border-top: 1px solid #e2e8f0;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.note-field {
  display: grid;
  gap: 7px;
}

.submit-button {
  background: #176b4d;
  border: 0;
  color: #ffffff;
  padding: 0 16px;
}

.result-panel {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.result-panel h2 {
  font-size: 0.94rem;
}

.error-panel {
  border-color: #fecaca;
}

.error-code {
  color: #b42318;
  font-weight: 900;
  margin: 0;
}

.message-key {
  color: #475569;
  margin: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 430px) {
  .quick-ticket-panel {
    align-items: start;
    flex-direction: column;
  }

  .quick-ticket-panel > strong {
    align-self: stretch;
    text-align: center;
  }
}
</style>
