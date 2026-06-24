<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { fetchMeApps } from '../api/meAppsApi'
import {
  checkInReservation,
  ReservationCheckInApiError
} from '../api/reservationCheckInApi'
import { getReservationCalendarSummary } from '../api/reservationCalendarSummaryApi'
import {
  getReservationTodayView,
  ReservationTodayViewApiError
} from '../api/reservationTodayViewApi'
import CreateReservationDialog from '../components/reservation-workbench/CreateReservationDialog.vue'
import ReservationMonthCalendar from '../components/reservation-workbench/ReservationMonthCalendar.vue'
import ReservationQuickActionPanel from '../components/reservation-workbench/ReservationQuickActionPanel.vue'
import ReservationSeatDialog from '../components/reservation-workbench/ReservationSeatDialog.vue'
import ReservationTableSwitchDialog from '../components/reservation-workbench/ReservationTableSwitchDialog.vue'
import ReservationTodayListPanel from '../components/reservation-workbench/ReservationTodayListPanel.vue'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type { ReservationCheckInApiErrorResponse } from '../types/reservationCheckIn'
import type {
  ReservationTodayViewApiErrorResponse,
  ReservationTodayViewItem,
  ReservationTodayViewResponse,
  ReservationTodayViewStatusFilter
} from '../types/reservationTodayView'
import type { MeAppEntry } from '../types/meApps'
import type { CreateReservationResponse } from '../types/reservation'

const route = useRoute()
const storeContext = useStoreContextStore()

const statusOptions: Array<{ value: ReservationTodayViewStatusFilter; label: string }> = [
  { value: 'operational', label: '进行中' },
  { value: 'all', label: '全部' },
  { value: 'confirmed', label: '已预约' },
  { value: 'arrived', label: '已到店' },
  { value: 'seated', label: '已入座' },
  { value: 'cancelled', label: '已取消' },
  { value: 'no_show', label: '爽约' },
  { value: 'completed', label: '已完成' }
]

const businessDate = ref(todayDateInput())
const selectedStatus = ref<ReservationTodayViewStatusFilter>(
  statusFilterFromQuery(route.query.status) ?? 'operational'
)
const isLoading = ref(false)
const response = ref<ReservationTodayViewResponse | null>(null)
const apiError = ref<ReservationTodayViewApiErrorResponse | null>(null)
const checkInApiError = ref<ReservationCheckInApiErrorResponse | null>(null)
const apps = ref<MeAppEntry[]>([])
const showCreateReservationDialog = ref(false)
const showSeatDialog = ref(false)
const showTableSwitchDialog = ref(false)
const selectedSeatReservation = ref<ReservationTodayViewItem | null>(null)
const selectedSwitchTableReservation = ref<ReservationTodayViewItem | null>(null)
const checkingInReservationId = ref<string | null>(null)
const visibleMonthKey = ref(monthKeyFromDate(businessDate.value))
const reservationCounts = ref<Record<string, number>>({})
let loadSequence = 0
let appsLoadSequence = 0
let calendarSummaryLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const items = computed(() => response.value?.items ?? [])
const displayedBusinessDate = computed(() => response.value?.businessDate || businessDate.value || '后端默认')
const storeTimezone = computed(() => response.value?.storeTimezone || 'Asia/Singapore')
const storeTodayDate = computed(() => todayDateInput(storeTimezone.value))
const canCreateReservationForSelectedDate = computed(() => businessDate.value >= storeTodayDate.value)
const canRunCurrentDayActions = computed(() => businessDate.value === storeTodayDate.value)
const showEmptyState = computed(
  () => !isLoading.value && !apiError.value && !!response.value && items.value.length === 0
)
const reservationQueueEntry = computed(() =>
  apps.value.find(app => app.appKey === 'reservation_queue' && app.entryVisible)
)
const canCancelReservation = computed(
  () => reservationQueueEntry.value?.permissions.includes('reservation.cancel') ?? false
)
const canSwitchTable = computed(
  () => reservationQueueEntry.value?.permissions.includes('table.switch') ?? false
)

watch(
  [storeId, businessDate, selectedStatus],
  () => {
    void loadTodayView()
  },
  { immediate: true }
)

watch(
  businessDate,
  nextBusinessDate => {
    visibleMonthKey.value = monthKeyFromDate(nextBusinessDate)
  }
)

watch(
  () => route.query.status,
  status => {
    const nextStatus = statusFilterFromQuery(status)

    if (nextStatus && nextStatus !== selectedStatus.value) {
      selectedStatus.value = nextStatus
    }
  }
)

watch(
  showSeatDialog,
  open => {
    if (!open) {
      selectedSeatReservation.value = null
    }
  }
)

watch(
  showTableSwitchDialog,
  open => {
    if (!open) {
      selectedSwitchTableReservation.value = null
    }
  }
)

watch(
  [storeId, visibleMonthKey],
  () => {
    void loadCalendarSummary()
  },
  { immediate: true }
)

watch(
  storeId,
  async nextStoreId => {
    const sequence = ++appsLoadSequence
    apps.value = []

    if (!nextStoreId) {
      return
    }

    try {
      const result = await fetchMeApps(nextStoreId)

      if (sequence === appsLoadSequence) {
        apps.value = result.apps
      }
    } catch {
      if (sequence === appsLoadSequence) {
        apps.value = []
      }
    }
  },
  { immediate: true }
)

async function loadTodayView(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++loadSequence
  response.value = null
  apiError.value = null

  if (!currentStoreId) {
    isLoading.value = false
    return
  }

  isLoading.value = true

  try {
    const result = await getReservationTodayView(currentStoreId, {
      businessDate: businessDate.value,
      status: selectedStatus.value
    })

    if (sequence === loadSequence) {
      response.value = result
    }
  } catch (error) {
    if (sequence === loadSequence) {
      apiError.value =
        error instanceof ReservationTodayViewApiError
          ? error.response
          : createLocalError('REQUEST_FAILED', 'reservation.today_view.request_failed')
    }
  } finally {
    if (sequence === loadSequence) {
      isLoading.value = false
    }
  }
}

async function loadCalendarSummary(): Promise<void> {
  const currentStoreId = storeId.value
  const currentMonth = visibleMonthKey.value
  const sequence = ++calendarSummaryLoadSequence

  if (!currentStoreId || !currentMonth) {
    reservationCounts.value = {}
    return
  }

  try {
    const result = await getReservationCalendarSummary(currentStoreId, currentMonth)

    if (sequence === calendarSummaryLoadSequence) {
      reservationCounts.value = Object.fromEntries(
        result.days.map(day => [day.businessDate, day.reservationCount])
      )
    }
  } catch {
    if (sequence === calendarSummaryLoadSequence) {
      reservationCounts.value = {}
    }
  }
}

function refreshReservationWorkbench(): void {
  checkInApiError.value = null
  void loadTodayView()
  void loadCalendarSummary()
}

function openCreateReservationDialog(): void {
  if (!canCreateReservationForSelectedDate.value) {
    return
  }

  showCreateReservationDialog.value = true
}

function showConfirmedReservations(): void {
  selectedStatus.value = 'confirmed'
}

function showArrivedReservations(): void {
  selectedStatus.value = 'arrived'
}

function handleReservationCreated(result: CreateReservationResponse): void {
  const shouldReload =
    businessDate.value === result.businessDate && selectedStatus.value === 'operational'

  businessDate.value = result.businessDate
  visibleMonthKey.value = monthKeyFromDate(result.businessDate)
  selectedStatus.value = 'operational'

  if (shouldReload) {
    void loadTodayView()
  }
  void loadCalendarSummary()
}

function handleReservationCancelled(): void {
  void loadTodayView()
  void loadCalendarSummary()
}

async function handleReservationCheckIn(item: ReservationTodayViewItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || checkingInReservationId.value) {
    return
  }

  if (!canRunCurrentDayActions.value) {
    checkInApiError.value = createLocalCheckInError('RESERVATION_NOT_TODAY', 'reservation.not_today')
    return
  }

  checkInApiError.value = null
  checkingInReservationId.value = item.reservationId

  try {
    await checkInReservation(
      currentStoreId,
      item.reservationId,
      {
        arrivedAt: null,
        reasonCode: 'staff_confirmed_arrival',
        note: 'staff_reservation_today_list'
      },
      createReservationCheckInIdempotencyKey(item.reservationId)
    )

    if (selectedStatus.value === 'confirmed') {
      selectedStatus.value = 'arrived'
    } else {
      void loadTodayView()
    }
    void loadCalendarSummary()
  } catch (error) {
    checkInApiError.value =
      error instanceof ReservationCheckInApiError
        ? error.response
        : createLocalCheckInError('REQUEST_FAILED', 'reservation.check_in.request_failed')
  } finally {
    checkingInReservationId.value = null
  }
}

function openReservationSeatDialog(item: ReservationTodayViewItem): void {
  checkInApiError.value = null

  if (!canRunCurrentDayActions.value) {
    checkInApiError.value = createLocalCheckInError('RESERVATION_NOT_TODAY', 'reservation.not_today')
    return
  }

  selectedSeatReservation.value = item
  showSeatDialog.value = true
}

function handleReservationSeated(): void {
  void loadTodayView()
  void loadCalendarSummary()
}

function openReservationTableSwitchDialog(item: ReservationTodayViewItem): void {
  checkInApiError.value = null

  if (!canRunCurrentDayActions.value) {
    checkInApiError.value = createLocalCheckInError('RESERVATION_NOT_TODAY', 'reservation.not_today')
    return
  }

  if (!canSwitchTable.value || !item.seatingId) {
    checkInApiError.value = createLocalCheckInError('FORBIDDEN', 'reservation.forbidden')
    return
  }

  selectedSwitchTableReservation.value = item
  showTableSwitchDialog.value = true
}

function handleReservationTableSwitched(): void {
  void loadTodayView()
  void loadCalendarSummary()
}

function handleVisibleMonthChanged(month: string): void {
  visibleMonthKey.value = month
}

function createLocalError(code: string, messageKey: string): ReservationTodayViewApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createLocalCheckInError(
  code: string,
  messageKey: string
): ReservationCheckInApiErrorResponse {
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

function createReservationCheckInIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:check-in:${reservationId}:${randomValue}`
}

function todayDateInput(timeZone = 'Asia/Singapore'): string {
  const date = new Date()
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date)
  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  const year = part('year')
  const month = part('month')
  const day = part('day')
  return `${year}-${month}-${day}`
}

function monthKeyFromDate(value: string): string {
  const [year, month] = value.split('-')
  if (!year || !month) {
    return ''
  }
  return `${year}-${month}`
}

function statusFilterFromQuery(
  value: unknown
): ReservationTodayViewStatusFilter | null {
  const candidate = Array.isArray(value) ? value[0] : value

  if (typeof candidate !== 'string') {
    return null
  }

  return statusOptions.some(option => option.value === candidate)
    ? (candidate as ReservationTodayViewStatusFilter)
    : null
}
</script>

<template>
  <main class="staff-workbench-shell staff-workbench-shell--padded reservation-workbench">
    <section class="reservation-workbench__header">
      <div>
        <p>门店员工</p>
        <h1>今日预约</h1>
        <span>门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }} · {{ displayedBusinessDate }}</span>
      </div>
      <button type="button" :disabled="isLoading" @click="refreshReservationWorkbench">刷新</button>
    </section>

    <ReservationQuickActionPanel
      :store-id="storeId"
      :can-create-reservation-for-selected-date="canCreateReservationForSelectedDate"
      :selected-date="businessDate"
      @open-create-reservation="openCreateReservationDialog"
      @show-arrived-reservations="showArrivedReservations"
      @show-confirmed-reservations="showConfirmedReservations"
    />

    <ReservationMonthCalendar
      v-model:selected-date="businessDate"
      :min-date="storeTodayDate"
      :reservation-counts="reservationCounts"
      @visible-month-changed="handleVisibleMonthChanged"
    />

    <ReservationTodayListPanel
      v-model:selected-status="selectedStatus"
      :api-error="apiError"
      :can-cancel-reservation="canCancelReservation"
      :can-run-current-day-actions="canRunCurrentDayActions"
      :can-switch-table="canSwitchTable"
      :checking-in-reservation-id="checkingInReservationId"
      :is-loading="isLoading"
      :items="items"
      :seating-reservation-id="selectedSeatReservation?.reservationId ?? null"
      :show-empty-state="showEmptyState"
      :status-options="statusOptions"
      :store-id="storeId"
      :store-timezone="storeTimezone"
      :switching-reservation-id="selectedSwitchTableReservation?.reservationId ?? null"
      @cancelled="handleReservationCancelled"
      @check-in-requested="handleReservationCheckIn"
      @seat-requested="openReservationSeatDialog"
      @switch-table-requested="openReservationTableSwitchDialog"
    />

    <section v-if="checkInApiError" class="reservation-workbench__action-error" aria-live="assertive">
      <h2>操作失败</h2>
      <p>错误代码：{{ checkInApiError.error.code }}</p>
      <p>消息键：{{ checkInApiError.error.messageKey }}</p>
    </section>

    <CreateReservationDialog
      v-model:open="showCreateReservationDialog"
      :min-date="storeTodayDate"
      :selected-date="businessDate"
      :store-id="storeId"
      @created="handleReservationCreated"
    />

    <ReservationSeatDialog
      v-model:open="showSeatDialog"
      :item="selectedSeatReservation"
      :store-id="storeId"
      @seated="handleReservationSeated"
    />

    <ReservationTableSwitchDialog
      v-model:open="showTableSwitchDialog"
      :item="selectedSwitchTableReservation"
      :store-id="storeId"
      @switched="handleReservationTableSwitched"
    />

    <StaffBottomNav :store-id="storeId" active-tab="reservation" />
  </main>
</template>

<style scoped>
.reservation-workbench__header {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-workbench__header p,
.reservation-workbench__header span {
  color: #64748b;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
}

.reservation-workbench__header h1 {
  color: #14213d;
  letter-spacing: 0;
  margin: 0;
}

.reservation-workbench__header h1 {
  font-size: 1.35rem;
  line-height: 1.15;
}

.reservation-workbench__header button {
  background: #f97316;
  border: 1px solid #f97316;
  border-radius: 999px;
  color: #ffffff;
  font-size: 0.86rem;
  font-weight: 900;
  min-height: 38px;
  padding: 0 14px;
}

.reservation-workbench__header button:disabled {
  background: #cbd5e1;
  border-color: #cbd5e1;
}

.reservation-workbench__action-error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  display: grid;
  gap: 6px;
  padding: 12px;
}

.reservation-workbench__action-error h2,
.reservation-workbench__action-error p {
  color: #be123c;
  font-size: 0.86rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

</style>
