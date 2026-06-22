<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { fetchMeApps } from '../api/meAppsApi'
import { useStoreContextStore } from '../stores/storeContext'
import type { MeAppEntry } from '../types/meApps'

const route = useRoute()
const storeContext = useStoreContextStore()

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
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
const canHandleCleaning = computed(() =>
  hasPermission('cleaning.start') && hasPermission('cleaning.complete')
)
const hasVisibleOperation = computed(
  () =>
    canSeatWalkInDirectly.value ||
    canHandleCleaning.value ||
    canCreateReservation.value ||
    canViewTodayReservations.value ||
    canCheckInReservation.value ||
    canQueueArrivedReservation.value ||
    canViewQueueTickets.value ||
    canCallQueueTicket.value ||
    canSeatCalledQueueTicket.value ||
    canSeatArrivedReservation.value
)
const walkInRoute = computed(() => ({
  name: 'walk-in-direct-seating',
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
const reservationCheckInRoute = computed(() => ({
  name: 'reservation-check-in',
  params: {
    storeId: storeId.value
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
const queueCallRoute = computed(() => ({
  name: 'queue-call',
  params: {
    storeId: storeId.value
  }
}))
const seatingFromCalledQueueRoute = computed(() => ({
  name: 'seating-from-called-queue',
  params: {
    storeId: storeId.value
  }
}))
const reservationArrivedDirectSeatingRoute = computed(() => ({
  name: 'reservation-arrived-direct-seating',
  params: {
    storeId: storeId.value
  }
}))

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

function hasPermission(permission: string): boolean {
  return reservationQueueEntry.value?.permissions.includes(permission) ?? false
}
</script>

<template>
  <main class="staff-shell">
    <section class="staff-header">
      <p class="eyebrow">门店员工</p>
      <h1>员工工作台</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
    </section>

    <section v-if="appsLoading" class="status-panel" aria-label="应用加载状态">
      <p class="status-label">可用应用</p>
      <p class="status-flow">加载中...</p>
      <p class="status-note">正在检查当前门店权限。</p>
    </section>

    <section v-else-if="!hasReservationQueue" class="status-panel" aria-label="应用可用状态">
      <p class="status-label">可用应用</p>
      <p class="status-flow">{{ appsLoadFailed ? '暂不可用' : '暂无' }}</p>
      <p class="status-note">当前门店没有可见应用入口。</p>
    </section>

    <section v-else class="status-panel" aria-label="闭环状态">
      <p class="status-label">预约与入座</p>
      <p class="status-label">当前闭环</p>
      <p class="status-flow">散客入座 → 占用 → 清台 → 可用</p>
      <p class="status-note">创建预约只锁定时段容量，不会直接安排入座。</p>
    </section>

    <nav v-if="hasVisibleOperation" class="operation-list" aria-label="门店员工操作">
      <RouterLink v-if="canSeatWalkInDirectly" class="operation-link primary-action" :to="walkInRoute">
        <span>散客直接入座</span>
        <strong>无预约客户有空桌时直接安排入座</strong>
      </RouterLink>

      <RouterLink v-if="canHandleCleaning" class="operation-link" :to="cleaningRoute">
        <span>清台处理</span>
        <strong>开始或完成清台，释放桌台</strong>
      </RouterLink>

      <RouterLink v-if="canCreateReservation" class="operation-link reservation-action" :to="reservationRoute">
        <span>创建预约</span>
        <strong>为客户创建新的预约记录</strong>
      </RouterLink>

      <RouterLink
        v-if="canViewTodayReservations"
        class="operation-link today-view-action"
        :to="reservationTodayViewRoute"
      >
        <span>今日预约</span>
        <strong>查看当天预约并进入到店或入座操作</strong>
      </RouterLink>

      <RouterLink
        v-if="canCheckInReservation"
        class="operation-link check-in-action"
        :to="reservationCheckInRoute"
      >
        <span>预约到店</span>
        <strong>客户到店后，将预约状态标记为已到店</strong>
      </RouterLink>

      <RouterLink
        v-if="canQueueArrivedReservation"
        class="operation-link queue-action"
        :to="reservationArrivedToQueueRoute"
      >
        <span>预约排队</span>
        <strong>为已到店且暂无空桌的预约创建排队号</strong>
      </RouterLink>

      <RouterLink
        v-if="canViewQueueTickets"
        class="operation-link queue-list-action"
        :to="queueTicketListRoute"
      >
        <span>排队列表</span>
        <strong>查看等待、已叫号和已入座的排队票</strong>
      </RouterLink>

      <RouterLink
        v-if="canCallQueueTicket"
        class="operation-link queue-call-action"
        :to="queueCallRoute"
      >
        <span>排队叫号</span>
        <strong>输入排队记录 ID 并执行叫号</strong>
      </RouterLink>

      <RouterLink
        v-if="canSeatCalledQueueTicket"
        class="operation-link queue-seat-action"
        :to="seatingFromCalledQueueRoute"
      >
        <span>排队入座</span>
        <strong>输入已叫号排队票 ID 并安排桌台入座</strong>
      </RouterLink>

      <RouterLink
        v-if="canSeatArrivedReservation"
        class="operation-link seat-reservation-action"
        :to="reservationArrivedDirectSeatingRoute"
      >
        <span>预约入座</span>
        <strong>为已到店预约安排桌台并完成入座</strong>
      </RouterLink>
    </nav>

    <section v-if="hasVisibleOperation" class="handoff-notes" aria-label="操作说明">
      <h2>操作路径</h2>
      <ol>
        <li>散客有空桌时，可直接入座。</li>
        <li>客人离席后进入清台处理。</li>
        <li>完成清台后确认桌台状态为 available。</li>
        <li>创建预约只创建预约记录，不自动执行到店或入座。</li>
        <li v-if="canViewTodayReservations">今日预约用于查看和复制预约 ID，不直接改变预约状态。</li>
        <li v-if="canQueueArrivedReservation">预约排队只处理已到店且需要等待的预约。</li>
        <li v-if="canViewQueueTickets">排队列表只读展示排队票，不执行叫号或入座。</li>
        <li v-if="canCallQueueTicket">排队叫号只处理等待中的排队记录，不执行入座。</li>
        <li v-if="canSeatCalledQueueTicket">排队入座只处理已叫号的排队记录。</li>
        <li>预约入座只处理已到店的预约。</li>
      </ol>
    </section>
  </main>
</template>

<style scoped>
.staff-shell {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 560px;
  min-height: 100vh;
  padding: 20px 14px 32px;
}

.staff-header {
  display: grid;
  gap: 4px;
}

.eyebrow,
.store-context,
.status-label {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

h1,
h2,
.status-flow {
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

.status-panel,
.operation-link,
.handoff-notes {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.status-panel {
  display: grid;
  gap: 6px;
  padding: 14px;
}

.status-flow,
.status-note {
  font-size: 1rem;
  font-weight: 800;
}

.status-note {
  color: #41516a;
  font-size: 0.9rem;
  line-height: 1.35;
}

.operation-list {
  display: grid;
  gap: 12px;
}

.operation-link {
  color: #182536;
  display: grid;
  gap: 5px;
  min-height: 78px;
  padding: 14px;
  text-decoration: none;
}

.operation-link span {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
}

.operation-link strong {
  font-size: 1rem;
  line-height: 1.35;
}

.primary-action {
  border-color: #a7d7be;
}

.primary-action span {
  color: #176b4d;
}

.reservation-action {
  border-color: #b8cdf6;
}

.reservation-action span {
  color: #315f91;
}

.check-in-action {
  border-color: #a7d7be;
}

.check-in-action span {
  color: #176b4d;
}

.today-view-action {
  border-color: #c7d2fe;
}

.today-view-action span {
  color: #4f46e5;
}

.queue-action {
  border-color: #a7d7be;
}

.queue-action span {
  color: #176b4d;
}

.queue-list-action {
  border-color: #b8cdf6;
}

.queue-list-action span {
  color: #315f91;
}

.queue-call-action {
  border-color: #fed7aa;
}

.queue-call-action span {
  color: #c2410c;
}

.queue-seat-action {
  border-color: #c7d2fe;
}

.queue-seat-action span {
  color: #4f46e5;
}

.seat-reservation-action {
  border-color: #fed7aa;
}

.seat-reservation-action span {
  color: #c2410c;
}

.operation-link:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.28);
  outline-offset: 2px;
}

.handoff-notes {
  display: grid;
  gap: 10px;
  padding: 14px;
}

ol {
  color: #41516a;
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 20px;
}

li {
  line-height: 1.45;
}

@media (min-width: 720px) {
  .staff-shell {
    padding-top: 36px;
  }

  h1 {
    font-size: 2rem;
  }
}
</style>
