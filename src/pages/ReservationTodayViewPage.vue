<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

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
import ReservationQuickActionPanel from '../components/reservation-workbench/ReservationQuickActionPanel.vue'
import ReservationSeatDialog from '../components/reservation-workbench/ReservationSeatDialog.vue'
import ReservationTodayListPanel from '../components/reservation-workbench/ReservationTodayListPanel.vue'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffBusinessDateSwitcher from '../components/staff/StaffBusinessDateSwitcher.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
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
const router = useRouter()
const storeContext = useStoreContextStore()
const { currentTimeText } = useCurrentClock()

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
const selectedSeatReservation = ref<ReservationTodayViewItem | null>(null)
const checkingInReservationId = ref<string | null>(null)
const visibleMonthKey = ref(monthKeyFromDate(businessDate.value))
const reservationCounts = ref<Record<string, number>>({})
let loadSequence = 0
let appsLoadSequence = 0
let calendarSummaryLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isLoading.value ? '加载中' : '今日预约'))
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
const canNoShowReservation = computed(
  () => reservationQueueEntry.value?.permissions.includes('reservation.no_show') ?? false
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
  () => route.query.create,
  create => {
    if (isOpenCreateQuery(create)) {
      openCreateReservationDialog()
    }
  },
  { immediate: true }
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

function handleReservationNoShowed(): void {
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

  const queueTicketId = item.queueTicketId?.trim()

  if (queueTicketId) {
    if (item.queueTicketStatus?.trim() !== 'called') {
      return
    }

    void router.push({
      name: 'seating-from-called-queue',
      params: {
        storeId: storeId.value
      },
      query: {
        queueTicketId
      }
    })
    return
  }

  selectedSeatReservation.value = item
  showSeatDialog.value = true
}

function handleReservationSeated(): void {
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

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return '默认门店'
  }

  return `门店 ${value.slice(0, 8)}`
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

function isOpenCreateQuery(value: unknown): boolean {
  const candidate = Array.isArray(value) ? value[0] : value
  return candidate === '1' || candidate === 'true'
}
</script>

<template>
  <main class="staff-workbench-shell reservation-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="displayedBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <button type="button" :disabled="isLoading" @click="refreshReservationWorkbench">
          {{ isLoading ? '刷新中' : '刷新' }}
        </button>
      </template>
    </StaffHomeTopBar>

    <div class="reservation-workbench-body">
      <StaffBusinessDateSwitcher
        v-model:selected-date="businessDate"
        :today-date="storeTodayDate"
        :reservation-counts="reservationCounts"
        calendar-label="预约日历"
        @visible-month-changed="handleVisibleMonthChanged"
      />

      <ReservationQuickActionPanel
        :store-id="storeId"
        :can-create-reservation-for-selected-date="canCreateReservationForSelectedDate"
        :selected-date="businessDate"
        @open-create-reservation="openCreateReservationDialog"
      />

      <ReservationTodayListPanel
        v-model:selected-status="selectedStatus"
        :api-error="apiError"
        :can-cancel-reservation="canCancelReservation"
        :can-no-show-reservation="canNoShowReservation"
        :can-run-current-day-actions="canRunCurrentDayActions"
        :checking-in-reservation-id="checkingInReservationId"
        :is-loading="isLoading"
        :items="items"
        :seating-reservation-id="selectedSeatReservation?.reservationId ?? null"
        :show-empty-state="showEmptyState"
        :status-options="statusOptions"
        :store-id="storeId"
        :store-timezone="storeTimezone"
        @cancelled="handleReservationCancelled"
        @check-in-requested="handleReservationCheckIn"
        @no-showed="handleReservationNoShowed"
        @seat-requested="openReservationSeatDialog"
      />

      <section v-if="checkInApiError" class="reservation-workbench__action-error" aria-live="assertive">
        <h2>操作失败</h2>
        <p>错误代码：{{ checkInApiError.error.code }}</p>
        <p>消息键：{{ checkInApiError.error.messageKey }}</p>
      </section>
    </div>

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

    <StaffBottomNav :store-id="storeId" active-tab="reservation" />
  </main>
</template>

<style scoped>
.reservation-workbench-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(128px + env(safe-area-inset-bottom));
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
