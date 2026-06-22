<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  SeatingFromCalledQueueApiError,
  seatCalledQueueTicket
} from '../api/seatingFromCalledQueueApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  SeatCalledQueueTicketRequest,
  SeatCalledQueueTicketResponse,
  SeatingFromCalledQueueApiErrorResponse
} from '../types/seatingFromCalledQueue'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  queueTicketId: '',
  tableId: '',
  tableGroupId: '',
  overrideReasonCode: '',
  overrideNote: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<SeatCalledQueueTicketResponse | null>(null)
const apiError = ref<SeatingFromCalledQueueApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const hasQueueTicketId = computed(() => !!form.queueTicketId.trim())
const hasTableId = computed(() => !!form.tableId.trim())
const hasTableGroupId = computed(() => !!form.tableGroupId.trim())
const hasExactlyOneResource = computed(() => hasTableId.value !== hasTableGroupId.value)
const resourceSelectionError = computed(() => {
  if (!hasQueueTicketId.value) {
    return null
  }

  if (!hasTableId.value && !hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'queue.seat.resource_selection_required')
      .error
  }

  if (hasTableId.value && hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'queue.seat.resource_selection_conflict')
      .error
  }

  return null
})
const canSubmit = computed(
  () =>
    !isSubmitting.value &&
    !!storeId.value &&
    hasQueueTicketId.value &&
    hasExactlyOneResource.value
)
const seatedQueueStatus = computed(() => result.value?.queueTicketStatus === 'seated')
const seatedReservationStatus = computed(() => result.value?.reservationStatus === 'seated')
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

async function submitQueueSeating(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await seatCalledQueueTicket(
      storeId.value,
      form.queueTicketId.trim(),
      toRequest(),
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof SeatingFromCalledQueueApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'queue.seat.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): SeatingFromCalledQueueApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'queue.seat.store_scope_mismatch')
  }

  if (!form.queueTicketId.trim()) {
    return createLocalError('INVALID_COMMAND', 'queue.seat.queue_ticket_id_required')
  }

  if (!hasTableId.value && !hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'queue.seat.resource_selection_required')
  }

  if (hasTableId.value && hasTableGroupId.value) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'queue.seat.resource_selection_conflict')
  }

  return null
}

function toRequest(): SeatCalledQueueTicketRequest {
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

function optionalDisplay(value: string | number | boolean | null | undefined): string {
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return value?.trim() ? value : '未返回'
}

function createIdempotencyKey(): string {
  const prefix = 'queue:seat'
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(
  code: string,
  messageKey: string
): SeatingFromCalledQueueApiErrorResponse {
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
      <h1>排队入座</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="queue-seating-form" @submit.prevent="submitQueueSeating">
      <label class="queue-ticket-id-field">
        <span>排队票 ID</span>
        <input
          v-model="form.queueTicketId"
          autocomplete="off"
          name="queueTicketId"
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
        <p v-if="resourceSelectionError" class="resource-error">
          错误代码：{{ resourceSelectionError.code }}<br />
          消息键：{{ resourceSelectionError.messageKey }}
        </p>
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
      <h2>{{ result.alreadySeated ? '已入座' : '入座成功' }}</h2>
      <div class="queue-highlight ticket-highlight">
        <span>排队号码</span>
        <strong>{{ result.queueTicketNumber }}</strong>
      </div>
      <div class="queue-highlight status-highlight">
        <span>排队状态</span>
        <strong>{{ result.queueTicketStatus }}</strong>
      </div>
      <div class="queue-highlight status-highlight">
        <span>预约状态</span>
        <strong>{{ result.reservationStatus }}</strong>
      </div>
      <div class="queue-highlight">
        <span>预约编号</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="queue-highlight seating-highlight">
        <span>入座记录 ID</span>
        <strong>{{ result.seatingId }}</strong>
      </div>
      <div class="queue-highlight">
        <span>资源</span>
        <strong>{{ result.resourceType }} {{ result.resourceId }}</strong>
      </div>
      <div class="queue-highlight already-seated-highlight">
        <span>是否已入座</span>
        <strong>{{ result.alreadySeated }}</strong>
      </div>
      <p v-if="seatedQueueStatus" class="seated-note">排队状态：seated</p>
      <p v-if="seatedReservationStatus" class="seated-note">预约状态：seated</p>
      <p v-if="result.alreadySeated" class="seated-note">该排队票已有入座记录，本次按成功展示。</p>
      <dl>
        <div>
          <dt>排队记录 ID</dt>
          <dd>{{ result.queueTicketId }}</dd>
        </div>
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
          <dd>{{ optionalDisplay(result.idempotency.replayed ?? false) }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>入座失败</h2>
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

.queue-seating-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.queue-seating-form {
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
.queue-highlight span {
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

.queue-ticket-id-field {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  padding: 12px;
}

.queue-ticket-id-field input {
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

.resource-error {
  color: #b42318;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
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

.queue-highlight {
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

.seating-highlight {
  background: #f0f9ff;
  border-color: #bae6fd;
}

.already-seated-highlight {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.queue-highlight strong {
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
