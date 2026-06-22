<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  ReservationArrivedDirectSeatingApiError,
  seatArrivedReservation
} from '../api/reservationArrivedDirectSeatingApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ReservationArrivedDirectSeatingApiErrorResponse,
  SeatArrivedReservationRequest,
  SeatArrivedReservationResponse
} from '../types/reservationArrivedDirectSeating'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  reservationId: '',
  tableId: '',
  tableGroupId: '',
  overrideReasonCode: '',
  overrideNote: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<SeatArrivedReservationResponse | null>(null)
const apiError = ref<ReservationArrivedDirectSeatingApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const hasReservationId = computed(() => !!form.reservationId.trim())
const hasTableId = computed(() => !!form.tableId.trim())
const hasTableGroupId = computed(() => !!form.tableGroupId.trim())
const hasExactlyOneResource = computed(() => hasTableId.value !== hasTableGroupId.value)
const canSubmit = computed(
  () =>
    !isSubmitting.value &&
    !!storeId.value &&
    hasReservationId.value &&
    hasExactlyOneResource.value
)
const seatedStatus = computed(() => result.value?.reservationStatus === 'seated')
const directSeatingErrorHint = computed(() => {
  switch (apiError.value?.error.code) {
    case 'RESOURCE_SELECTION_REQUIRED':
      return '请填写桌台 ID 或桌组 ID'
    case 'RESOURCE_SELECTION_CONFLICT':
      return '桌台 ID 和桌组 ID 只能选择一个'
    default:
      return ''
  }
})
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

async function submitDirectSeating(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await seatArrivedReservation(
      storeId.value,
      form.reservationId.trim(),
      toRequest(),
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof ReservationArrivedDirectSeatingApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'reservation.direct_seating.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): ReservationArrivedDirectSeatingApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'reservation.store_scope_mismatch')
  }

  if (!form.reservationId.trim()) {
    return createLocalError('INVALID_COMMAND', 'reservation.direct_seating.reservation_id_required')
  }

  if (!hasTableId.value && !hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'reservation.resource_selection_required')
  }

  if (hasTableId.value && hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'reservation.resource_selection_conflict')
  }

  return null
}

function toRequest(): SeatArrivedReservationRequest {
  return {
    tableId: optionalValue(form.tableId),
    tableGroupId: optionalValue(form.tableGroupId),
    overrideReasonCode: optionalValue(form.overrideReasonCode),
    overrideNote: optionalValue(form.overrideNote),
    note: optionalValue(form.note)
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(): string {
  const prefix = 'reservation:seat'
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(
  code: string,
  messageKey: string
): ReservationArrivedDirectSeatingApiErrorResponse {
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
      <h1>预约入座</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="direct-seating-form" @submit.prevent="submitDirectSeating">
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

      <section class="resource-panel" aria-label="桌台选择">
        <p class="resource-rule">桌台 ID 和桌组 ID 必须二选一</p>
        <label>
          <span>桌台 ID</span>
          <input v-model="form.tableId" autocomplete="off" name="tableId" type="text" />
        </label>
        <label>
          <span>桌组 ID</span>
          <input v-model="form.tableGroupId" autocomplete="off" name="tableGroupId" type="text" />
        </label>
      </section>

      <details class="field-group">
        <summary>调整信息</summary>
        <label>
          <span>调整原因代码（可选）</span>
          <input v-model="form.overrideReasonCode" name="overrideReasonCode" type="text" />
        </label>
        <label>
          <span>调整说明（可选）</span>
          <textarea v-model="form.overrideNote" name="overrideNote" rows="3" />
        </label>
      </details>

      <details class="field-group">
        <summary>备注</summary>
        <label>
          <span>备注（可选）</span>
          <textarea v-model="form.note" name="note" rows="3" />
        </label>
      </details>

      <button class="submit-button" :disabled="!canSubmit" type="submit">
        {{ isSubmitting ? '提交中...' : '确认入座' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>入座成功</h2>
      <div class="reservation-highlight status-highlight">
        <span>预约状态</span>
        <strong>{{ result.reservationStatus }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>预约编号</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>入座记录 ID</span>
        <strong>{{ result.seatingId }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>资源</span>
        <strong>{{ result.resourceType }} {{ result.resourceId }}</strong>
      </div>
      <div class="reservation-highlight already-seated-highlight">
        <span>是否已入座</span>
        <strong>{{ result.alreadySeated }}</strong>
      </div>
      <p v-if="seatedStatus" class="seated-note">状态：seated</p>
      <p v-if="result.alreadySeated" class="seated-note">该预约此前已完成入座</p>
      <dl>
        <div>
          <dt>预约 ID</dt>
          <dd>{{ result.reservationId }}</dd>
        </div>
        <div>
          <dt>入座状态</dt>
          <dd>{{ result.seatingStatus }}</dd>
        </div>
        <div>
          <dt>资源类型</dt>
          <dd>{{ result.resourceType }}</dd>
        </div>
        <div>
          <dt>资源 ID</dt>
          <dd>{{ result.resourceId }}</dd>
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
      <h2>入座失败</h2>
      <p v-if="directSeatingErrorHint" class="message-key">{{ directSeatingErrorHint }}</p>
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
.resource-rule,
.seated-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.seated-note {
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

.direct-seating-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.direct-seating-form {
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

.resource-panel,
.field-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.resource-panel {
  display: grid;
  gap: 12px;
}

.resource-rule {
  color: #315f91;
  font-weight: 800;
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

.status-highlight {
  background: #eaf2ff;
  border-color: #b8cdf6;
}

.already-seated-highlight {
  background: #fff7ed;
  border-color: #fed7aa;
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
