<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, type RouteLocationRaw } from 'vue-router'

import {
  getStaffHomeOverview,
  StaffHomeOverviewApiError
} from '../api/staffHomeOverviewApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  StaffHomeOverviewApiErrorResponse,
  StaffHomeOverviewResponse
} from '../types/staffHomeOverview'

interface KpiItem {
  key: string
  label: string
  value: number
  unit: string
  detail: string
  tone: 'reservation' | 'arrival' | 'queue' | 'table'
}

interface StatusRow {
  key: string
  label: string
  value: number
  detail: string
}

interface OperationToolbarItem {
  id: string
  label: string
  description: string
  symbol: string
  to: RouteLocationRaw
  tone: 'reservation' | 'queue' | 'success'
}

const route = useRoute()
const storeContext = useStoreContextStore()
const authSession = useAuthSessionStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const overview = ref<StaffHomeOverviewResponse | null>(null)
const apiError = ref<StaffHomeOverviewApiErrorResponse | null>(null)
const isLoading = ref(false)
let overviewLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const displayedBusinessDate = computed(() => overview.value?.businessDate ?? currentBusinessDate.value)
const currentPermissions = computed(() => authSession.user?.permissions ?? [])
const hasReservationQueue = computed(() =>
  currentPermissions.value.some(permission =>
    permission.startsWith('reservation.') || permission.startsWith('queue.')
  )
)
const canCheckInReservation = computed(() =>
  hasPermission('reservation.check_in')
)
const canCallQueueTicket = computed(() =>
  hasPermission('queue.call')
)
const canSeatCalledQueueTicket = computed(() =>
  hasPermission('queue.seat')
)
const hasVisibleOperation = computed(
  () => canCheckInReservation.value || canCallQueueTicket.value || canSeatCalledQueueTicket.value
)
const appStatusLabel = computed(() => {
  if (isLoading.value) {
    return '刷新中'
  }

  if (apiError.value) {
    return '暂不可用'
  }

  return '首页'
})
const reservationConfirmedTodayRoute = computed(() => ({
  name: 'reservation-today-view',
  params: {
    storeId: storeId.value
  },
  query: {
    status: 'confirmed'
  }
}))
const queueTicketListRoute = computed(() => ({
  name: 'queue-ticket-list',
  params: {
    storeId: storeId.value
  }
}))
const operationToolbarItems = computed<OperationToolbarItem[]>(() => compactToolbarItems([
  canCheckInReservation.value
    ? {
        id: 'reservation-confirmed-today',
        label: '预约到店',
        description: '从今日预约确认客人到店',
        symbol: '到',
        to: reservationConfirmedTodayRoute.value,
        tone: 'reservation'
      }
    : null,
  canCallQueueTicket.value
    ? {
        id: 'queue-call',
        label: '排队叫号',
        description: '从列表选择排队票一键叫号',
        symbol: '叫',
        to: queueTicketListRoute.value,
        tone: 'queue'
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
const showOperationToolbar = computed(() => hasVisibleOperation.value && operationToolbarItems.value.length > 0)
const activeQueueTickets = computed(() => {
  const queue = overview.value?.queue
  return (queue?.waitingTickets ?? 0) + (queue?.calledTickets ?? 0)
})
const activeQueuePartySize = computed(() => {
  const queue = overview.value?.queue
  return (queue?.waitingPartySize ?? 0) + (queue?.calledPartySize ?? 0)
})
const arrivedReservationGroups = computed(() => {
  const reservation = overview.value?.reservation
  return (reservation?.arrivedReservations ?? 0) + (reservation?.seatedReservations ?? 0)
})
const arrivedReservationPartySize = computed(() => {
  const reservation = overview.value?.reservation
  return (reservation?.arrivedPartySize ?? 0) + (reservation?.seatedPartySize ?? 0)
})
const primaryKpis = computed<KpiItem[]>(() => {
  const reservation = overview.value?.reservation
  const queue = overview.value?.queue
  const tables = overview.value?.tables

  return [
    {
      key: 'reservations',
      label: '今日预约',
      value: reservation?.totalReservations ?? 0,
      unit: '组',
      detail: `${reservation?.totalPartySize ?? 0} 人`,
      tone: 'reservation'
    },
    {
      key: 'arrived',
      label: '已到店',
      value: arrivedReservationGroups.value,
      unit: '组',
      detail: `${arrivedReservationPartySize.value} 人`,
      tone: 'arrival'
    },
    {
      key: 'queue',
      label: '当前排队',
      value: activeQueueTickets.value,
      unit: '组',
      detail: `${activeQueuePartySize.value} 人`,
      tone: 'queue'
    },
    {
      key: 'tables',
      label: '可用桌台',
      value: tables?.availableTables ?? 0,
      unit: '张',
      detail: `共 ${tables?.totalTables ?? 0} 张`,
      tone: 'table'
    }
  ]
})
const queueRows = computed<StatusRow[]>(() => {
  const queue = overview.value?.queue
  return [
    {
      key: 'waiting',
      label: '等待中',
      value: queue?.waitingTickets ?? 0,
      detail: `${queue?.waitingPartySize ?? 0} 人`
    },
    {
      key: 'called',
      label: '已叫号',
      value: queue?.calledTickets ?? 0,
      detail: `${queue?.calledPartySize ?? 0} 人`
    },
    {
      key: 'skipped',
      label: '已过号',
      value: queue?.skippedTickets ?? 0,
      detail: '可重回或取消'
    }
  ]
})
const tableRows = computed<StatusRow[]>(() => {
  const tables = overview.value?.tables
  return [
    {
      key: 'available',
      label: '可用',
      value: tables?.availableTables ?? 0,
      detail: '可安排入座'
    },
    {
      key: 'occupied',
      label: '占用',
      value: tables?.occupiedTables ?? 0,
      detail: '当前服务中'
    },
    {
      key: 'reserved',
      label: '预留',
      value: tables?.reservedTables ?? 0,
      detail: '预约占用'
    },
    {
      key: 'cleaning',
      label: '清台',
      value: tables?.cleaningTables ?? 0,
      detail: '待恢复可用'
    },
    {
      key: 'temporary',
      label: '临时组',
      value: tables?.temporaryGroups ?? 0,
      detail: '按桌组使用'
    }
  ]
})
const overviewHint = computed(() => {
  if (apiError.value) {
    return '今日概览暂不可用'
  }

  if (!overview.value) {
    return '正在读取今日营业数据'
  }

  if (activeQueueTickets.value > 0) {
    return `排队 ${activeQueueTickets.value} 组，优先看等待和已叫号`
  }

  return '当前没有排队压力'
})
const errorText = computed(() => apiError.value?.error.messageKey ?? 'staff_home.overview.unknown_error')

watch(
  [storeId, currentBusinessDate],
  async ([nextStoreId, nextBusinessDate]) => {
    await loadOverview(nextStoreId, nextBusinessDate)
  },
  { immediate: true }
)

async function reloadOverview(): Promise<void> {
  await loadOverview(storeId.value, currentBusinessDate.value)
}

async function loadOverview(nextStoreId: string | undefined, businessDate: string): Promise<void> {
  const sequence = ++overviewLoadSequence
  overview.value = null
  apiError.value = null

  if (!nextStoreId) {
    isLoading.value = false
    return
  }

  isLoading.value = true

  try {
    const response = await getStaffHomeOverview(nextStoreId, { businessDate })

    if (sequence === overviewLoadSequence) {
      overview.value = response
    }
  } catch (error) {
    if (sequence === overviewLoadSequence) {
      apiError.value = error instanceof StaffHomeOverviewApiError
        ? error.response
        : createLocalError()
    }
  } finally {
    if (sequence === overviewLoadSequence) {
      isLoading.value = false
    }
  }
}

function createLocalError(): StaffHomeOverviewApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'REQUEST_FAILED',
      messageKey: 'staff_home.overview.request_failed',
      details: {}
    }
  }
}

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return '默认门店'
  }

  return `门店 ${value.slice(0, 8)}`
}

function compactToolbarItems(actions: Array<OperationToolbarItem | null>): OperationToolbarItem[] {
  return actions.filter((action): action is OperationToolbarItem => action !== null)
}

function hasPermission(permission: string): boolean {
  return currentPermissions.value.includes(permission)
}
</script>

<template>
  <main class="staff-workbench-shell staff-shell">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="displayedBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <button class="topbar-refresh" type="button" :disabled="isLoading" @click="reloadOverview">
          刷新
        </button>
      </template>
    </StaffHomeTopBar>

    <div class="home-overview-body">
      <section class="date-strip" aria-label="今日营业日期">
        <div>
          <span>今日</span>
          <strong>{{ displayedBusinessDate }}</strong>
        </div>
        <em>{{ overviewHint }}</em>
      </section>

      <section v-if="apiError" class="overview-error" aria-label="今日概览加载失败">
        <strong>今日概览暂不可用</strong>
        <span>{{ errorText }}</span>
      </section>

      <nav v-if="showOperationToolbar" class="operation-toolbar" aria-label="门店员工操作">
        <RouterLink
          v-for="item in operationToolbarItems"
          :key="item.id"
          class="operation-tool"
          :class="`operation-tool--${item.tone}`"
          :to="item.to"
        >
          <span class="operation-symbol" aria-hidden="true">{{ item.symbol }}</span>
          <span class="operation-copy">
            <strong>{{ item.label }}</strong>
            <em>{{ item.description }}</em>
          </span>
        </RouterLink>
      </nav>

      <section
        v-else-if="hasReservationQueue && authSession.loaded"
        class="empty-state"
        aria-label="当前权限无可用入口"
      >
        <p>当前权限无可用入口</p>
        <strong>入口会按 App Gate 权限自动显示。</strong>
      </section>

      <section class="kpi-grid" aria-label="今日概览">
        <article
          v-for="item in primaryKpis"
          :key="item.key"
          class="kpi-card"
          :class="`kpi-card--${item.tone}`"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
          <em>{{ item.detail }}</em>
        </article>
      </section>

      <section class="overview-section" aria-label="当前排队人数组">
        <header>
          <div>
            <span>当前排队</span>
            <strong>{{ activeQueueTickets }} 组 / {{ activeQueuePartySize }} 人</strong>
          </div>
        </header>
        <div class="party-size-row">
          <div
            v-for="group in overview?.partySizeGroups ?? []"
            :key="group.label"
            class="party-size-chip"
          >
            <strong>{{ group.label }}</strong>
            <span>{{ group.groups }}组 / {{ group.partySize }}人</span>
          </div>
        </div>
        <div class="status-grid">
          <div v-for="row in queueRows" :key="row.key" class="status-item">
            <span>{{ row.label }}</span>
            <strong>{{ row.value }}</strong>
            <em>{{ row.detail }}</em>
          </div>
        </div>
      </section>

      <section class="overview-section" aria-label="桌台状态">
        <header>
          <div>
            <span>桌台状态</span>
            <strong>{{ overview?.tables.availableTables ?? 0 }} 可用 / {{ overview?.tables.totalTables ?? 0 }} 总桌</strong>
          </div>
        </header>
        <div class="status-grid status-grid--tables">
          <div v-for="row in tableRows" :key="row.key" class="status-item">
            <span>{{ row.label }}</span>
            <strong>{{ row.value }}</strong>
            <em>{{ row.detail }}</em>
          </div>
        </div>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="home" />
  </main>
</template>

<style scoped>
.home-overview-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(92px + env(safe-area-inset-bottom));
}

.topbar-refresh {
  background: #fff7ed;
  border: 1px solid #fdba74;
  border-radius: 999px;
  color: #c2410c;
  cursor: pointer;
  font-size: 0.78rem;
  font-weight: 900;
  letter-spacing: 0;
  min-height: 34px;
  padding: 0 12px;
}

.topbar-refresh:disabled {
  cursor: wait;
  opacity: 0.62;
}

.date-strip,
.overview-section,
.overview-error {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.date-strip {
  align-items: center;
  display: flex;
  justify-content: space-between;
  min-height: 58px;
  padding: 10px 12px;
}

.date-strip div,
.overview-section header div {
  display: grid;
  gap: 2px;
}

.date-strip span,
.overview-section header span,
.kpi-card span,
.status-item span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
  letter-spacing: 0;
}

.date-strip strong,
.overview-section header strong {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
  letter-spacing: 0;
}

.date-strip em {
  color: #c2410c;
  font-size: 0.74rem;
  font-style: normal;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1.25;
  max-width: 52%;
  text-align: right;
}

.overview-error {
  display: grid;
  gap: 4px;
  padding: 12px;
}

.overview-error strong {
  color: #991b1b;
  font-size: 0.9rem;
  font-weight: 950;
}

.overview-error span {
  color: #b91c1c;
  font-size: 0.78rem;
  font-weight: 800;
}

.operation-toolbar {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 8px;
}

.operation-tool {
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 8px;
  grid-template-columns: 32px minmax(0, 1fr);
  min-height: 66px;
  padding: 8px;
  text-decoration: none;
}

.operation-symbol {
  align-items: center;
  background: #f8fafc;
  border-radius: 999px;
  color: #475569;
  display: inline-flex;
  font-size: 0.86rem;
  font-weight: 950;
  height: 32px;
  justify-content: center;
  width: 32px;
}

.operation-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.operation-copy strong {
  color: #0f172a;
  font-size: 0.84rem;
  font-weight: 950;
  letter-spacing: 0;
  line-height: 1.18;
}

.operation-copy em {
  color: #64748b;
  font-size: 0.7rem;
  font-style: normal;
  font-weight: 800;
  line-height: 1.2;
}

.operation-tool--reservation .operation-symbol {
  background: #dbeafe;
  color: #2563eb;
}

.operation-tool--queue .operation-symbol {
  background: #ffedd5;
  color: #c2410c;
}

.operation-tool--success .operation-symbol {
  background: #d1fae5;
  color: #047857;
}

.operation-tool:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

.empty-state {
  background: #ffffff;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  padding: 14px;
}

.empty-state p {
  color: #0f172a;
  font-size: 0.9rem;
  font-weight: 900;
  letter-spacing: 0;
  margin: 0;
}

.empty-state strong {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.3;
}

.kpi-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.kpi-card {
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 5px;
  min-height: 104px;
  padding: 12px;
}

.kpi-card strong {
  color: #0f172a;
  font-size: 1.62rem;
  font-weight: 950;
  letter-spacing: 0;
  line-height: 1;
}

.kpi-card small {
  font-size: 0.72rem;
  font-weight: 900;
  margin-left: 2px;
}

.kpi-card em,
.status-item em {
  color: #475569;
  font-size: 0.78rem;
  font-style: normal;
  font-weight: 850;
  letter-spacing: 0;
}

.kpi-card--reservation {
  background: #fff7ed;
  border-color: #fdba74;
}

.kpi-card--arrival {
  background: #eff6ff;
  border-color: #93c5fd;
}

.kpi-card--queue {
  background: #fefce8;
  border-color: #facc15;
}

.kpi-card--table {
  background: #ecfdf5;
  border-color: #86efac;
}

.overview-section {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.overview-section header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.party-size-row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.party-size-chip {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  min-height: 58px;
  padding: 8px;
}

.party-size-chip strong {
  color: #0f172a;
  font-size: 0.86rem;
  font-weight: 950;
}

.party-size-chip span {
  color: #334155;
  font-size: 0.72rem;
  font-weight: 850;
  line-height: 1.25;
}

.status-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.status-grid--tables {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.status-item {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  min-height: 74px;
  padding: 8px;
}

.status-item strong {
  color: #0f172a;
  font-size: 1.2rem;
  font-weight: 950;
  line-height: 1;
}

@media (max-width: 420px) {
  .party-size-row,
  .status-grid--tables,
  .operation-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
