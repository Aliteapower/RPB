<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  switchTable,
  TableSwitchApiError
} from '../../api/tableSwitchApi'
import type {
  SwitchTableRequest,
  SwitchTableResponse,
  TableSwitchApiErrorResponse
} from '../../types/tableSwitch'
import TableResourcePicker from '../staff-table/TableResourcePicker.vue'

interface TableSwitchDialogItem {
  reservationCode?: string | null
  customerName?: string | null
  customerNickname?: string | null
  partySize: number
  businessDate?: string | null
  seatingId?: string | null
  currentResourceCode?: string | null
}

const props = defineProps<{
  open: boolean
  item: TableSwitchDialogItem | null
  storeId: string
  businessDate?: string | null
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  switched: [response: SwitchTableResponse]
}>()

const { t } = useI18n()
const selectedTableId = ref('')
const selectedTableGroupId = ref('')
const isSubmitting = ref(false)
const apiError = ref<TableSwitchApiErrorResponse | null>(null)

const customerLabel = computed(() => {
  const item = props.item

  if (!item) {
    return t('reservationWorkbench.dialogs.currentReservation')
  }

  const values = [item.customerName, item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : item.reservationCode
})
const currentResourceLabel = computed(() =>
  props.item?.currentResourceCode
    ? t('reservationWorkbench.dialogs.currentTable', { code: props.item.currentResourceCode })
    : t('reservationWorkbench.dialogs.unknownTable')
)
const pickerBusinessDate = computed(() => props.businessDate || props.item?.businessDate || null)
const canSubmit = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !!props.item?.seatingId &&
    !isSubmitting.value &&
    hasExactlyOneResource()
)

watch(
  () => [props.open, props.item?.seatingId] as const,
  () => {
    selectedTableId.value = ''
    selectedTableGroupId.value = ''
    apiError.value = null
  }
)

function close(): void {
  if (!isSubmitting.value) {
    emit('update:open', false)
  }
}

function selectTable(tableId: string): void {
  selectedTableId.value = tableId
  selectedTableGroupId.value = ''
}

function selectTableGroup(tableGroupId: string): void {
  selectedTableGroupId.value = tableGroupId
  selectedTableId.value = ''
}

async function submit(): Promise<void> {
  apiError.value = validateForm()

  if (apiError.value || !props.item?.seatingId || !props.storeId) {
    return
  }

  isSubmitting.value = true

  try {
    const result = await switchTable(
      props.storeId,
      props.item.seatingId,
      toRequest(),
      createTableSwitchIdempotencyKey(props.item.seatingId)
    )
    emit('switched', result)
    emit('update:open', false)
  } catch (error) {
    apiError.value =
      error instanceof TableSwitchApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'table_switch.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): TableSwitchApiErrorResponse | null {
  if (!props.storeId) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'reservation.store_scope_mismatch')
  }

  if (!props.item?.seatingId) {
    return createLocalError('SEATING_NOT_FOUND', 'table_switch.seating_required')
  }

  if (!hasExactlyOneResource()) {
    return createLocalError('TABLE_SWITCH_TARGET_INVALID', 'table_switch.target_required')
  }

  return null
}

function hasExactlyOneResource(): boolean {
  return !!selectedTableId.value.trim() !== !!selectedTableGroupId.value.trim()
}

function toRequest(): SwitchTableRequest {
  return {
    tableId: optionalValue(selectedTableId.value),
    tableGroupId: optionalValue(selectedTableGroupId.value),
    reasonCode: 'staff_requested',
    note: 'staff_table_page_switch_table'
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createTableSwitchIdempotencyKey(seatingId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `table:switch:${seatingId}:${randomValue}`
}

function createLocalError(code: string, messageKey: string): TableSwitchApiErrorResponse {
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
  <Teleport to="body">
    <section
      v-if="open"
      class="reservation-table-switch-dialog"
      :aria-label="$t('reservationWorkbench.dialogs.switchAria')"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-table-switch-dialog__backdrop" @click="close"></div>

      <form class="reservation-table-switch-dialog__panel" @submit.prevent="submit">
        <header>
          <h2>{{ $t('reservationWorkbench.dialogs.switchTitle') }}</h2>
          <button
            type="button"
            :aria-label="$t('reservationWorkbench.dialogs.switchClose')"
            :disabled="isSubmitting"
            @click="close"
          >
            ×
          </button>
        </header>

        <p class="reservation-table-switch-dialog__summary">
          {{ $t('reservationWorkbench.dialogs.switchSubject', { customer: customerLabel }) }}
        </p>
        <p class="reservation-table-switch-dialog__current">{{ currentResourceLabel }}</p>

        <TableResourcePicker
          :store-id="storeId"
          :party-size="null"
          :business-date="pickerBusinessDate"
          :available-only="true"
          :selected-table-id="selectedTableId"
          :selected-table-group-id="selectedTableGroupId"
          @select-table="selectTable"
          @select-table-group="selectTableGroup"
        />

        <section v-if="apiError" class="reservation-table-switch-dialog__error" aria-live="assertive">
          <h3>{{ $t('reservationWorkbench.dialogs.switchFailed') }}</h3>
          <p>{{ $t('reservationWorkbench.dialogs.errorCode', { code: apiError.error.code }) }}</p>
          <p>{{ $t('reservationWorkbench.dialogs.messageKey', { messageKey: apiError.error.messageKey }) }}</p>
        </section>

        <footer>
          <button class="reservation-table-switch-dialog__save" :disabled="!canSubmit" type="submit">
            {{ isSubmitting ? $t('reservationWorkbench.dialogs.switchSubmitting') : $t('reservationWorkbench.dialogs.confirmSwitch') }}
          </button>
          <button class="reservation-table-switch-dialog__cancel" type="button" :disabled="isSubmitting" @click="close">
            {{ $t('common.actions.cancel') }}
          </button>
        </footer>
      </form>
    </section>
  </Teleport>
</template>

<style scoped>
.reservation-table-switch-dialog {
  align-items: center;
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 18px;
  position: fixed;
  z-index: 84;
}

.reservation-table-switch-dialog__backdrop {
  backdrop-filter: blur(4px);
  background: rgba(15, 23, 42, 0.46);
  inset: 0;
  position: absolute;
}

.reservation-table-switch-dialog__panel {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
  display: grid;
  gap: 12px;
  max-height: min(92dvh, 760px);
  max-width: 420px;
  overflow-y: auto;
  padding: 24px 20px 22px;
  position: relative;
  width: min(100%, 420px);
}

.reservation-table-switch-dialog__panel header {
  align-items: center;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-table-switch-dialog__panel h2 {
  color: #14213d;
  font-size: 1.15rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-table-switch-dialog__panel h2::before {
  color: #8b5e5e;
  content: '▦';
  font-size: 1rem;
  margin-right: 8px;
}

.reservation-table-switch-dialog__panel header button {
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

.reservation-table-switch-dialog__summary,
.reservation-table-switch-dialog__current {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  margin: 0;
}

.reservation-table-switch-dialog__current {
  color: #64748b;
}

.reservation-table-switch-dialog__error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  display: grid;
  gap: 5px;
  padding: 9px 11px;
}

.reservation-table-switch-dialog__error h3,
.reservation-table-switch-dialog__error p {
  color: #be123c;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-table-switch-dialog__panel footer {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 4px;
}

.reservation-table-switch-dialog__save,
.reservation-table-switch-dialog__cancel {
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 950;
  min-height: 40px;
  padding: 0 16px;
}

.reservation-table-switch-dialog__save {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-table-switch-dialog__save:disabled {
  background: #fdba74;
  border-color: #fdba74;
}

.reservation-table-switch-dialog__cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.reservation-table-switch-dialog__panel button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
