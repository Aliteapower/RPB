<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getReservationTodayView,
  ReservationTodayViewApiError
} from '../api/reservationTodayViewApi'
import {
  checkInReservation,
  ReservationCheckInApiError
} from '../api/reservationCheckInApi'
import {
  queueArrivedReservation,
  ReservationArrivedToQueueApiError
} from '../api/reservationArrivedToQueueApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ReservationTodayViewApiErrorResponse,
  ReservationTodayViewItem,
  ReservationTodayViewResponse
} from '../types/reservationTodayView'
import type { ReservationCheckInApiErrorResponse } from '../types/reservationCheckIn'
import type {
  QueueArrivedReservationRequest,
  ReservationArrivedToQueueApiErrorResponse
} from '../types/reservationArrivedToQueue'

const route = useRoute()
const router = useRouter()
const storeContext = useStoreContextStore()
const { currentTimeText } = useCurrentClock()

const businessDate = ref(queryBusinessDate(route.query.businessDate) || todayDateInput())
const isLoading = ref(false)
const response = ref<ReservationTodayViewResponse | null>(null)
const listApiError = ref<ReservationTodayViewApiErrorResponse | null>(null)
const queueApiError = ref<ReservationArrivedToQueueApiErrorResponse | ReservationCheckInApiErrorResponse | null>(null)
const submittingReservationId = ref<string | null>(null)
let loadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const displayedBusinessDate = computed(() => response.value?.businessDate || businessDate.value)
const queueCandidateReservations = computed(() =>
  (response.value?.items ?? []).filter(item => ['confirmed', 'arrived'].includes(item.status))
)
const showEmptyState = computed(
  () => !isLoading.value && !listApiError.value && queueCandidateReservations.value.length === 0
)
const appStatusLabel = computed(() =>
  submittingReservationId.value ? '取号中' : '预约排队'
)
const queueTicketListRoute = computed(() => ({
  name: 'queue-ticket-list',
  params: {
    storeId: storeId.value || ''
  }
}))
const reservationTodayViewRoute = computed(() => ({
  name: 'reservation-today-view',
  params: {
    storeId: storeId.value || ''
  },
  query: {
    businessDate: displayedBusinessDate.value
  }
}))
const walkInQueueRoute = computed(() => ({
  name: 'walk-in-queue',
  params: {
    storeId: storeId.value || ''
  }
}))

watch(
  [storeId, businessDate],
  () => {
    void loadArrivedReservations()
  },
  { immediate: true }
)

watch(
  () => route.query.businessDate,
  value => {
    const nextBusinessDate = queryBusinessDate(value)

    if (nextBusinessDate && nextBusinessDate !== businessDate.value) {
      businessDate.value = nextBusinessDate
    }
  }
)

async function loadArrivedReservations(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++loadSequence
  listApiError.value = null
  queueApiError.value = null

  if (!currentStoreId) {
    response.value = null
    isLoading.value = false
    return
  }

  isLoading.value = true

  try {
    const result = await getReservationTodayView(currentStoreId, {
      businessDate: businessDate.value,
      status: 'operational'
    })

    if (sequence === loadSequence) {
      response.value = result
    }
  } catch (error) {
    if (sequence === loadSequence) {
      response.value = null
      listApiError.value =
        error instanceof ReservationTodayViewApiError
          ? error.response
          : createTodayViewError('REQUEST_FAILED', 'reservation.today_view.request_failed')
    }
  } finally {
    if (sequence === loadSequence) {
      isLoading.value = false
    }
  }
}

async function queueReservation(item: ReservationTodayViewItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canQueueReservation(item) || submittingReservationId.value) {
    return
  }

  queueApiError.value = null
  submittingReservationId.value = item.reservationId

  try {
    if (shouldCheckInBeforeQueue(item)) {
      await checkInReservation(
        currentStoreId,
        item.reservationId,
        {
          arrivedAt: null,
          reasonCode: 'staff_queue_check_in',
          note: 'reservation_queue_one_tap'
        },
        createReservationCheckInIdempotencyKey(item.reservationId)
      )
    }

    await queueArrivedReservation(
      currentStoreId,
      item.reservationId,
      toRequest(),
      createIdempotencyKey(item.reservationId)
    )
    await router.push(queueTicketListRoute.value)
  } catch (error) {
    if (error instanceof ReservationCheckInApiError) {
      queueApiError.value = error.response
    } else {
      queueApiError.value =
        error instanceof ReservationArrivedToQueueApiError
          ? error.response
          : createQueueError('UNKNOWN_ERROR', 'reservation.queue.unknown_error')
    }
  } finally {
    submittingReservationId.value = null
  }
}

function canQueueReservation(item: ReservationTodayViewItem): boolean {
  return ['confirmed', 'arrived'].includes(item.status) && !item.queueTicketId
}

function shouldCheckInBeforeQueue(item: ReservationTodayViewItem): boolean {
  return item.status === 'confirmed'
}

function actionLabel(item: ReservationTodayViewItem): string {
  if (submittingReservationId.value === item.reservationId) {
    return '进入中...'
  }

  if (item.queueTicketId) {
    return '已在排队线'
  }

  return shouldCheckInBeforeQueue(item) ? '到店取号' : '进入排队线'
}

function toRequest(): QueueArrivedReservationRequest {
  return {}
}

function createIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:queue:${reservationId}:${randomValue}`
}

function createReservationCheckInIdempotencyKey(reservationId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `reservation:check-in:${reservationId}:${randomValue}`
}

function createTodayViewError(code: string, messageKey: string): ReservationTodayViewApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createQueueError(code: string, messageKey: string): ReservationArrivedToQueueApiErrorResponse {
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

function queryBusinessDate(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value
  return typeof candidate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(candidate)
    ? candidate
    : ''
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

function formatTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Singapore',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('hour')}:${part('minute')}`
}

function customerLabel(item: ReservationTodayViewItem): string {
  return item.customerName?.trim() || item.customerNickname?.trim() || '未留姓名'
}

function queueHint(item: ReservationTodayViewItem): string {
  if (!item.queueTicketId) {
    return shouldCheckInBeforeQueue(item) ? '待到店，可一键取号' : '已到店，可进排队线'
  }

  const ticket = item.queueTicketDisplayNumber?.trim()
    ? `#${item.queueTicketDisplayNumber.trim()}`
    : item.queueTicketNumber
      ? `#${item.queueTicketNumber}`
      : '已取号'
  return `${ticket} · ${item.queueTicketStatus || '排队中'}`
}
</script>

<template>
  <main class="staff-workbench-shell reservation-queue-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="displayedBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="reservation-queue-body">
      <section class="queue-heading">
        <div>
          <p>预约</p>
          <h1>预约排队</h1>
        </div>
      </section>

      <section class="queue-list-panel" aria-label="可取号预约">
        <header>
          <div>
            <p>可取号预约</p>
            <h2>到店排队</h2>
          </div>
          <button type="button" :disabled="isLoading" @click="loadArrivedReservations">刷新</button>
        </header>

        <div v-if="isLoading" class="queue-state">
          加载中...
        </div>

        <section v-else-if="listApiError" class="result-panel error-panel" aria-live="assertive">
          <h2>加载失败</h2>
          <p class="error-code">错误代码：{{ listApiError.error.code }}</p>
          <p class="message-key">消息键：{{ listApiError.error.messageKey }}</p>
        </section>

        <div v-else-if="showEmptyState" class="queue-state queue-state--actions">
          <strong>暂无可取号预约</strong>
          <span>可以先去今日预约确认到店，或给散客现场取号。</span>
          <div class="empty-actions">
            <RouterLink class="empty-action-link" :to="reservationTodayViewRoute">去今日预约</RouterLink>
            <RouterLink class="empty-action-link primary" :to="walkInQueueRoute">现场取号</RouterLink>
          </div>
        </div>

        <article
          v-for="item in queueCandidateReservations"
          v-else
          :key="item.reservationId"
          class="reservation-card"
          :class="{ queued: !!item.queueTicketId }"
        >
          <div class="reservation-card__main">
            <strong>{{ item.reservationCode }}</strong>
            <span>{{ customerLabel(item) }}</span>
          </div>

          <dl>
            <div>
              <dt>时间</dt>
              <dd>{{ formatTime(item.reservedStartAt) }} - {{ formatTime(item.reservedEndAt) }}</dd>
            </div>
            <div>
              <dt>人数</dt>
              <dd>{{ item.partySize }}人</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ queueHint(item) }}</dd>
            </div>
          </dl>

          <button
            class="queue-action-button"
            :disabled="!canQueueReservation(item) || !!submittingReservationId"
            type="button"
            @click="queueReservation(item)"
          >
            {{ actionLabel(item) }}
          </button>
        </article>
      </section>

      <section v-if="queueApiError" class="result-panel error-panel" aria-live="assertive">
        <h2>排队失败</h2>
        <p class="error-code">错误代码：{{ queueApiError.error.code }}</p>
        <p class="message-key">消息键：{{ queueApiError.error.messageKey }}</p>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="reservation" />
  </main>
</template>

<style scoped>
.reservation-queue-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.queue-heading,
.queue-list-panel,
.result-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
}

.queue-heading {
  align-items: start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding: 14px;
}

.queue-heading p,
.queue-list-panel header p,
dt {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 0;
}

h1,
h2 {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.24rem;
  line-height: 1.15;
}

h2 {
  font-size: 0.94rem;
}

.queue-list-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.queue-list-panel header {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.queue-list-panel header button {
  background: #fff7ed;
  border: 1px solid #fdba74;
  border-radius: 999px;
  color: #c2410c;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 12px;
}

.queue-state {
  align-items: center;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  color: #475569;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-weight: 800;
  justify-content: center;
  min-height: 110px;
  text-align: center;
}

.queue-state--actions {
  padding: 16px;
}

.queue-state--actions strong {
  color: #0f172a;
}

.queue-state--actions span {
  font-size: 0.86rem;
  font-weight: 700;
}

.empty-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.empty-action-link {
  align-items: center;
  background: #ffffff;
  border: 1px solid #fdba74;
  border-radius: 10px;
  color: #c2410c;
  display: inline-flex;
  font-weight: 950;
  justify-content: center;
  min-height: 42px;
  padding: 0 12px;
  text-decoration: none;
}

.empty-action-link.primary {
  background: #f97316;
  color: #ffffff;
}

.reservation-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.reservation-card.queued {
  background: #f8fafc;
}

.reservation-card__main {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.reservation-card__main strong {
  color: #f97316;
  font-size: 1rem;
  font-weight: 950;
}

.reservation-card__main span {
  color: #0f172a;
  font-weight: 900;
}

dl {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
}

dt,
dd {
  margin: 0;
}

dd {
  color: #0f172a;
  font-size: 0.86rem;
  font-weight: 850;
  overflow-wrap: anywhere;
}

.queue-action-button {
  align-items: center;
  background: #176b4d;
  border: 0;
  border-radius: 10px;
  color: #ffffff;
  display: inline-flex;
  font-weight: 950;
  justify-content: center;
  min-height: 46px;
  padding: 0 16px;
}

button:disabled {
  background: #cbd5e1;
  border-color: #cbd5e1;
  color: #64748b;
  cursor: not-allowed;
}

.result-panel {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.error-panel {
  border-color: #fecaca;
}

.error-code {
  color: #b42318;
  font-weight: 900;
  margin: 0;
}

.message-key {
  color: #475569;
  margin: 0;
  overflow-wrap: anywhere;
}

button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (max-width: 430px) {
  dl {
    grid-template-columns: 1fr;
  }
}
</style>
