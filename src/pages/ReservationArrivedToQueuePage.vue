<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  queueArrivedReservation,
  ReservationArrivedToQueueApiError
} from '../api/reservationArrivedToQueueApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  QueueArrivedReservationRequest,
  QueueArrivedReservationResponse,
  ReservationArrivedToQueueApiErrorResponse
} from '../types/reservationArrivedToQueue'

const AUTO_PARTY_SIZE_GROUP = 'auto'

const partySizeOptions = [
  { value: AUTO_PARTY_SIZE_GROUP, label: '自动推导' },
  { value: '1-2', label: '1-2' },
  { value: '3-4', label: '3-4' },
  { value: '5-6', label: '5-6' },
  { value: '7+', label: '7+' }
]

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  reservationId: '',
  partySizeGroup: AUTO_PARTY_SIZE_GROUP,
  reasonCode: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<QueueArrivedReservationResponse | null>(null)
const apiError = ref<ReservationArrivedToQueueApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const canSubmit = computed(
  () => !isSubmitting.value && !!storeId.value && !!form.reservationId.trim()
)
const arrivedStatus = computed(() => result.value?.reservationStatus === 'arrived')
const waitingStatus = computed(() => result.value?.queueTicketStatus === 'waiting')
const eventsDisplay = computed(() => {
  if (!result.value?.events.length) {
    return '[]'
  }

  return result.value.events.join(', ')
})

watch(
  () => route.query.reservationId,
  value => {
    const reservationId = queryValue(value)

    if (reservationId) {
      form.reservationId = reservationId
    }
  },
  { immediate: true }
)

async function submitQueue(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await queueArrivedReservation(
      storeId.value,
      form.reservationId.trim(),
      toRequest(),
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof ReservationArrivedToQueueApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'reservation.queue.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): ReservationArrivedToQueueApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'reservation.store_scope_mismatch')
  }

  if (!form.reservationId.trim()) {
    return createLocalError('INVALID_COMMAND', 'reservation.queue.reservation_id_required')
  }

  return null
}

function toRequest(): QueueArrivedReservationRequest {
  return {
    partySizeGroup:
      form.partySizeGroup === AUTO_PARTY_SIZE_GROUP
        ? null
        : optionalValue(form.partySizeGroup),
    reasonCode: optionalValue(form.reasonCode),
    note: optionalValue(form.note)
  }
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

function createIdempotencyKey(): string {
  const prefix = 'reservation:queue'
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(
  code: string,
  messageKey: string
): ReservationArrivedToQueueApiErrorResponse {
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
      <h1>预约排队</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="queue-form" @submit.prevent="submitQueue">
      <label class="reservation-id-field">
        <span>预约 ID</span>
        <input
          v-model="form.reservationId"
          autocomplete="off"
          name="reservationId"
          required
          type="text"
        />
      </label>

      <section class="party-size-panel" aria-label="人数分组">
        <label>
          <span>人数分组</span>
          <select v-model="form.partySizeGroup" name="partySizeGroup">
            <option v-for="option in partySizeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <p>选择自动推导时，由后端按预约人数匹配队列分组。</p>
      </section>

      <details class="field-group">
        <summary>备注</summary>
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
        {{ isSubmitting ? '提交中...' : '进入排队' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>{{ result.alreadyQueued ? '已在排队中' : '排队成功' }}</h2>
      <div class="reservation-highlight ticket-highlight">
        <span>排队号码</span>
        <strong>{{ result.queueTicketNumber }}</strong>
      </div>
      <div class="reservation-highlight status-highlight">
        <span>排队状态</span>
        <strong>{{ result.queueTicketStatus }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>预约编号</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>人数分组</span>
        <strong>{{ result.partySizeGroup }}</strong>
      </div>
      <div class="reservation-highlight already-queued-highlight">
        <span>是否已在排队中</span>
        <strong>{{ result.alreadyQueued }}</strong>
      </div>
      <p v-if="arrivedStatus" class="queue-note">预约状态：arrived</p>
      <p v-if="waitingStatus" class="queue-note">排队状态：waiting</p>
      <p v-if="result.alreadyQueued" class="queue-note">该预约已存在等待中的排队号</p>
      <dl>
        <div>
          <dt>预约 ID</dt>
          <dd>{{ result.reservationId }}</dd>
        </div>
        <div>
          <dt>预约状态</dt>
          <dd>{{ result.reservationStatus }}</dd>
        </div>
        <div>
          <dt>排队记录 ID</dt>
          <dd>{{ result.queueTicketId }}</dd>
        </div>
        <div>
          <dt>队列分组 ID</dt>
          <dd>{{ result.queueGroupId }}</dd>
        </div>
        <div>
          <dt>队列分组代码</dt>
          <dd>{{ optionalDisplay(result.queueGroupCode) }}</dd>
        </div>
        <div>
          <dt>人数</dt>
          <dd>{{ result.partySize }}</dd>
        </div>
        <div>
          <dt>营业日期</dt>
          <dd>{{ optionalDisplay(result.businessDate) }}</dd>
        </div>
        <div>
          <dt>队列位置</dt>
          <dd>{{ optionalDisplay(result.queuePosition) }}</dd>
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
      <h2>排队失败</h2>
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
.party-size-panel p,
.queue-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.queue-note {
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

.queue-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.queue-form {
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
.reservation-highlight span {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
}

input,
select,
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
select:focus,
textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.reservation-id-field {
  background: #eaf2ff;
  border: 1px solid #b8cdf6;
  border-radius: 8px;
  padding: 12px;
}

.reservation-id-field input {
  background: #ffffff;
  font-size: 1.05rem;
  font-weight: 800;
  min-height: 56px;
}

.party-size-panel,
.field-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.party-size-panel {
  display: grid;
  gap: 8px;
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

.reservation-highlight {
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

.already-queued-highlight {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.reservation-highlight strong {
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
