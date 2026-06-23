<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { checkInReservation, ReservationCheckInApiError } from '../api/reservationCheckInApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  CheckInReservationRequest,
  CheckInReservationResponse,
  ReservationCheckInApiErrorResponse
} from '../types/reservationCheckIn'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  reservationId: '',
  arrivedAt: '',
  reasonCode: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<CheckInReservationResponse | null>(null)
const apiError = ref<ReservationCheckInApiErrorResponse | null>(null)
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
const arrivedStatus = computed(() => result.value?.status === 'arrived')
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

async function submitCheckIn(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await checkInReservation(
      storeId.value,
      form.reservationId.trim(),
      toRequest(),
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof ReservationCheckInApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'reservation.check_in.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): ReservationCheckInApiErrorResponse | null {
  if (!form.reservationId.trim()) {
    return createLocalError('RESERVATION_NOT_FOUND', 'reservation.not_found')
  }

  if (form.arrivedAt.trim() && !toIsoInstant(form.arrivedAt)) {
    return createLocalError('INVALID_ARRIVED_AT', 'reservation.check_in.invalid_arrived_at')
  }

  return null
}

function toRequest(): CheckInReservationRequest {
  return {
    arrivedAt: optionalIsoInstant(form.arrivedAt),
    reasonCode: optionalValue(form.reasonCode),
    note: optionalValue(form.note)
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function optionalIsoInstant(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? toIsoInstant(trimmed) : null
}

function toIsoInstant(value: string): string | null {
  const timestamp = new Date(value).getTime()

  if (Number.isNaN(timestamp)) {
    return null
  }

  return new Date(timestamp).toISOString()
}

function formatStoreDateTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Singapore',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('day')}-${part('month')}-${part('year')} ${part('hour')}:${part('minute')}`
}

function createIdempotencyKey(): string {
  const prefix = 'reservation:check-in'
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): ReservationCheckInApiErrorResponse {
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
  <main class="staff-workbench-shell staff-workbench-shell--padded reservation-check-in-workbench">
    <section class="reservation-check-in-workbench__header">
      <div>
        <p class="eyebrow">门店员工</p>
        <h1>预约到店</h1>
        <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      </div>
      <RouterLink class="home-link" :to="staffHomeRoute">返回</RouterLink>
    </section>

    <form
      class="reservation-check-in-card"
      aria-label="确认预约客人已到店"
      @submit.prevent="submitCheckIn"
    >
      <header class="reservation-check-in-card__header">
        <span aria-hidden="true">到</span>
        <div>
          <h2>确认预约客人已到店</h2>
          <p>输入预约 ID，按现有权限和状态机完成到店确认。</p>
        </div>
      </header>

      <label class="reservation-check-in-card__primary-field">
        <span>预约 ID</span>
        <input
          v-model="form.reservationId"
          autocomplete="off"
          name="reservationId"
          required
          type="text"
        />
      </label>

      <label>
        <span>到店时间（可选）</span>
        <input v-model="form.arrivedAt" name="arrivedAt" type="datetime-local" />
      </label>

      <label>
        <span>原因代码（可选）</span>
        <input v-model="form.reasonCode" name="reasonCode" type="text" />
      </label>

      <label>
        <span>备注（可选）</span>
        <textarea v-model="form.note" name="note" rows="3" />
      </label>

      <footer class="reservation-check-in-card__actions">
        <button class="reservation-check-in-card__save" :disabled="!canSubmit" type="submit">
          {{ isSubmitting ? '提交中...' : '确认到店' }}
        </button>
        <RouterLink class="reservation-check-in-card__cancel" :to="staffHomeRoute">
          返回
        </RouterLink>
      </footer>
    </form>

    <section v-if="result" class="check-in-result-card check-in-result-card--success" aria-live="polite">
      <h2>到店确认成功</h2>
      <div class="reservation-highlight status-highlight">
        <span>预约状态</span>
        <strong>{{ result.status }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>预约编号</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>是否已到店</span>
        <strong>{{ result.alreadyArrived }}</strong>
      </div>
      <p v-if="arrivedStatus" class="arrived-note">状态：arrived</p>
      <p v-if="result.alreadyArrived" class="arrived-note">该预约此前已完成到店确认</p>
      <dl>
        <div>
          <dt>预约 ID</dt>
          <dd>{{ result.reservationId }}</dd>
        </div>
        <div>
          <dt>到店时间</dt>
          <dd>{{ formatStoreDateTime(result.arrivedAt) }}</dd>
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

    <section v-if="apiError" class="check-in-result-card check-in-result-card--error" aria-live="assertive">
      <h2>到店确认失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <p v-if="lastIdempotencyKey" class="idempotency-key">
      幂等键 {{ lastIdempotencyKey }}
    </p>

    <StaffBottomNav :store-id="storeId" active-tab="reservation" />
  </main>
</template>

<style scoped>
.reservation-check-in-workbench__header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.eyebrow,
.store-context,
.idempotency-key,
.arrived-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.arrived-note {
  color: #176b4d;
  font-weight: 800;
}

.home-link {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 999px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.86rem;
  font-weight: 900;
  justify-content: center;
  min-height: 36px;
  padding: 0 14px;
  text-decoration: none;
}

h1,
h2 {
  color: #14213d;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.35rem;
  line-height: 1.15;
}

h2 {
  font-size: 1rem;
}

.reservation-check-in-card,
.check-in-result-card {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.reservation-check-in-card {
  display: grid;
  gap: 11px;
  padding: 16px;
}

.reservation-check-in-card__header {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: auto minmax(0, 1fr);
}

.reservation-check-in-card__header > span {
  align-items: center;
  background: #ffedd5;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  font-size: 0.9rem;
  font-weight: 950;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.reservation-check-in-card__header p {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.35;
  margin: 3px 0 0;
}

label {
  display: grid;
  gap: 6px;
}

label span,
dt,
.reservation-highlight span {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
}

input,
textarea {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  min-height: 40px;
  outline: none;
  padding: 8px 12px;
  width: 100%;
}

input:focus,
textarea:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.reservation-check-in-card__primary-field {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  padding: 12px;
}

.reservation-check-in-card__primary-field input {
  background: #ffffff;
  font-size: 1.05rem;
  font-weight: 800;
  min-height: 46px;
}

.reservation-check-in-card__actions {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 4px;
}

.reservation-check-in-card__save,
.reservation-check-in-card__cancel {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font-size: 0.92rem;
  font-weight: 950;
  justify-content: center;
  min-height: 42px;
  padding: 0 16px;
  text-decoration: none;
}

.reservation-check-in-card__save {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-check-in-card__save:disabled {
  background: #fdba74;
  border-color: #fdba74;
  cursor: not-allowed;
}

.reservation-check-in-card__cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.check-in-result-card {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.check-in-result-card--success {
  border-color: #a7d7be;
}

.check-in-result-card--error {
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

button:focus-visible,
a:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

</style>
