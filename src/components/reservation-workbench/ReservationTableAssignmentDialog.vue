<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  assignReservationTable,
  getAssignableReservationTables,
  ReservationTableAssignmentApiError
} from '../../api/reservationTableAssignmentApi'
import type {
  AssignableReservationTable,
  ReservationTableAssignmentApiErrorResponse,
  ReservationTableAssignmentResponse
} from '../../types/reservationTableAssignment'
import type { ReservationTodayViewItem } from '../../types/reservationTodayView'

const conflictCodes = new Set([
  'TABLE_NOT_AVAILABLE',
  'TABLE_CAPACITY_INSUFFICIENT',
  'RESERVATION_ALREADY_ASSIGNED',
  'RESERVATION_NOT_ASSIGNABLE'
])

const props = defineProps<{
  open: boolean
  storeId: string
  item: ReservationTodayViewItem | null
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  assigned: [response: ReservationTableAssignmentResponse]
}>()

const { t } = useI18n()
const tables = ref<AssignableReservationTable[]>([])
const selectedTableId = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false)
const apiError = ref<ReservationTableAssignmentApiErrorResponse | null>(null)
let loadSequence = 0

const customerLabel = computed(() => {
  const item = props.item
  if (!item) {
    return t('reservationWorkbench.tableAssignment.currentReservation')
  }
  const values = [item.customerName, item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : item.reservationCode
})
const selectedTable = computed(() =>
  tables.value.find(table => table.tableId === selectedTableId.value) ?? null
)
const groupedTables = computed(() => {
  const groups = new Map<string, AssignableReservationTable[]>()
  for (const table of tables.value) {
    const area = table.areaName?.trim() || t('reservationWorkbench.tableAssignment.unassignedArea')
    groups.set(area, [...(groups.get(area) ?? []), table])
  }
  return [...groups.entries()].map(([areaName, areaTables]) => ({ areaName, tables: areaTables }))
})
const canSubmit = computed(() =>
  props.open && !!props.storeId && !!props.item && !!selectedTable.value && !isLoading.value && !isSubmitting.value
)
const errorMessage = computed(() => {
  const code = apiError.value?.error.code
  if (!code) {
    return ''
  }
  if (conflictCodes.has(code)) {
    return t('reservationWorkbench.tableAssignment.conflict')
  }
  if (code === 'FORBIDDEN' || code === 'STORE_SCOPE_MISMATCH') {
    return t('reservationWorkbench.tableAssignment.forbidden')
  }
  return t('reservationWorkbench.tableAssignment.loadOrSubmitFailed')
})

watch(
  () => [props.open, props.storeId, props.item?.reservationId] as const,
  ([open]) => {
    if (!open) {
      selectedTableId.value = ''
      tables.value = []
      apiError.value = null
      return
    }
    void loadTables(false)
  },
  { immediate: true }
)

function close(): void {
  if (!isSubmitting.value) {
    emit('update:open', false)
  }
}

function selectTable(tableId: string): void {
  selectedTableId.value = tableId
  apiError.value = null
}

async function refreshTables(): Promise<void> {
  await loadTables(true)
}

async function loadTables(preserveSelection: boolean): Promise<void> {
  const storeId = props.storeId
  const reservationId = props.item?.reservationId
  const sequence = ++loadSequence
  if (!storeId || !reservationId) {
    tables.value = []
    return
  }

  const previousSelection = preserveSelection ? selectedTableId.value : ''
  isLoading.value = true
  apiError.value = null
  try {
    const result = await getAssignableReservationTables(storeId, reservationId)
    if (sequence !== loadSequence) {
      return
    }
    tables.value = result.tables
    selectedTableId.value = result.tables.some(table => table.tableId === previousSelection)
      ? previousSelection
      : ''
  } catch (error) {
    if (sequence !== loadSequence) {
      return
    }
    tables.value = []
    selectedTableId.value = ''
    apiError.value = error instanceof ReservationTableAssignmentApiError
      ? error.response
      : localError('REQUEST_FAILED', 'reservation.table_assignment.request_failed')
  } finally {
    if (sequence === loadSequence) {
      isLoading.value = false
    }
  }
}

async function submit(): Promise<void> {
  const item = props.item
  const table = selectedTable.value
  if (!canSubmit.value || !item || !table) {
    return
  }

  isSubmitting.value = true
  apiError.value = null
  try {
    const result = await assignReservationTable(
      props.storeId,
      item.reservationId,
      table.tableId,
      createIdempotencyKey(item.reservationId)
    )
    emit('assigned', result)
    emit('update:open', false)
  } catch (error) {
    const response = error instanceof ReservationTableAssignmentApiError
      ? error.response
      : localError('REQUEST_FAILED', 'reservation.table_assignment.request_failed')
    if (conflictCodes.has(response.error.code)) {
      await loadTables(true)
    }
    apiError.value = response
  } finally {
    isSubmitting.value = false
  }
}

function createIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `reservation:table-assignment:${reservationId}:${randomValue}`
}

function localError(code: string, messageKey: string): ReservationTableAssignmentApiErrorResponse {
  return {
    success: false,
    error: { code, messageKey, details: {} }
  }
}
</script>

<template>
  <Teleport to="body">
    <section
      v-if="open"
      class="reservation-table-assignment-dialog"
      :aria-label="$t('reservationWorkbench.tableAssignment.aria')"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-table-assignment-dialog__backdrop" @click="close"></div>

      <form class="reservation-table-assignment-dialog__panel" @submit.prevent="submit">
        <header>
          <div>
            <h2>{{ $t('reservationWorkbench.tableAssignment.title') }}</h2>
            <p>{{ $t('reservationWorkbench.tableAssignment.subject', { customer: customerLabel, count: item?.partySize ?? 0 }) }}</p>
          </div>
          <button
            class="reservation-table-assignment-dialog__close"
            type="button"
            :aria-label="$t('reservationWorkbench.tableAssignment.close')"
            :disabled="isSubmitting"
            @click="close"
          >
            ×
          </button>
        </header>

        <p class="reservation-table-assignment-dialog__instruction">
          {{ $t('reservationWorkbench.tableAssignment.instruction') }}
        </p>

        <section v-if="isLoading" class="reservation-table-assignment-dialog__state" aria-live="polite">
          <strong>{{ $t('reservationWorkbench.tableAssignment.loading') }}</strong>
        </section>

        <section
          v-else-if="!tables.length && !apiError"
          class="reservation-table-assignment-dialog__state"
          aria-live="polite"
        >
          <strong>{{ $t('reservationWorkbench.tableAssignment.empty') }}</strong>
          <span>{{ $t('reservationWorkbench.tableAssignment.emptyHint') }}</span>
        </section>

        <div v-else-if="tables.length" class="reservation-table-assignment-dialog__groups">
          <section v-for="group in groupedTables" :key="group.areaName" class="reservation-table-assignment-dialog__group">
            <h3>{{ group.areaName }}</h3>
            <div role="radiogroup" :aria-label="group.areaName">
              <button
                v-for="table in group.tables"
                :key="table.tableId"
                :aria-checked="selectedTableId === table.tableId"
                :class="{ selected: selectedTableId === table.tableId }"
                role="radio"
                type="button"
                @click="selectTable(table.tableId)"
              >
                <strong>{{ table.tableCode }}</strong>
                <span>{{ table.displayName }}</span>
                <small>{{ $t('reservationWorkbench.tableAssignment.capacity', { min: table.capacityMin, max: table.capacityMax }) }}</small>
              </button>
            </div>
          </section>
        </div>

        <section v-if="apiError" class="reservation-table-assignment-dialog__error" aria-live="assertive">
          <strong>{{ errorMessage }}</strong>
          <span>{{ $t('reservationWorkbench.tableAssignment.errorCode', { code: apiError.error.code }) }}</span>
          <button type="button" :disabled="isLoading || isSubmitting" @click="refreshTables">
            {{ $t('reservationWorkbench.tableAssignment.refresh') }}
          </button>
        </section>

        <footer>
          <span v-if="selectedTable">
            {{ $t('reservationWorkbench.tableAssignment.selected', { code: selectedTable.tableCode }) }}
          </span>
          <span v-else>{{ $t('reservationWorkbench.tableAssignment.selectPrompt') }}</span>
          <div>
            <button type="button" :disabled="isSubmitting" @click="close">
              {{ $t('common.actions.cancel') }}
            </button>
            <button class="primary" type="submit" :disabled="!canSubmit">
              {{ isSubmitting ? $t('reservationWorkbench.tableAssignment.submitting') : $t('reservationWorkbench.tableAssignment.confirm') }}
            </button>
          </div>
        </footer>
      </form>
    </section>
  </Teleport>
</template>

<style scoped>
.reservation-table-assignment-dialog {
  inset: 0;
  position: fixed;
  z-index: 1200;
}

.reservation-table-assignment-dialog__backdrop {
  background: rgba(15, 23, 42, 0.58);
  inset: 0;
  position: absolute;
}

.reservation-table-assignment-dialog__panel {
  background: #ffffff;
  border-radius: 14px 14px 0 0;
  bottom: 0;
  box-shadow: 0 -18px 48px rgba(15, 23, 42, 0.2);
  display: grid;
  gap: 14px;
  left: 50%;
  max-height: min(86vh, 720px);
  max-width: 680px;
  overflow: auto;
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
  position: absolute;
  transform: translateX(-50%);
  width: min(100%, 680px);
}

.reservation-table-assignment-dialog__panel header,
.reservation-table-assignment-dialog__panel footer,
.reservation-table-assignment-dialog__panel footer div {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.reservation-table-assignment-dialog__panel h2,
.reservation-table-assignment-dialog__panel h3,
.reservation-table-assignment-dialog__panel p {
  margin: 0;
}

.reservation-table-assignment-dialog__panel h2 {
  color: #14213d;
  font-size: 1.15rem;
}

.reservation-table-assignment-dialog__panel header p,
.reservation-table-assignment-dialog__instruction,
.reservation-table-assignment-dialog__panel footer > span {
  color: #64748b;
  font-size: 0.82rem;
  font-weight: 750;
}

.reservation-table-assignment-dialog__close {
  border: 0;
  border-radius: 999px;
  font-size: 1.4rem;
  height: 34px;
  width: 34px;
}

.reservation-table-assignment-dialog__groups {
  display: grid;
  gap: 14px;
}

.reservation-table-assignment-dialog__group {
  display: grid;
  gap: 8px;
}

.reservation-table-assignment-dialog__group h3 {
  color: #475569;
  font-size: 0.86rem;
}

.reservation-table-assignment-dialog__group > div {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
}

.reservation-table-assignment-dialog__group button {
  align-items: flex-start;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  color: #334155;
  display: grid;
  gap: 3px;
  min-height: 82px;
  padding: 10px;
  text-align: left;
}

.reservation-table-assignment-dialog__group button.selected {
  background: #fff7ed;
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

.reservation-table-assignment-dialog__group button span,
.reservation-table-assignment-dialog__group button small {
  overflow-wrap: anywhere;
}

.reservation-table-assignment-dialog__state,
.reservation-table-assignment-dialog__error {
  border-radius: 10px;
  display: grid;
  gap: 6px;
  padding: 14px;
}

.reservation-table-assignment-dialog__state {
  background: #f8fafc;
  color: #475569;
}

.reservation-table-assignment-dialog__error {
  background: #fff1f2;
  color: #be123c;
}

.reservation-table-assignment-dialog__error button {
  justify-self: start;
}

.reservation-table-assignment-dialog__panel button {
  cursor: pointer;
  font-weight: 850;
}

.reservation-table-assignment-dialog__panel footer {
  border-top: 1px solid #e2e8f0;
  flex-wrap: wrap;
  padding-top: 14px;
}

.reservation-table-assignment-dialog__panel footer button,
.reservation-table-assignment-dialog__error button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  min-height: 38px;
  padding: 0 14px;
}

.reservation-table-assignment-dialog__panel footer button.primary {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.reservation-table-assignment-dialog__panel button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.reservation-table-assignment-dialog__panel button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (min-width: 700px) {
  .reservation-table-assignment-dialog__panel {
    border-radius: 14px;
    bottom: auto;
    top: 50%;
    transform: translate(-50%, -50%);
  }
}
</style>
