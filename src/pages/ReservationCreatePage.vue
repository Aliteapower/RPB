<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { createReservation, ReservationCreateApiError } from '../api/reservationCreateApi'
import DateTimeWheelPicker from '../components/DateTimeWheelPicker.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  CreateReservationRequest,
  CreateReservationResponse,
  ReservationApiErrorResponse
} from '../types/reservation'

const E164_PATTERN = /^[+][1-9][0-9]{1,14}$/

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  partySize: 4,
  reservedStartAt: defaultReservationStartAt(),
  reservedEndAt: '',
  customerId: '',
  customerName: '',
  customerNickname: '',
  phoneE164: '',
  note: ''
})

const isSubmitting = ref(false)
const result = ref<CreateReservationResponse | null>(null)
const apiError = ref<ReservationApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')
const useCustomEndTime = ref(false)

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const canSubmit = computed(() => !isSubmitting.value && !!storeId.value)
const isConfirmed = computed(() => result.value?.status === 'confirmed')
const customerDisplay = computed(() => {
  if (!result.value?.customer) {
    return 'null'
  }

  return result.value.customer.displayName || result.value.customer.id || 'null'
})

async function submitReservation(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await createReservation(storeId.value, toRequest(), idempotencyKey)
  } catch (error) {
    apiError.value =
      error instanceof ReservationCreateApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'reservation.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): ReservationApiErrorResponse | null {
  if (!Number.isInteger(form.partySize) || form.partySize <= 0) {
    return createLocalError('INVALID_PARTY_SIZE', 'reservation.invalid_party_size')
  }

  if (!form.reservedStartAt.trim() || !toIsoInstant(form.reservedStartAt)) {
    return createLocalError('INVALID_TIME_RANGE', 'reservation.invalid_time_range')
  }

  const reservedEndAt = form.reservedEndAt.trim()
  if (reservedEndAt) {
    const start = toIsoInstant(form.reservedStartAt)
    const end = toIsoInstant(reservedEndAt)

    if (!start || !end || new Date(end).getTime() <= new Date(start).getTime()) {
      return createLocalError('INVALID_TIME_RANGE', 'reservation.invalid_time_range')
    }
  }

  const phoneE164 = form.phoneE164.trim()
  if (phoneE164 && !E164_PATTERN.test(phoneE164)) {
    return createLocalError('INVALID_PHONE_E164', 'reservation.invalid_phone_e164')
  }

  return null
}

function toRequest(): CreateReservationRequest {
  return {
    partySize: form.partySize,
    reservedStartAt: toIsoInstant(form.reservedStartAt) ?? '',
    reservedEndAt: optionalIsoInstant(form.reservedEndAt),
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerNickname),
    phoneE164: optionalValue(form.phoneE164),
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

function onCustomEndTimeChanged(): void {
  if (useCustomEndTime.value) {
    form.reservedEndAt = addMinutesToDateTimeLocal(form.reservedStartAt, 90)
    return
  }

  form.reservedEndAt = ''
}

function addMinutesToDateTimeLocal(value: string, minutes: number): string {
  const date = new Date(value)
  const validDate = Number.isNaN(date.getTime()) ? new Date() : date
  return toDateTimeLocalValue(new Date(validDate.getTime() + minutes * 60 * 1000))
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
  const prefix = 'reservation:create'
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): ReservationApiErrorResponse {
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

function defaultReservationStartAt(): string {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  date.setHours(19, 0, 0, 0)
  return toDateTimeLocalValue(date)
}

function toDateTimeLocalValue(date: Date): string {
  const year = date.getFullYear()
  const month = pad2(date.getMonth() + 1)
  const day = pad2(date.getDate())
  const hours = pad2(date.getHours())
  const minutes = pad2(date.getMinutes())
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
</script>

<template>
  <main class="page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>创建预约</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="reservation-form" @submit.prevent="submitReservation">
      <section class="primary-grid" aria-label="required reservation fields">
        <label class="party-size-field">
          <span>人数</span>
          <input
            v-model.number="form.partySize"
            min="1"
            inputmode="numeric"
            name="partySize"
            required
            type="number"
          />
        </label>

        <DateTimeWheelPicker
          v-model="form.reservedStartAt"
          class="time-field"
          label="预约开始时间"
          name="reservedStartAt"
        />
      </section>

      <details class="field-group">
        <summary>时间</summary>
        <label class="toggle-field">
          <input
            v-model="useCustomEndTime"
            name="useCustomEndTime"
            type="checkbox"
            @change="onCustomEndTimeChanged"
          />
          <span>预约结束时间（可选）</span>
        </label>
        <DateTimeWheelPicker
          v-if="useCustomEndTime"
          v-model="form.reservedEndAt"
          class="compact-picker"
          label="预约结束时间"
          name="reservedEndAt"
        />
      </details>

      <details class="field-group">
        <summary>客户信息</summary>
        <label>
          <span>客户 ID（可选）</span>
          <input v-model="form.customerId" autocomplete="off" name="customerId" type="text" />
        </label>
        <label>
          <span>客户姓名（可选）</span>
          <input v-model="form.customerName" autocomplete="name" name="customerName" type="text" />
        </label>
        <label>
          <span>客户昵称（可选）</span>
          <input v-model="form.customerNickname" name="customerNickname" type="text" />
        </label>
        <label>
          <span>手机号（可选）</span>
          <input
            v-model="form.phoneE164"
            autocomplete="tel"
            name="phoneE164"
            placeholder="+6591234567"
            type="tel"
          />
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
        {{ isSubmitting ? '提交中...' : '提交预约' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>预约创建成功</h2>
      <div class="reservation-highlight">
        <span>预约编号</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="reservation-highlight status-highlight">
        <span>预约状态</span>
        <strong>{{ result.status }}</strong>
      </div>
      <p v-if="isConfirmed" class="confirmed-note">状态：confirmed</p>
      <dl>
        <div>
          <dt>预约 ID</dt>
          <dd>{{ result.reservationId }}</dd>
        </div>
        <div>
          <dt>人数</dt>
          <dd>{{ result.partySize }}</dd>
        </div>
        <div>
          <dt>开始时间</dt>
          <dd>{{ formatStoreDateTime(result.reservedStartAt) }}</dd>
        </div>
        <div>
          <dt>结束时间</dt>
          <dd>{{ formatStoreDateTime(result.reservedEndAt) }}</dd>
        </div>
        <div>
          <dt>保留到</dt>
          <dd>{{ formatStoreDateTime(result.holdUntilAt) }}</dd>
        </div>
        <div>
          <dt>营业日期</dt>
          <dd>{{ result.businessDate }}</dd>
        </div>
        <div>
          <dt>客户信息</dt>
          <dd>
            {{ customerDisplay }}
            <span v-if="result.customer?.phoneE164"> {{ result.customer.phoneE164 }}</span>
          </dd>
        </div>
        <div>
          <dt>事件</dt>
          <dd>{{ result.events.join(', ') }}</dd>
        </div>
        <div>
          <dt>幂等状态</dt>
          <dd>
            {{ result.idempotency.status }}
            <span v-if="result.idempotency.replayed">重放：true</span>
          </dd>
        </div>
      </dl>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>创建失败</h2>
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
  max-width: 640px;
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
.confirmed-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.confirmed-note {
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

.reservation-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.reservation-form {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.primary-grid {
  display: grid;
  gap: 12px;
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

.party-size-field,
.time-field {
  border-radius: 8px;
  padding: 12px;
}

.party-size-field {
  background: #eaf2ff;
  border: 1px solid #b8cdf6;
}

.time-field {
  background: #eef6f1;
  border: 1px solid #b8d8c4;
}

.party-size-field input {
  background: #ffffff;
  font-weight: 800;
}

.party-size-field input {
  font-size: 1.7rem;
  min-height: 56px;
}

.compact-picker {
  background: #fbfcfe;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
}

.toggle-field {
  align-items: center;
  display: flex;
  gap: 10px;
}

.toggle-field input {
  accent-color: #176b4d;
  flex: 0 0 auto;
  min-height: 18px;
  width: 18px;
}

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

@media (min-width: 720px) {
  .page-shell {
    padding-top: 36px;
  }

  h1 {
    font-size: 2rem;
  }

  .primary-grid {
    grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  }
}
</style>
