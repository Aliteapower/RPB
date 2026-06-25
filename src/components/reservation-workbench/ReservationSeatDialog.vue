<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  ReservationArrivedDirectSeatingApiError,
  seatArrivedReservation
} from '../../api/reservationArrivedDirectSeatingApi'
import TableResourcePicker from '../staff-table/TableResourcePicker.vue'
import type {
  ReservationArrivedDirectSeatingApiErrorResponse,
  SeatArrivedReservationRequest,
  SeatArrivedReservationResponse
} from '../../types/reservationArrivedDirectSeating'
import type { ReservationTodayViewItem } from '../../types/reservationTodayView'

const props = defineProps<{
  open: boolean
  item: ReservationTodayViewItem | null
  storeId: string
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  seated: [response: SeatArrivedReservationResponse]
}>()

const selectedTableId = ref('')
const selectedTableGroupId = ref('')
const selectedTemporaryTableIds = ref<string[]>([])
const isSubmitting = ref(false)
const apiError = ref<ReservationArrivedDirectSeatingApiErrorResponse | null>(null)

const customerLabel = computed(() => {
  const item = props.item

  if (!item) {
    return '当前预约'
  }

  const values = [item.customerName, item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : item.reservationCode
})
const hasAssignedResource = computed(() => !!props.item?.assignedResourceId)
const assignedResourceLabel = computed(() => {
  const item = props.item

  if (!item?.assignedResourceId) {
    return '未指定'
  }

  const code = item.assignedResourceCode?.trim() || item.assignedResourceId
  return item.assignedResourceType === 'table_group' ? `桌组 ${code}` : `桌号 ${code}`
})
const canSubmit = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !!props.item &&
    !isSubmitting.value &&
    hasExactlyOneResource()
)

watch(
  () => [props.open, props.item?.reservationId] as const,
  () => {
    if (props.item?.assignedResourceType === 'table_group' && props.item.assignedResourceId) {
      selectedTableId.value = ''
      selectedTableGroupId.value = props.item.assignedResourceId
      selectedTemporaryTableIds.value = []
    } else if (props.item?.assignedResourceType === 'dining_table' && props.item.assignedResourceId) {
      selectedTableId.value = props.item.assignedResourceId
      selectedTableGroupId.value = ''
      selectedTemporaryTableIds.value = []
    } else {
      selectedTableId.value = ''
      selectedTableGroupId.value = ''
      selectedTemporaryTableIds.value = []
    }
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
  selectedTemporaryTableIds.value = []
}

function selectTableGroup(tableGroupId: string): void {
  selectedTableGroupId.value = tableGroupId
  selectedTableId.value = ''
  selectedTemporaryTableIds.value = []
}

function selectTemporaryTables(tableIds: string[]): void {
  selectedTemporaryTableIds.value = tableIds
  selectedTableId.value = ''
  selectedTableGroupId.value = ''
}

async function submit(): Promise<void> {
  apiError.value = validateForm()

  if (apiError.value || !props.item || !props.storeId) {
    return
  }

  isSubmitting.value = true

  try {
    const result = await seatArrivedReservation(
      props.storeId,
      props.item.reservationId,
      toRequest(),
      createIdempotencyKey(props.item.reservationId)
    )
    emit('seated', result)
    emit('update:open', false)
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
  if (!props.storeId) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'reservation.store_scope_mismatch')
  }

  if (!props.item) {
    return createLocalError('INVALID_COMMAND', 'reservation.direct_seating.reservation_required')
  }

  if (resourceSelectionCount() === 0) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'reservation.resource_selection_required')
  }

  if (resourceSelectionCount() > 1) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'reservation.resource_selection_conflict')
  }

  if (selectedTemporaryTableIds.value.length === 1) {
    return createLocalError(
      'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED',
      'reservation.temporary_table_group_member_required'
    )
  }

  return null
}

function hasExactlyOneResource(): boolean {
  return resourceSelectionCount() === 1 && selectedTemporaryTableIds.value.length !== 1
}

function resourceSelectionCount(): number {
  return (
    Number(!!selectedTableId.value.trim()) +
    Number(!!selectedTableGroupId.value.trim()) +
    Number(selectedTemporaryTableIds.value.length > 0)
  )
}

function toRequest(): SeatArrivedReservationRequest {
  return {
    tableId: optionalValue(selectedTableId.value),
    tableGroupId: optionalValue(selectedTableGroupId.value),
    temporaryTableIds: selectedTemporaryTableIds.value.length ? selectedTemporaryTableIds.value : null,
    overrideReasonCode: null,
    overrideNote: null,
    note: 'staff_reservation_today_list'
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:seat:${reservationId}:${randomValue}`
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
</script>

<template>
  <Teleport to="body">
    <section
      v-if="open"
      class="reservation-seat-dialog"
      aria-label="选择桌号入桌弹窗"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-seat-dialog__backdrop" @click="close"></div>

      <form class="reservation-seat-dialog__panel" @submit.prevent="submit">
        <header>
          <h2>选择桌号（入桌）</h2>
          <button type="button" aria-label="关闭选择桌号" :disabled="isSubmitting" @click="close">
            ×
          </button>
        </header>

        <p class="reservation-seat-dialog__summary">
          为 {{ customerLabel }}（预约）分配座位
        </p>

        <section v-if="hasAssignedResource" class="reservation-seat-dialog__assigned-resource">
          <span>预约指定</span>
          <strong>{{ assignedResourceLabel }}</strong>
        </section>

        <TableResourcePicker
          v-else
          :store-id="storeId"
          :party-size="null"
          :available-only="true"
          :selected-table-id="selectedTableId"
          :selected-table-group-id="selectedTableGroupId"
          :selected-temporary-table-ids="selectedTemporaryTableIds"
          temporary-selection-enabled
          @select-table="selectTable"
          @select-table-group="selectTableGroup"
          @select-temporary-tables="selectTemporaryTables"
        />

        <section v-if="apiError" class="reservation-seat-dialog__error" aria-live="assertive">
          <h3>入桌失败</h3>
          <p>错误代码：{{ apiError.error.code }}</p>
          <p>消息键：{{ apiError.error.messageKey }}</p>
        </section>

        <footer>
          <button class="reservation-seat-dialog__save" :disabled="!canSubmit" type="submit">
            {{ isSubmitting ? '入桌中...' : '确认入桌' }}
          </button>
          <button class="reservation-seat-dialog__cancel" type="button" :disabled="isSubmitting" @click="close">
            取消
          </button>
        </footer>
      </form>
    </section>
  </Teleport>
</template>

<style scoped>
.reservation-seat-dialog {
  align-items: center;
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 18px;
  position: fixed;
  z-index: 82;
}

.reservation-seat-dialog__backdrop {
  backdrop-filter: blur(4px);
  background: rgba(15, 23, 42, 0.46);
  inset: 0;
  position: absolute;
}

.reservation-seat-dialog__panel {
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

.reservation-seat-dialog__panel header {
  align-items: center;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-seat-dialog__panel h2 {
  color: #14213d;
  font-size: 1.15rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-seat-dialog__panel h2::before {
  color: #8b5e5e;
  content: '椅';
  font-size: 1rem;
  margin-right: 8px;
}

.reservation-seat-dialog__panel header button {
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

.reservation-seat-dialog__summary {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  margin: 0;
}

.reservation-seat-dialog__assigned-resource {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  padding: 11px 12px;
}

.reservation-seat-dialog__assigned-resource span {
  color: #c2410c;
  font-size: 0.78rem;
  font-weight: 850;
}

.reservation-seat-dialog__assigned-resource strong {
  color: #14213d;
  font-size: 1rem;
  font-weight: 950;
}

.reservation-seat-dialog__error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  display: grid;
  gap: 5px;
  padding: 9px 11px;
}

.reservation-seat-dialog__error h3,
.reservation-seat-dialog__error p {
  color: #be123c;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-seat-dialog__panel footer {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 4px;
}

.reservation-seat-dialog__save,
.reservation-seat-dialog__cancel {
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 950;
  min-height: 40px;
  padding: 0 16px;
}

.reservation-seat-dialog__save {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-seat-dialog__save:disabled {
  background: #fdba74;
  border-color: #fdba74;
}

.reservation-seat-dialog__cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.reservation-seat-dialog__panel button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
