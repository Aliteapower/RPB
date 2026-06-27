<script setup lang="ts">
import { computed, ref } from 'vue'

import {
  cancelReservation,
  ReservationCancelApiError
} from '../../api/reservationCancelApi'
import {
  markReservationNoShow,
  ReservationStatusActionApiError
} from '../../api/reservationStatusActionApi'
import type {
  CancelReservationResponse,
  ReservationCancelApiErrorResponse
} from '../../types/reservationCancel'
import type {
  MarkReservationNoShowResponse,
  ReservationStatusActionApiErrorResponse
} from '../../types/reservationStatusAction'
import type {
  ReservationTodayViewApiErrorResponse,
  ReservationTodayViewItem,
  ReservationTodayViewStatusFilter
} from '../../types/reservationTodayView'
import {
  formatAppGateErrorMessage,
  formatAppGateErrorTitle
} from '../../utils/appGateErrorMessages'
import ReservationTodayListItem from './ReservationTodayListItem.vue'

const props = defineProps<{
  canCancelReservation: boolean
  canNoShowReservation: boolean
  canRunCurrentDayActions: boolean
  checkingInReservationId?: string | null
  items: ReservationTodayViewItem[]
  isLoading: boolean
  apiError: ReservationTodayViewApiErrorResponse | null
  seatingReservationId?: string | null
  showEmptyState: boolean
  selectedStatus: ReservationTodayViewStatusFilter
  statusOptions: Array<{ value: ReservationTodayViewStatusFilter; label: string }>
  storeId: string
  storeTimezone: string
}>()

const emit = defineEmits<{
  'update:selectedStatus': [value: ReservationTodayViewStatusFilter]
  cancelled: [value: CancelReservationResponse]
  'check-in-requested': [item: ReservationTodayViewItem]
  'no-showed': [value: MarkReservationNoShowResponse]
  'seat-requested': [item: ReservationTodayViewItem]
}>()

const phoneFilter = ref('')
const partySizeFilter = ref('')
const cancellingReservationId = ref<string | null>(null)
const noShowingReservationId = ref<string | null>(null)
const cancelApiError = ref<ReservationCancelApiErrorResponse | null>(null)
const statusActionApiError = ref<ReservationStatusActionApiErrorResponse | null>(null)

const partySizeOptions = computed(() =>
  [...new Set(props.items.map(item => item.partySize))]
    .filter(size => Number.isFinite(size) && size > 0)
    .sort((left, right) => left - right)
)

const visibleItems = computed(() => {
  const phoneNeedle = phoneFilter.value.trim()
  const partySize = Number(partySizeFilter.value)

  return props.items.filter(item => {
    const matchesPhone =
      !phoneNeedle ||
      [item.phoneMasked, item.customerName, item.customerNickname, item.reservationCode]
        .filter(Boolean)
        .some(value => String(value).includes(phoneNeedle))
    const matchesPartySize = !partySizeFilter.value || item.partySize === partySize
    return matchesPhone && matchesPartySize
  })
})

const showFilteredEmpty = computed(
  () =>
    !props.isLoading &&
    !props.apiError &&
    props.items.length > 0 &&
    visibleItems.value.length === 0
)

function selectStatus(value: ReservationTodayViewStatusFilter): void {
  emit('update:selectedStatus', value)
}

function resetFilters(): void {
  phoneFilter.value = ''
  partySizeFilter.value = ''
}

async function handleCancelRequested(item: ReservationTodayViewItem): Promise<void> {
  if (!props.storeId || cancellingReservationId.value) {
    return
  }

  if (!props.canCancelReservation) {
    cancelApiError.value = createLocalCancelError('FORBIDDEN', 'reservation.forbidden')
    return
  }

  cancelApiError.value = null
  statusActionApiError.value = null
  cancellingReservationId.value = item.reservationId

  try {
    const result = await cancelReservation(
      props.storeId,
      item.reservationId,
      {
        reasonCode: 'guest_requested',
        note: 'staff_reservation_today_list'
      },
      createReservationCancelIdempotencyKey(item.reservationId)
    )
    emit('cancelled', result)
  } catch (error) {
    cancelApiError.value =
      error instanceof ReservationCancelApiError
        ? error.response
        : createLocalCancelError('REQUEST_FAILED', 'reservation.cancel.api_error')
  } finally {
    cancellingReservationId.value = null
  }
}

async function handleNoShowRequested(item: ReservationTodayViewItem): Promise<void> {
  if (!props.storeId || noShowingReservationId.value) {
    return
  }

  if (!props.canNoShowReservation) {
    statusActionApiError.value = createLocalStatusActionError('FORBIDDEN', 'reservation.forbidden')
    return
  }

  statusActionApiError.value = null
  cancelApiError.value = null
  noShowingReservationId.value = item.reservationId

  try {
    const result = await markReservationNoShow(
      props.storeId,
      item.reservationId,
      {
        reasonCode: 'guest_no_show',
        note: 'staff_reservation_today_list'
      },
      createReservationStatusActionIdempotencyKey('no-show', item.reservationId)
    )
    emit('no-showed', result)
  } catch (error) {
    statusActionApiError.value =
      error instanceof ReservationStatusActionApiError
        ? error.response
        : createLocalStatusActionError('REQUEST_FAILED', 'reservation.no_show.api_error')
  } finally {
    noShowingReservationId.value = null
  }
}

function handleCheckInRequested(item: ReservationTodayViewItem): void {
  emit('check-in-requested', item)
}

function handleSeatRequested(item: ReservationTodayViewItem): void {
  emit('seat-requested', item)
}

function createReservationCancelIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:cancel:${reservationId}:${randomValue}`
}

function createReservationStatusActionIdempotencyKey(action: string, reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:${action}:${reservationId}:${randomValue}`
}

function createLocalCancelError(
  code: string,
  messageKey: string
): ReservationCancelApiErrorResponse {
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

function createLocalStatusActionError(
  code: string,
  messageKey: string
): ReservationStatusActionApiErrorResponse {
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
  <section class="reservation-panel reservation-today-list" aria-label="当日预约">
    <header class="reservation-panel__header">
      <div>
        <span></span>
        <h2>当日预约</h2>
      </div>
      <strong>共 {{ visibleItems.length }} 条</strong>
    </header>

    <section class="reservation-today-list__status-filter" aria-label="状态筛选">
      <button
        v-for="option in statusOptions"
        :key="option.value"
        :aria-pressed="selectedStatus === option.value"
        :class="{ selected: selectedStatus === option.value }"
        type="button"
        @click="selectStatus(option.value)"
      >
        {{ option.label }}
      </button>
    </section>

    <section class="reservation-today-list__filters" aria-label="预约筛选">
      <label>
        <span>手机号</span>
        <input v-model.trim="phoneFilter" placeholder="手机号" type="search" />
      </label>

      <label>
        <span>人数</span>
        <select v-model="partySizeFilter">
          <option value="">全部人数</option>
          <option v-for="size in partySizeOptions" :key="size" :value="String(size)">
            {{ size }}人
          </option>
        </select>
      </label>

      <button type="button" @click="resetFilters">重置</button>
    </section>

    <section v-if="isLoading" class="reservation-today-list__state" aria-live="polite">
      <h3>加载中...</h3>
      <p>正在读取当前门店预约。</p>
    </section>

    <section v-if="apiError" class="reservation-today-list__state reservation-today-list__state--error" aria-live="assertive">
      <h3>{{ formatAppGateErrorTitle(apiError.error, '加载失败') }}</h3>
      <p>{{ formatAppGateErrorMessage(apiError.error, '暂时无法读取当日预约，请稍后重试。') }}</p>
    </section>

    <section v-if="cancelApiError" class="reservation-today-list__state reservation-today-list__state--error" aria-live="assertive">
      <h3>{{ formatAppGateErrorTitle(cancelApiError.error, '取消失败') }}</h3>
      <p>{{ formatAppGateErrorMessage(cancelApiError.error, '暂时无法取消预约，请稍后重试。') }}</p>
    </section>

    <section v-if="statusActionApiError" class="reservation-today-list__state reservation-today-list__state--error" aria-live="assertive">
      <h3>{{ formatAppGateErrorTitle(statusActionApiError.error, '状态操作失败') }}</h3>
      <p>{{ formatAppGateErrorMessage(statusActionApiError.error, '暂时无法更新预约状态，请稍后重试。') }}</p>
    </section>

    <section v-if="showEmptyState" class="reservation-today-list__state" aria-live="polite">
      <h3>今日暂无预约</h3>
      <p>可以切换日期或状态筛选。</p>
    </section>

    <section v-if="showFilteredEmpty" class="reservation-today-list__state" aria-live="polite">
      <h3>没有匹配的预约</h3>
      <p>可以重置筛选后再查看。</p>
    </section>

    <section v-if="visibleItems.length" class="reservation-today-list__items" aria-label="今日预约列表">
      <ReservationTodayListItem
        v-for="item in visibleItems"
        :key="item.reservationId"
        :can-cancel-reservation="canCancelReservation"
        :can-no-show-reservation="canNoShowReservation"
        :can-run-current-day-actions="canRunCurrentDayActions"
        :is-cancelling="cancellingReservationId === item.reservationId"
        :is-checking-in="checkingInReservationId === item.reservationId"
        :is-no-showing="noShowingReservationId === item.reservationId"
        :is-seating="seatingReservationId === item.reservationId"
        :item="item"
        :store-timezone="storeTimezone"
        @cancel-requested="handleCancelRequested"
        @check-in-requested="handleCheckInRequested"
        @no-show-requested="handleNoShowRequested"
        @seat-requested="handleSeatRequested"
      />
    </section>
  </section>
</template>

<style scoped>
.reservation-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
  display: grid;
  gap: 12px;
  padding: 14px;
}

.reservation-panel__header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.reservation-panel__header div {
  align-items: center;
  display: flex;
  gap: 8px;
}

.reservation-panel__header div span {
  background: #f97316;
  border-radius: 999px;
  height: 8px;
  width: 8px;
}

.reservation-panel__header h2,
.reservation-today-list__state h3 {
  color: #14213d;
  font-size: 1rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-panel__header strong {
  color: #64748b;
  font-size: 0.82rem;
  font-weight: 800;
}

.reservation-today-list__status-filter {
  background: #eef2f7;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: flex;
  gap: 4px;
  margin: 0 -14px;
  overflow-x: auto;
  padding: 4px;
  scrollbar-width: none;
}

.reservation-today-list__status-filter::-webkit-scrollbar {
  display: none;
}

.reservation-today-list__status-filter button {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #315f91;
  flex: 0 0 auto;
  font-size: 0.72rem;
  font-weight: 900;
  min-height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.reservation-today-list__status-filter button.selected {
  background: #f97316;
  border-color: #f97316;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.18);
  color: #ffffff;
}

.reservation-today-list__status-filter button:not(.selected):hover {
  background: #ffffff;
  border-color: #d1dae7;
}

.reservation-today-list__filters {
  align-items: center;
  background: #f8fbff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) minmax(110px, auto) auto;
  padding: 8px;
}

.reservation-today-list__filters label {
  min-width: 0;
  position: relative;
}

.reservation-today-list__filters label span {
  height: 1px;
  overflow: hidden;
  position: absolute;
  width: 1px;
}

.reservation-today-list__filters input,
.reservation-today-list__filters select,
.reservation-today-list__filters button {
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 800;
  min-height: 32px;
}

.reservation-today-list__filters input,
.reservation-today-list__filters select {
  background: #ffffff;
  border: 1px solid #d6e0ec;
  color: #14213d;
  width: 100%;
}

.reservation-today-list__filters input {
  padding: 0 12px;
}

.reservation-today-list__filters select {
  padding: 0 28px 0 12px;
}

.reservation-today-list__filters button {
  background: #e2e8f0;
  border: 1px solid #e2e8f0;
  color: #315f91;
  padding: 0 12px;
}

.reservation-today-list__state {
  background: #f8fafc;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  padding: 12px;
}

.reservation-today-list__state p {
  color: #41516a;
  margin: 0;
}

.reservation-today-list__state--error {
  background: #fff1f2;
  border-color: #fecdd3;
}

.reservation-today-list__state--error p {
  color: #b42318;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.reservation-today-list__items {
  display: grid;
}

button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .reservation-today-list__filters {
    grid-template-columns: minmax(0, 1fr) minmax(104px, auto);
  }

  .reservation-today-list__filters button {
    grid-column: 1 / -1;
  }
}
</style>
