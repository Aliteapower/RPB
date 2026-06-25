<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { callQueueTicket, QueueCallApiError } from '../api/queueCallApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  CallQueueTicketRequest,
  CallQueueTicketResponse,
  QueueCallApiErrorResponse
} from '../types/queueCall'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  queueTicketId: '',
  calledAt: '',
  reasonCode: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<CallQueueTicketResponse | null>(null)
const apiError = ref<QueueCallApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const canSubmit = computed(
  () => !isSubmitting.value && !!storeId.value && !!form.queueTicketId.trim()
)
const calledStatus = computed(() => result.value?.queueTicketStatus === 'called')
const arrivedStatus = computed(() => result.value?.reservationStatus === 'arrived')
const eventsDisplay = computed(() => {
  if (!result.value?.events.length) {
    return '[]'
  }

  return result.value.events.join(', ')
})

watch(
  () => route.query.queueTicketId,
  value => {
    const queueTicketId = queryValue(value)

    if (queueTicketId) {
      form.queueTicketId = queueTicketId
    }
  },
  { immediate: true }
)

async function submitCall(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await callQueueTicket(
      storeId.value,
      form.queueTicketId.trim(),
      toRequest(),
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof QueueCallApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'queue.call.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): QueueCallApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'queue.call.store_scope_mismatch')
  }

  if (!form.queueTicketId.trim()) {
    return createLocalError('INVALID_COMMAND', 'queue.call.queue_ticket_id_required')
  }

  if (form.calledAt.trim() && !toIsoInstant(form.calledAt)) {
    return createLocalError('INVALID_COMMAND', 'queue.call.called_at_invalid')
  }

  return null
}

function toRequest(): CallQueueTicketRequest {
  return {
    calledAt: toIsoInstant(form.calledAt),
    reasonCode: optionalValue(form.reasonCode),
    note: optionalValue(form.note)
  }
}

function toIsoInstant(value: string): string | null {
  const trimmed = value.trim()

  if (!trimmed) {
    return null
  }

  const parsed = new Date(trimmed)

  if (Number.isNaN(parsed.getTime())) {
    return null
  }

  return parsed.toISOString()
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function optionalDisplay(value: string | number | null | undefined): string {
  if (typeof value === 'number') {
    return String(value)
  }

  return value?.trim() ? value : '未返回'
}

function queueTicketDisplayText(value: CallQueueTicketResponse): string {
  const displayNumber = value.queueTicketDisplayNumber?.trim() || String(value.queueTicketNumber)
  return `#${displayNumber}`
}

function createIdempotencyKey(): string {
  const prefix = 'queue:call'
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): QueueCallApiErrorResponse {
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

function queryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}
</script>

<template>
  <main class="page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>排队叫号</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="call-form" @submit.prevent="submitCall">
      <label class="ticket-id-field">
        <span>排队票 ID</span>
        <input
          v-model="form.queueTicketId"
          autocomplete="off"
          name="queueTicketId"
          required
          type="text"
        />
      </label>

      <label class="called-at-field">
        <span>叫号时间（可选）</span>
        <input v-model="form.calledAt" name="calledAt" type="datetime-local" />
      </label>

      <details class="field-group">
        <summary>补充信息</summary>
        <label>
          <span>原因代码（可选）</span>
          <input v-model="form.reasonCode" name="reasonCode" type="text" />
        </label>
        <label>
          <span>备注（可选）</span>
          <textarea v-model="form.note" name="note" rows="3" />
        </label>
      </details>

      <button class="submit-button" :disabled="!canSubmit" type="submit">
        {{ isSubmitting ? '叫号中...' : '执行叫号' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>{{ result.alreadyCalled ? '已叫号' : '叫号成功' }}</h2>
      <div class="call-highlight ticket-highlight">
        <span>排队号码</span>
        <strong>{{ queueTicketDisplayText(result) }}</strong>
      </div>
      <div class="call-highlight status-highlight">
        <span>叫号状态</span>
        <strong>{{ result.queueTicketStatus }}</strong>
      </div>
      <div class="call-highlight hold-highlight">
        <span>保留到</span>
        <strong>{{ result.holdUntilAt }}</strong>
      </div>
      <div class="call-highlight">
        <span>预约编号</span>
        <strong>{{ optionalDisplay(result.reservationCode) }}</strong>
      </div>
      <div class="call-highlight already-called-highlight">
        <span>是否已叫号</span>
        <strong>{{ result.alreadyCalled }}</strong>
      </div>
      <p v-if="calledStatus" class="call-note">排队状态：called</p>
      <p v-if="arrivedStatus" class="call-note">预约状态：arrived</p>
      <p v-if="result.alreadyCalled" class="call-note">该排队票已有叫号证据，本次按成功展示。</p>
      <dl>
        <div>
          <dt>排队记录 ID</dt>
          <dd>{{ result.queueTicketId }}</dd>
        </div>
        <div>
          <dt>排队状态</dt>
          <dd>{{ result.queueTicketStatus }}</dd>
        </div>
        <div>
          <dt>预约 ID</dt>
          <dd>{{ optionalDisplay(result.reservationId) }}</dd>
        </div>
        <div>
          <dt>预约状态</dt>
          <dd>{{ optionalDisplay(result.reservationStatus) }}</dd>
        </div>
        <div>
          <dt>叫号时间</dt>
          <dd>{{ result.calledAt }}</dd>
        </div>
        <div>
          <dt>保留截止</dt>
          <dd>{{ result.holdUntilAt }}</dd>
        </div>
        <div>
          <dt>事件</dt>
          <dd>{{ eventsDisplay }}</dd>
        </div>
        <div>
          <dt>幂等状态</dt>
          <dd>{{ result.idempotency.status }}</dd>
        </div>
        <div>
          <dt>幂等重放</dt>
          <dd>{{ result.idempotency.replayed ?? false }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>叫号失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <p v-if="lastIdempotencyKey" class="idempotency-key">
      幂等键 {{ lastIdempotencyKey }}
    </p>
  </main>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 620px;
  min-height: 100vh;
  padding: 20px 14px 32px;
}

.page-header {
  display: grid;
  gap: 4px;
}

.eyebrow,
.store-context,
.idempotency-key,
.call-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.call-note {
  color: #176b4d;
  font-weight: 800;
}

.home-link {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  justify-self: start;
  text-decoration: none;
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

.call-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.call-form {
  display: grid;
  gap: 12px;
  padding: 14px;
}

label {
  display: grid;
  gap: 6px;
}

label span,
summary,
dt,
.call-highlight span {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
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

input:focus,
textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.ticket-id-field {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  padding: 12px;
}

.ticket-id-field input {
  background: #ffffff;
  font-size: 1.05rem;
  font-weight: 800;
  min-height: 56px;
}

.called-at-field,
.field-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.field-group[open] {
  display: grid;
  gap: 12px;
}

summary {
  cursor: pointer;
  min-height: 32px;
}

.submit-button {
  background: #176b4d;
  border: 0;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 800;
  min-height: 52px;
  padding: 0 16px;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.result-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.success-panel {
  border-color: #a7d7be;
}

.error-panel {
  border-color: #f4b8b8;
}

.call-highlight {
  background: #eef6f1;
  border: 1px solid #b8d8c4;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  padding: 11px;
}

.ticket-highlight {
  background: #fff7ed;
  border-color: #fed7aa;
}

.ticket-highlight strong {
  color: #c2410c;
  font-size: 1.55rem;
}

.status-highlight {
  background: #eaf2ff;
  border-color: #b8cdf6;
}

.hold-highlight {
  background: #f0f9ff;
  border-color: #bae6fd;
}

.already-called-highlight {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.call-highlight strong {
  color: #14213d;
  font-size: 1.1rem;
  overflow-wrap: anywhere;
}

dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

dt,
dd {
  margin: 0;
}

dd {
  color: #1d2736;
  overflow-wrap: anywhere;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  margin: 0;
}

.message-key {
  color: #41516a;
  margin: 0;
  overflow-wrap: anywhere;
}

.idempotency-key {
  overflow-wrap: anywhere;
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
