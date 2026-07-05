<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  ReservationArrivedDirectSeatingApiError,
  seatArrivedReservation
} from '../api/reservationArrivedDirectSeatingApi'
import TableResourcePicker from '../components/staff-table/TableResourcePicker.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ReservationArrivedDirectSeatingApiErrorResponse,
  SeatArrivedReservationRequest,
  SeatArrivedReservationResponse
} from '../types/reservationArrivedDirectSeating'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  reservationId: '',
  tableId: '',
  tableGroupId: '',
  temporaryTableIds: [] as string[],
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
const hasTemporaryTables = computed(() => form.temporaryTableIds.length > 0)
const selectedResourceCount = computed(
  () => Number(hasTableId.value) + Number(hasTableGroupId.value) + Number(hasTemporaryTables.value)
)
const hasValidTemporaryTables = computed(
  () => !hasTemporaryTables.value || form.temporaryTableIds.length >= 2
)
const hasExactlyOneResource = computed(
  () => selectedResourceCount.value === 1 && hasValidTemporaryTables.value
)
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
      return gt('generated.reservation-arrived-direct-seating.037')
    case 'RESOURCE_SELECTION_CONFLICT':
      return gt('generated.reservation-arrived-direct-seating.038')
    case 'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED':
      return gt('generated.reservation-arrived-direct-seating.039')
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

  if (selectedResourceCount.value === 0) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'reservation.resource_selection_required')
  }

  if (selectedResourceCount.value > 1) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'reservation.resource_selection_conflict')
  }

  if (form.temporaryTableIds.length === 1) {
    return createLocalError(
      'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED',
      'reservation.temporary_table_group_member_required'
    )
  }

  return null
}

function selectTable(tableId: string): void {
  form.tableId = tableId
  form.tableGroupId = ''
  form.temporaryTableIds = []
}

function selectTableGroup(tableGroupId: string): void {
  form.tableGroupId = tableGroupId
  form.tableId = ''
  form.temporaryTableIds = []
}

function selectTemporaryTables(tableIds: string[]): void {
  form.temporaryTableIds = tableIds
  form.tableId = ''
  form.tableGroupId = ''
}

function toRequest(): SeatArrivedReservationRequest {
  return {
    tableId: optionalValue(form.tableId),
    tableGroupId: optionalValue(form.tableGroupId),
    temporaryTableIds: form.temporaryTableIds.length ? form.temporaryTableIds : null,
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
      <p class="eyebrow">{{ gt('generated.reservation-arrived-direct-seating.001') }}</p>
      <h1>{{ gt('generated.reservation-arrived-direct-seating.002') }}</h1>
      <p class="store-context">{{ gt('generated.reservation-arrived-direct-seating.003') }} {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">{{ gt('generated.reservation-arrived-direct-seating.004') }}</RouterLink>
    </section>

    <form class="direct-seating-form" @submit.prevent="submitDirectSeating">
      <label class="reservation-id-field">
        <span>{{ gt('generated.reservation-arrived-direct-seating.005') }}</span>
        <input
          v-model="form.reservationId"
          autocomplete="off"
          name="reservationId"
          required
          type="text"
        />
      </label>

      <section class="resource-panel" :aria-label="gt('generated.reservation-arrived-direct-seating.006')">
        <p class="resource-rule">{{ gt('generated.reservation-arrived-direct-seating.007') }}</p>
        <TableResourcePicker
          :store-id="storeId"
          :available-only="true"
          :selected-table-id="form.tableId"
          :selected-table-group-id="form.tableGroupId"
          :selected-temporary-table-ids="form.temporaryTableIds"
          temporary-selection-enabled
          @select-table="selectTable"
          @select-table-group="selectTableGroup"
          @select-temporary-tables="selectTemporaryTables"
        />
        <details class="field-group">
          <summary>{{ gt('generated.reservation-arrived-direct-seating.008') }}</summary>
          <label>
            <span>{{ gt('generated.reservation-arrived-direct-seating.009') }}</span>
            <input v-model="form.tableId" autocomplete="off" name="tableId" type="text" />
          </label>
          <label>
            <span>{{ gt('generated.reservation-arrived-direct-seating.010') }}</span>
            <input v-model="form.tableGroupId" autocomplete="off" name="tableGroupId" type="text" />
          </label>
        </details>
      </section>

      <details class="field-group">
        <summary>{{ gt('generated.reservation-arrived-direct-seating.011') }}</summary>
        <label>
          <span>{{ gt('generated.reservation-arrived-direct-seating.012') }}</span>
          <input v-model="form.overrideReasonCode" name="overrideReasonCode" type="text" />
        </label>
        <label>
          <span>{{ gt('generated.reservation-arrived-direct-seating.013') }}</span>
          <textarea v-model="form.overrideNote" name="overrideNote" rows="3" />
        </label>
      </details>

      <details class="field-group">
        <summary>{{ gt('generated.reservation-arrived-direct-seating.014') }}</summary>
        <label>
          <span>{{ gt('generated.reservation-arrived-direct-seating.015') }}</span>
          <textarea v-model="form.note" name="note" rows="3" />
        </label>
      </details>

      <button class="submit-button" :disabled="!canSubmit" type="submit">
        {{ isSubmitting ? gt('generated.reservation-arrived-direct-seating.016') : gt('generated.reservation-arrived-direct-seating.017') }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>{{ gt('generated.reservation-arrived-direct-seating.018') }}</h2>
      <div class="reservation-highlight status-highlight">
        <span>{{ gt('generated.reservation-arrived-direct-seating.019') }}</span>
        <strong>{{ result.reservationStatus }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>{{ gt('generated.reservation-arrived-direct-seating.020') }}</span>
        <strong>{{ result.reservationCode }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>{{ gt('generated.reservation-arrived-direct-seating.021') }}</span>
        <strong>{{ result.seatingId }}</strong>
      </div>
      <div class="reservation-highlight">
        <span>{{ gt('generated.reservation-arrived-direct-seating.022') }}</span>
        <strong>{{ result.resourceType }} {{ result.resourceId }}</strong>
      </div>
      <div class="reservation-highlight already-seated-highlight">
        <span>{{ gt('generated.reservation-arrived-direct-seating.023') }}</span>
        <strong>{{ result.alreadySeated }}</strong>
      </div>
      <p v-if="seatedStatus" class="seated-note">{{ gt('generated.reservation-arrived-direct-seating.024') }}</p>
      <p v-if="result.alreadySeated" class="seated-note">{{ gt('generated.reservation-arrived-direct-seating.025') }}</p>
      <dl>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.026') }}</dt>
          <dd>{{ result.reservationId }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.027') }}</dt>
          <dd>{{ result.seatingStatus }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.028') }}</dt>
          <dd>{{ result.resourceType }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.029') }}</dt>
          <dd>{{ result.resourceId }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.030') }}</dt>
          <dd>{{ eventsDisplay }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.031') }}</dt>
          <dd>{{ result.idempotency.status }}</dd>
        </div>
        <div>
          <dt>{{ gt('generated.reservation-arrived-direct-seating.032') }}</dt>
          <dd>{{ result.idempotency.replayed ?? false }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>{{ gt('generated.reservation-arrived-direct-seating.033') }}</h2>
      <p v-if="directSeatingErrorHint" class="message-key">{{ directSeatingErrorHint }}</p>
      <p class="error-code">{{ gt('generated.reservation-arrived-direct-seating.034') }}{{ apiError.error.code }}</p>
      <p class="message-key">{{ gt('generated.reservation-arrived-direct-seating.035') }}{{ apiError.error.messageKey }}</p>
    </section>

    <p v-if="lastIdempotencyKey" class="idempotency-key"> {{ gt('generated.reservation-arrived-direct-seating.036') }} {{ lastIdempotencyKey }}
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
