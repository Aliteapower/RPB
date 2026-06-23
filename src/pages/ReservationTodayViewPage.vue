<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { fetchMeApps } from '../api/meAppsApi'
import {
  getReservationTodayView,
  ReservationTodayViewApiError
} from '../api/reservationTodayViewApi'
import CreateReservationDialog from '../components/reservation-workbench/CreateReservationDialog.vue'
import ReservationMonthCalendar from '../components/reservation-workbench/ReservationMonthCalendar.vue'
import ReservationQuickActionPanel from '../components/reservation-workbench/ReservationQuickActionPanel.vue'
import ReservationTodayListPanel from '../components/reservation-workbench/ReservationTodayListPanel.vue'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ReservationTodayViewApiErrorResponse,
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
  { value: 'confirmed', label: '已确认' },
  { value: 'arrived', label: '已到店' },
  { value: 'seated', label: '已入座' },
  { value: 'cancelled', label: '已取消' },
  { value: 'no_show', label: '爽约' },
  { value: 'completed', label: '已完成' }
]

const businessDate = ref(todayDateInput())
const selectedStatus = ref<ReservationTodayViewStatusFilter>('operational')
const isLoading = ref(false)
const response = ref<ReservationTodayViewResponse | null>(null)
const apiError = ref<ReservationTodayViewApiErrorResponse | null>(null)
const apps = ref<MeAppEntry[]>([])
const showCreateReservationDialog = ref(false)
let loadSequence = 0
let appsLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const items = computed(() => response.value?.items ?? [])
const displayedBusinessDate = computed(() => response.value?.businessDate || businessDate.value || '后端默认')
const storeTimezone = computed(() => response.value?.storeTimezone || 'Asia/Singapore')
const showEmptyState = computed(
  () => !isLoading.value && !apiError.value && !!response.value && items.value.length === 0
)
const markedReservationDates = computed(() =>
  response.value?.businessDate && items.value.length > 0 ? [response.value.businessDate] : []
)
const reservationQueueEntry = computed(() =>
  apps.value.find(app => app.appKey === 'reservation_queue' && app.entryVisible)
)
const canCancelReservation = computed(
  () => reservationQueueEntry.value?.permissions.includes('reservation.cancel') ?? false
)

watch(
  [storeId, businessDate, selectedStatus],
  () => {
    void loadTodayView()
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

function openCreateReservationDialog(): void {
  showCreateReservationDialog.value = true
}

function handleReservationCreated(result: CreateReservationResponse): void {
  const shouldReload =
    businessDate.value === result.businessDate && selectedStatus.value === 'operational'

  businessDate.value = result.businessDate
  selectedStatus.value = 'operational'

  if (shouldReload) {
    void loadTodayView()
  }
}

function handleReservationCancelled(): void {
  void loadTodayView()
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

function todayDateInput(): string {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
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
      <button type="button" :disabled="isLoading" @click="loadTodayView">刷新</button>
    </section>

    <ReservationQuickActionPanel
      :store-id="storeId"
      :selected-date="businessDate"
      @open-create-reservation="openCreateReservationDialog"
    />

    <ReservationMonthCalendar
      v-model:selected-date="businessDate"
      :marked-dates="markedReservationDates"
    />

    <ReservationTodayListPanel
      v-model:selected-status="selectedStatus"
      :api-error="apiError"
      :can-cancel-reservation="canCancelReservation"
      :is-loading="isLoading"
      :items="items"
      :show-empty-state="showEmptyState"
      :status-options="statusOptions"
      :store-id="storeId"
      :store-timezone="storeTimezone"
      @cancelled="handleReservationCancelled"
    />

    <CreateReservationDialog
      v-model:open="showCreateReservationDialog"
      :selected-date="businessDate"
      :store-id="storeId"
      @created="handleReservationCreated"
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

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

</style>
