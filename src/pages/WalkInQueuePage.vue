<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { queueWalkIn, WalkInQueueApiError } from '../api/walkInQueueApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffGuestContactLookup from '../components/staff/StaffGuestContactLookup.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../components/staff/staffGuestContact'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  QueueWalkInRequest,
  QueueWalkInResponse,
  WalkInQueueApiErrorResponse
} from '../types/walkInQueue'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  partySize: 2,
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<QueueWalkInResponse | null>(null)
const apiError = ref<WalkInQueueApiErrorResponse | null>(null)

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const queueTicketListRoute = computed(() => ({
  name: 'queue-ticket-list',
  params: {
    storeId: storeId.value
  }
}))
const canSubmit = computed(
  () => !isSubmitting.value && !!storeId.value && Number.isInteger(form.partySize) && form.partySize > 0
)

async function submitWalkInQueue(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  isSubmitting.value = true

  try {
    result.value = await queueWalkIn(storeId.value, toRequest(), idempotencyKey)
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
</script>

<template>
  <main class="staff-workbench-shell staff-workbench-shell--padded page-shell">
    <section class="page-header">
      <p class="eyebrow">门店接待</p>
      <h1>现场取号</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="walk-in-queue-form" @submit.prevent="submitWalkInQueue">
      <section class="party-size-panel" aria-label="现场人数">
        <span>人数</span>
        <div class="party-size-stepper">
          <button type="button" :disabled="form.partySize <= 1" @click="updatePartySize(-1)">-</button>
          <input v-model.number="form.partySize" inputmode="numeric" min="1" name="partySize" type="number" />
          <button type="button" @click="updatePartySize(1)">+</button>
        </div>
      </section>

      <StaffGuestContactLookup
        :store-id="storeId || ''"
        v-model:customer-id="form.customerId"
        v-model:customer-name="form.customerName"
        v-model:salutation="form.customerSalutation"
        v-model:phone-local="form.phoneLocal"
        :disabled="isSubmitting"
      />

      <label class="note-field">
        <span>备注</span>
        <textarea v-model="form.note" name="note" rows="3" />
      </label>

      <button class="submit-button" :disabled="!canSubmit" type="submit">
        {{ isSubmitting ? '取号中...' : '确认取号入队' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>{{ result.alreadyQueued ? '已在队列中' : '取号成功' }}</h2>
      <div class="ticket-number">
        <span>排队号码</span>
        <strong>#{{ result.queueTicketNumber }}</strong>
      </div>
      <dl>
        <div>
          <dt>人数</dt>
          <dd>{{ result.partySize }}</dd>
        </div>
        <div>
          <dt>人数分组</dt>
          <dd>{{ result.partySizeGroup }}</dd>
        </div>
        <div>
          <dt>队列位置</dt>
          <dd>{{ result.queuePosition ?? '未返回' }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ result.queueTicketStatus }}</dd>
        </div>
      </dl>
      <RouterLink class="primary-link" :to="queueTicketListRoute">进入排队列表</RouterLink>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>取号失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="queue" />
  </main>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 620px;
  min-height: 100vh;
  padding: 20px 14px calc(86px + env(safe-area-inset-bottom));
}

.page-header,
.walk-in-queue-form,
.result-panel,
dl {
  display: grid;
  gap: 12px;
}

.page-header {
  gap: 4px;
}

.eyebrow,
.store-context,
dt {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.home-link,
.primary-link {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  justify-self: start;
  text-decoration: none;
}

.primary-link {
  align-items: center;
  background: #176b4d;
  border-radius: 8px;
  color: #ffffff;
  display: inline-flex;
  min-height: 44px;
  padding: 0 14px;
}

h1,
h2 {
  color: #14213d;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.7rem;
  line-height: 1.15;
}

h2 {
  font-size: 1rem;
}

.walk-in-queue-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
  padding: 14px;
}

.party-size-panel,
.note-field {
  display: grid;
  gap: 8px;
}

.party-size-panel > span,
.note-field span,
dt {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 800;
}

.party-size-stepper {
  display: grid;
  gap: 8px;
  grid-template-columns: 48px minmax(0, 1fr) 48px;
}

.party-size-stepper button,
.submit-button {
  border-radius: 8px;
  font-weight: 900;
  min-height: 48px;
}

.party-size-stepper button {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #c2410c;
  font-size: 1.2rem;
}

.party-size-stepper button:disabled {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #94a3b8;
}

input,
textarea {
  background: #fbfcfe;
  border: 1px solid #c8d3e2;
  border-radius: 6px;
  color: #182536;
  min-height: 44px;
  outline: none;
  padding: 10px 11px;
  width: 100%;
}

input {
  font-size: 1.2rem;
  font-weight: 900;
  text-align: center;
}

input:focus,
textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.submit-button {
  background: #f97316;
  border: 0;
  color: #ffffff;
  padding: 0 16px;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.success-panel {
  border-color: #a7d7be;
}

.error-panel {
  border-color: #f4b8b8;
}

.ticket-number {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  padding: 12px;
}

.ticket-number span {
  color: #9a3412;
  font-size: 0.82rem;
  font-weight: 900;
}

.ticket-number strong {
  color: #c2410c;
  font-size: 2rem;
  line-height: 1.05;
}

dl {
  margin: 0;
}

dl div {
  display: grid;
  gap: 4px;
}

dd {
  color: #1d2736;
  font-weight: 800;
  margin: 0;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  margin: 0;
}

.message-key {
  overflow-wrap: anywhere;
}

.message-key {
  color: #41516a;
  margin: 0;
}

@media (min-width: 720px) {
  .page-shell {
    padding-top: 36px;
  }

  h1 {
    font-size: 2rem;
  }
}
</style>
