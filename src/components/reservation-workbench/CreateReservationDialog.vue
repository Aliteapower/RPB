<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import { createReservation, ReservationCreateApiError } from '../../api/reservationCreateApi'
import { formatReservationCreateErrorMessage } from '../../utils/reservationCreateMessages'
import StaffGuestContactLookup from '../staff/StaffGuestContactLookup.vue'
import StaffTimeWheelPicker from '../staff/StaffTimeWheelPicker.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../staff/staffGuestContact'
import {
  defaultFutureReservationDateTime,
  isReservationStartInPast
} from './reservationCreateTime'
import type {
  CreateReservationRequest,
  CreateReservationResponse,
  ReservationApiErrorResponse
} from '../../types/reservation'

const props = defineProps<{
  open: boolean
  storeId: string
  selectedDate: string
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  created: [response: CreateReservationResponse]
}>()

const form = reactive({
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  businessDate: props.selectedDate,
  time: '',
  partySize: 2,
  tablePreference: 'unassigned'
})

const isSubmitting = ref(false)
const apiError = ref<ReservationApiErrorResponse | null>(null)

const canSubmit = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !isSubmitting.value &&
    !!form.businessDate &&
    !!form.time &&
    Number.isInteger(form.partySize) &&
    form.partySize > 0
)

watch(
  () => props.open,
  open => {
    if (open) {
      apiError.value = null
      applyDefaultFutureDateTime(props.selectedDate)
    }
  }
)

watch(
  () => props.selectedDate,
  selectedDate => {
    if (props.open) {
      applyDefaultFutureDateTime(selectedDate)
    }
  }
)

async function submit(): Promise<void> {
  apiError.value = validateForm()
  if (apiError.value || !canSubmit.value) {
    return
  }

  isSubmitting.value = true

  try {
    const result = await createReservation(props.storeId, toRequest(), createIdempotencyKey())
    emit('created', result)
    emit('update:open', false)
    resetAfterSuccess()
  } catch (error) {
    apiError.value =
      error instanceof ReservationCreateApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'reservation.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function close(): void {
  if (!isSubmitting.value) {
    emit('update:open', false)
  }
}

function validateForm(): ReservationApiErrorResponse | null {
  if (!Number.isInteger(form.partySize) || form.partySize <= 0) {
    return createLocalError('INVALID_PARTY_SIZE', 'reservation.invalid_party_size')
  }

  if (!toIsoInstant()) {
    return createLocalError('INVALID_TIME_RANGE', 'reservation.invalid_time_range')
  }

  if (isReservationStartInPast(form.businessDate, form.time)) {
    return createLocalError('RESERVATION_START_IN_PAST', 'reservation.start_in_past')
  }

  const phone = form.phoneLocal.trim()
  if (phone && !isValidSingaporeLocalPhone(phone)) {
    return createLocalError('INVALID_PHONE_E164', 'reservation.invalid_phone_e164')
  }

  return null
}

function toRequest(): CreateReservationRequest {
  return {
    partySize: form.partySize,
    reservedStartAt: toIsoInstant() ?? '',
    reservedEndAt: null,
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerSalutation),
    phoneE164: toSingaporePhoneE164(form.phoneLocal)
  }
}

function toIsoInstant(): string | null {
  if (!form.businessDate || !form.time) {
    return null
  }

  const timestamp = new Date(`${form.businessDate}T${form.time}`).getTime()
  return Number.isNaN(timestamp) ? null : new Date(timestamp).toISOString()
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
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

function resetAfterSuccess(): void {
  form.customerId = ''
  form.customerName = ''
  form.customerSalutation = ''
  form.phoneLocal = ''
  form.partySize = 2
  form.tablePreference = 'unassigned'
}

function applyDefaultFutureDateTime(selectedDate: string): void {
  const next = defaultFutureReservationDateTime(selectedDate)
  form.businessDate = next.businessDate
  form.time = next.time
}
</script>

<template>
  <Teleport to="body">
    <section
      v-if="open"
      class="reservation-create-dialog"
      aria-label="新增预约弹窗"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-create-dialog__backdrop" @click="close"></div>

      <form class="reservation-create-dialog__panel" @submit.prevent="submit">
        <header>
          <h2>新增预约</h2>
          <button type="button" aria-label="关闭新增预约" :disabled="isSubmitting" @click="close">
            ×
          </button>
        </header>

        <StaffGuestContactLookup
          :store-id="storeId"
          v-model:customer-id="form.customerId"
          v-model:customer-name="form.customerName"
          v-model:salutation="form.customerSalutation"
          v-model:phone-local="form.phoneLocal"
          :disabled="isSubmitting"
        />

        <label>
          <span>日期</span>
          <input v-model="form.businessDate" name="businessDate" type="date" />
        </label>

        <StaffTimeWheelPicker v-model="form.time" label="时间" name="reservationTime" />

        <label>
          <span>人数</span>
          <input v-model.number="form.partySize" min="1" name="partySize" type="number" />
        </label>

        <label>
          <span>桌号（可选）</span>
          <select v-model="form.tablePreference" name="tablePreference">
            <option value="unassigned">未指定</option>
          </select>
        </label>

        <section v-if="apiError" class="reservation-create-dialog__error" aria-live="assertive">
          {{ formatReservationCreateErrorMessage(apiError.error.messageKey) }}
        </section>

        <footer>
          <button class="reservation-create-dialog__save" :disabled="!canSubmit" type="submit">
            {{ isSubmitting ? '保存中...' : '保存' }}
          </button>
          <button class="reservation-create-dialog__cancel" type="button" :disabled="isSubmitting" @click="close">
            取消
          </button>
        </footer>
      </form>
    </section>
  </Teleport>
</template>

<style scoped>
.reservation-create-dialog {
  align-items: center;
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 18px;
  position: fixed;
  z-index: 80;
}

.reservation-create-dialog__backdrop {
  backdrop-filter: blur(4px);
  background: rgba(15, 23, 42, 0.46);
  inset: 0;
  position: absolute;
}

.reservation-create-dialog__panel {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
  display: grid;
  gap: 11px;
  max-height: min(92dvh, 720px);
  max-width: 382px;
  overflow-y: auto;
  padding: 24px 20px 22px;
  position: relative;
  width: min(100%, 382px);
}

.reservation-create-dialog__panel header {
  align-items: center;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-create-dialog__panel h2 {
  color: #14213d;
  font-size: 1.15rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-create-dialog__panel h2::before {
  content: '▦';
  color: #5b7cff;
  font-size: 1rem;
  margin-right: 8px;
}

.reservation-create-dialog__panel header button {
  background: transparent;
  border: 0;
  color: #94a3b8;
  font-size: 1.55rem;
  font-weight: 800;
  height: 32px;
  line-height: 1;
  padding: 0;
  width: 32px;
}

.reservation-create-dialog__panel label {
  color: #0f172a;
  display: grid;
  font-size: 0.82rem;
  font-weight: 900;
  gap: 6px;
}

.reservation-create-dialog__panel input,
.reservation-create-dialog__panel select {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  min-height: 36px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.reservation-create-dialog__panel input:focus,
.reservation-create-dialog__panel select:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.reservation-create-dialog__error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  color: #be123c;
  font-size: 0.82rem;
  font-weight: 800;
  padding: 9px 11px;
}

.reservation-create-dialog__panel footer {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 8px;
}

.reservation-create-dialog__save,
.reservation-create-dialog__cancel {
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 950;
  min-height: 40px;
  padding: 0 16px;
}

.reservation-create-dialog__save {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-create-dialog__save:disabled {
  background: #fdba74;
  border-color: #fdba74;
}

.reservation-create-dialog__cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.reservation-create-dialog__panel button:focus-visible,
.reservation-create-dialog__panel input:focus-visible,
.reservation-create-dialog__panel select:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
