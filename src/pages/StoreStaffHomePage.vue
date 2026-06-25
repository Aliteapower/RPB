<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { fetchMeApps } from '../api/meAppsApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeActionGroup from '../components/staff-home/StaffHomeActionGroup.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import StaffHomeWorkflowStrip from '../components/staff-home/StaffHomeWorkflowStrip.vue'
import type { StaffHomeActionItem } from '../components/staff-home/staffHomeActions'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useStoreContextStore } from '../stores/storeContext'
import type { MeAppEntry } from '../types/meApps'

const route = useRoute()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const apps = ref<MeAppEntry[]>([])
const appsLoading = ref(false)
const appsLoadFailed = ref(false)
let loadSequence = 0

const reservationQueueEntry = computed(() =>
  apps.value.find(app => app.appKey === 'reservation_queue' && app.entryVisible)
)
const hasReservationQueue = computed(() => !!reservationQueueEntry.value)
const canCreateReservation = computed(() => hasPermission('reservation.create'))
const canViewTodayReservations = computed(() =>
  hasPermission('reservation.today_view')
)
const canCheckInReservation = computed(() =>
  hasPermission('reservation.check_in')
)
const canQueueArrivedReservation = computed(() =>
  hasPermission('reservation.queue')
)
const canViewQueueTickets = computed(() =>
  hasPermission('queue.view')
)
const canCallQueueTicket = computed(() =>
  hasPermission('queue.call')
)
const canSeatCalledQueueTicket = computed(() =>
  hasPermission('queue.seat')
)
const canSeatArrivedReservation = computed(() =>
  hasPermission('reservation.seat')
)
const canSeatWalkInDirectly = computed(() =>
  hasPermission('walkin.direct_seating.create')
)
const canCreateWalkInQueue = computed(() =>
  hasPermission('walkin.queue.create')
)
const canHandleCleaning = computed(() =>
  hasPermission('cleaning.start') && hasPermission('cleaning.complete')
)
const canViewTables = computed(() =>
  hasPermission('table.view')
)
const hasReceptionOperations = computed(
  () => canCreateWalkInQueue.value || canSeatWalkInDirectly.value || canCheckInReservation.value
)
const hasReservationOperations = computed(
  () =>
    canCreateReservation.value ||
    canViewTodayReservations.value ||
    canQueueArrivedReservation.value ||
    canSeatArrivedReservation.value
)
const hasQueueOperations = computed(
  () =>
    canViewQueueTickets.value ||
    canCallQueueTicket.value ||
    canSeatCalledQueueTicket.value
)
const hasTableTurnoverOperations = computed(() => canHandleCleaning.value || canViewTables.value)
const hasVisibleOperation = computed(
  () =>
    hasReceptionOperations.value ||
    hasReservationOperations.value ||
    hasQueueOperations.value ||
    hasTableTurnoverOperations.value
)
const appStatusTone = computed(() => {
  if (appsLoading.value) {
    return 'loading'
  }

  if (appsLoadFailed.value) {
    return 'error'
  }

  if (!hasReservationQueue.value) {
    return 'empty'
  }

  if (!hasVisibleOperation.value) {
    return 'limited'
  }

  return 'ready'
})
const appStatusLabel = computed(() => {
  if (appsLoading.value) {
    return '检查中'
  }

  if (appsLoadFailed.value) {
    return '暂不可用'
  }

  if (!hasReservationQueue.value) {
    return '暂无应用'
  }

  if (!hasVisibleOperation.value) {
    return '无入口'
  }

  return '首页'
})
const appStatusTitle = computed(() => {
  if (appsLoading.value) {
    return '应用检查中'
  }

  if (appsLoadFailed.value) {
    return '应用暂不可用'
  }

  if (!hasReservationQueue.value) {
    return '暂无可见应用'
  }

  if (!hasVisibleOperation.value) {
    return '当前权限无可用入口'
  }

  return '可用入口已按权限展示'
})
const appStatusDetail = computed(() => {
  if (appsLoading.value) {
    return '正在读取当前门店 App Gate。'
  }

  if (appsLoadFailed.value) {
    return '请稍后重试或联系管理员检查应用权限。'
  }

  if (!hasReservationQueue.value) {
    return '当前门店没有可见的 reservation_queue 应用入口。'
  }

  if (!hasVisibleOperation.value) {
    return '应用可见，但当前账号没有已开放的操作权限。'
  }

  return '入口不会展示未授权业务操作。'
})
const walkInRoute = computed(() => ({
  name: 'walk-in-direct-seating',
  params: {
    storeId: storeId.value
  }
}))
const walkInQueueRoute = computed(() => ({
  name: 'walk-in-queue',
  params: {
    storeId: storeId.value
  }
}))
const cleaningRoute = computed(() => ({
  name: 'cleaning-complete',
  params: {
    storeId: storeId.value
  }
}))
const tableResourceListRoute = computed(() => ({
  name: 'table-resource-list',
  params: {
    storeId: storeId.value
  }
}))
const reservationRoute = computed(() => ({
  name: 'reservation-create',
  params: {
    storeId: storeId.value
  }
}))
const reservationTodayViewRoute = computed(() => ({
  name: 'reservation-today-view',
  params: {
    storeId: storeId.value
  }
}))
const reservationConfirmedTodayRoute = computed(() => ({
  name: 'reservation-today-view',
  params: {
    storeId: storeId.value
  },
  query: {
    status: 'confirmed'
  }
}))
const reservationArrivedToQueueRoute = computed(() => ({
  name: 'reservation-arrived-to-queue',
  params: {
    storeId: storeId.value
  }
}))
const queueTicketListRoute = computed(() => ({
  name: 'queue-ticket-list',
  params: {
    storeId: storeId.value
  }
}))
const reservationArrivedTodayRoute = computed(() => ({
  name: 'reservation-today-view',
  params: {
    storeId: storeId.value
  },
  query: {
    status: 'arrived'
  }
}))
const receptionActions = computed<StaffHomeActionItem[]>(() => compactActions([
  canCreateWalkInQueue.value
    ? {
        id: 'walkin-queue',
        label: '现场取号',
        description: '现场客人取号加入排队',
        symbol: '号',
        to: walkInQueueRoute.value,
        tone: 'queue',
        emphasis: true
      }
    : null,
  canSeatWalkInDirectly.value
    ? {
        id: 'walkin-direct-seating',
        label: '散客直接入座',
        description: '现场客人不排队直接安排桌台',
        symbol: '入',
        to: walkInRoute.value,
        tone: 'primary',
        emphasis: true
      }
    : null,
  canCheckInReservation.value
    ? {
        id: 'reservation-confirmed-today',
        label: '预约到店',
        description: '从今日预约确认客人到店',
        symbol: '到',
        to: reservationConfirmedTodayRoute.value,
        tone: 'primary',
        emphasis: true
      }
    : null
]))
const reservationActions = computed<StaffHomeActionItem[]>(() => compactActions([
  canCreateReservation.value
    ? {
        id: 'reservation-create',
        label: '创建预约',
        description: '登记新的门店预约',
        symbol: '约',
        to: reservationRoute.value,
        tone: 'reservation'
      }
    : null,
  canViewTodayReservations.value
    ? {
        id: 'reservation-today-view',
        label: '今日预约',
        description: '查看当前营业日预约',
        symbol: '今',
        to: reservationTodayViewRoute.value,
        tone: 'reservation'
      }
    : null,
  canQueueArrivedReservation.value
    ? {
        id: 'reservation-arrived-to-queue',
        label: '预约排队',
        description: '将到店预约加入排队',
        symbol: '排',
        to: reservationArrivedToQueueRoute.value,
        tone: 'reservation'
      }
    : null,
  canSeatArrivedReservation.value
    ? {
        id: 'reservation-arrived-today-seating',
        label: '预约入座',
        description: '从已到店预约选择桌台',
        symbol: '座',
        to: reservationArrivedTodayRoute.value,
        tone: 'success'
      }
    : null
]))
const queueActions = computed<StaffHomeActionItem[]>(() => compactActions([
  canViewQueueTickets.value
    ? {
        id: 'queue-ticket-list',
        label: '排队列表',
        description: '查看当前排队票状态',
        symbol: '列',
        to: queueTicketListRoute.value,
        tone: 'queue',
        emphasis: true
      }
    : null,
  canCallQueueTicket.value
    ? {
        id: 'queue-call',
        label: '排队叫号',
        description: '从列表选择排队票一键叫号',
        symbol: '叫',
        to: queueTicketListRoute.value,
        tone: 'queue',
        emphasis: true
      }
    : null,
  canSeatCalledQueueTicket.value
    ? {
        id: 'seating-from-called-queue',
        label: '排队入座',
        description: '从已叫号票直接安排桌台',
        symbol: '座',
        to: queueTicketListRoute.value,
        tone: 'success'
      }
    : null
]))
const tableTurnoverActions = computed<StaffHomeActionItem[]>(() => compactActions([
  canViewTables.value
    ? {
        id: 'table-resource-list',
        label: '桌台列表',
        description: '查看后台配置的桌号及分组',
        symbol: '桌',
        to: tableResourceListRoute.value,
        tone: 'support'
      }
    : null,
  canHandleCleaning.value
    ? {
        id: 'cleaning-complete',
        label: '清台处理',
        description: '处理已离店桌台清洁',
        symbol: '清',
        to: cleaningRoute.value,
        tone: 'support'
      }
    : null
]))

watch(
  storeId,
  async nextStoreId => {
    const sequence = ++loadSequence
    apps.value = []
    appsLoadFailed.value = false

    if (!nextStoreId) {
      appsLoading.value = false
      return
    }

    appsLoading.value = true

    try {
      const response = await fetchMeApps(nextStoreId)

      if (sequence === loadSequence) {
        apps.value = response.apps
      }
    } catch {
      if (sequence === loadSequence) {
        appsLoadFailed.value = true
      }
    } finally {
      if (sequence === loadSequence) {
        appsLoading.value = false
      }
    }
  },
  { immediate: true }
)

function compactActions(actions: Array<StaffHomeActionItem | null>): StaffHomeActionItem[] {
  return actions.filter((action): action is StaffHomeActionItem => action !== null)
}

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return '默认门店'
  }

  return `门店 ${value.slice(0, 8)}`
}

function hasPermission(permission: string): boolean {
  return reservationQueueEntry.value?.permissions.includes(permission) ?? false
}
</script>

<template>
  <main class="staff-workbench-shell staff-shell">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="workbench-body">
      <StaffHomeWorkflowStrip />

      <section
        class="app-state"
        :class="`tone-${appStatusTone}`"
        aria-label="应用可用状态"
      >
        <div>
          <p>{{ appStatusTitle }}</p>
          <strong>{{ appStatusDetail }}</strong>
        </div>
      </section>

      <nav v-if="hasVisibleOperation" class="operation-groups" aria-label="门店员工操作">
        <StaffHomeActionGroup
          group-id="staff-section-reception"
          heading="接待"
          :layout="'three'"
          :actions="receptionActions"
        />
        <StaffHomeActionGroup
          group-id="staff-section-reservation"
          heading="预约管理"
          :actions="reservationActions"
        />
        <StaffHomeActionGroup
          group-id="staff-section-queue"
          heading="排队管理"
          :actions="queueActions"
        />
        <StaffHomeActionGroup
          group-id="staff-section-table-turnover"
          heading="桌台流转"
          :actions="tableTurnoverActions"
        />
      </nav>

      <section
        v-else-if="hasReservationQueue && !appsLoading"
        class="empty-state"
        aria-label="当前权限无可用入口"
      >
        <p>当前权限无可用入口</p>
        <strong>入口会按 App Gate 权限自动显示。</strong>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="home" />
  </main>
</template>

<style scoped>
.workbench-body {
  display: grid;
  gap: 16px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.app-state {
  align-items: center;
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-left: 4px solid #94a3b8;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
  display: flex;
  min-height: 64px;
  padding: 12px 14px;
}

.app-state p,
.empty-state p {
  color: #0f172a;
  font-size: 0.92rem;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1.25;
  margin: 0;
}

.app-state strong,
.empty-state strong {
  color: #64748b;
  display: block;
  font-size: 0.82rem;
  font-weight: 800;
  line-height: 1.35;
  margin-top: 4px;
}

.tone-ready {
  border-left-color: #22c55e;
}

.tone-loading,
.tone-limited {
  border-left-color: #f97316;
}

.tone-empty {
  border-left-color: #64748b;
}

.tone-error {
  border-left-color: #ef4444;
}

.operation-groups {
  display: grid;
  gap: 18px;
}

.empty-state {
  background: #ffffff;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  padding: 18px 14px;
}

@media (min-width: 720px) {
  .workbench-body {
    padding-top: 16px;
  }
}
</style>
