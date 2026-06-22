<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  getReservationTodayView,
  ReservationTodayViewApiError
} from '../api/reservationTodayViewApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ReservationTodayViewApiErrorResponse,
  ReservationTodayViewItem,
  ReservationTodayViewResponse,
  ReservationTodayViewStatusFilter
} from '../types/reservationTodayView'

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

const statusLabels: Record<string, string> = {
  confirmed: '已确认',
  arrived: '已到店',
  seated: '已入座',
  cancelled: '已取消',
  no_show: '爽约',
  completed: '已完成',
  draft: '草稿'
}

const businessDate = ref('')
const selectedStatus = ref<ReservationTodayViewStatusFilter>('operational')
const isLoading = ref(false)
const response = ref<ReservationTodayViewResponse | null>(null)
const apiError = ref<ReservationTodayViewApiErrorResponse | null>(null)
const copyStatus = reactive<Record<string, string>>({})
let loadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const items = computed(() => response.value?.items ?? [])
const displayedBusinessDate = computed(() => response.value?.businessDate || businessDate.value || '后端默认')
const storeTimezone = computed(() => response.value?.storeTimezone || 'Asia/Singapore')
const showEmptyState = computed(
  () => !isLoading.value && !apiError.value && !!response.value && items.value.length === 0
)

watch(
  [storeId, businessDate, selectedStatus],
  () => {
    void loadTodayView()
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

async function copyReservationId(reservationId: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(reservationId)
      setCopyStatus(reservationId, '已复制')
      return
    } catch {
      setCopyStatus(reservationId, '请手动复制')
      return
    }
  }

  setCopyStatus(reservationId, '请手动复制')
}

function setCopyStatus(reservationId: string, message: string): void {
  copyStatus[reservationId] = message

  window.setTimeout(() => {
    if (copyStatus[reservationId] === message) {
      delete copyStatus[reservationId]
    }
  }, 1800)
}

function checkInRoute(item: ReservationTodayViewItem) {
  return {
    name: 'reservation-check-in',
    params: {
      storeId: storeId.value
    },
    query: {
      reservationId: item.reservationId
    }
  }
}

function directSeatingRoute(item: ReservationTodayViewItem) {
  return {
    name: 'reservation-arrived-direct-seating',
    params: {
      storeId: storeId.value
    },
    query: {
      reservationId: item.reservationId
    }
  }
}

function queueReservationRoute(item: ReservationTodayViewItem) {
  return {
    name: 'reservation-arrived-to-queue',
    params: {
      storeId: storeId.value
    },
    query: {
      reservationId: item.reservationId
    }
  }
}

function isReadOnlyStatus(status: string): boolean {
  return status !== 'confirmed' && status !== 'arrived' && status !== 'seated'
}

function formatStoreDateTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: storeTimezone.value,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`
}

function reservationTimeRange(item: ReservationTodayViewItem): string {
  return `${formatStoreDateTime(item.reservedStartAt)} - ${formatStoreDateTime(item.reservedEndAt)}`
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function statusClass(status: string): string {
  return `status-${status.replace(/_/g, '-')}`
}

function customerDisplay(item: ReservationTodayViewItem): string {
  const values = [item.customerName, item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : '未填写'
}

function optionalDisplay(value: string | null | undefined): string {
  return value?.trim() ? value : '未填写'
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
</script>

<template>
  <main class="page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>今日预约</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <section class="filter-panel" aria-label="今日预约筛选">
      <label class="date-field">
        <span>营业日期</span>
        <input v-model="businessDate" name="businessDate" type="date" />
      </label>

      <div class="date-summary" aria-live="polite">
        <span>当前日期</span>
        <strong>{{ displayedBusinessDate }}</strong>
      </div>

      <section class="status-filter" aria-label="状态筛选">
        <p>状态筛选</p>
        <div class="status-options">
          <button
            v-for="option in statusOptions"
            :key="option.value"
            :aria-pressed="selectedStatus === option.value"
            :class="{ selected: selectedStatus === option.value }"
            type="button"
            @click="selectedStatus = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </section>
    </section>

    <section v-if="isLoading" class="state-panel" aria-live="polite">
      <h2>加载中...</h2>
      <p>正在读取当前门店预约。</p>
    </section>

    <section v-if="apiError" class="state-panel error-panel" aria-live="assertive">
      <h2>加载失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <section v-if="showEmptyState" class="state-panel" aria-live="polite">
      <h2>今日暂无预约</h2>
      <p>可以切换日期或状态筛选。</p>
    </section>

    <section v-if="items.length" class="reservation-list" aria-label="今日预约列表">
      <article v-for="item in items" :key="item.reservationId" class="reservation-card">
        <header class="card-header">
          <div>
            <span>预约编号</span>
            <strong>{{ item.reservationCode }}</strong>
          </div>
          <span class="status-pill" :class="statusClass(item.status)">
            {{ statusLabel(item.status) }}
          </span>
        </header>

        <p class="time-line">{{ reservationTimeRange(item) }}</p>
        <p class="raw-status">状态代码：{{ item.status }}</p>

        <dl class="card-details">
          <div>
            <dt>人数</dt>
            <dd>{{ item.partySize }}</dd>
          </div>
          <div>
            <dt>客户姓名 / 昵称</dt>
            <dd>{{ customerDisplay(item) }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ optionalDisplay(item.phoneMasked) }}</dd>
          </div>
          <div>
            <dt>保留到</dt>
            <dd>{{ formatStoreDateTime(item.holdUntilAt) }}</dd>
          </div>
          <div>
            <dt>备注</dt>
            <dd>{{ optionalDisplay(item.note) }}</dd>
          </div>
          <div class="reservation-id-row">
            <dt>预约 ID</dt>
            <dd>{{ item.reservationId }}</dd>
          </div>
        </dl>

        <div class="card-actions">
          <button class="secondary-button" type="button" @click="copyReservationId(item.reservationId)">
            复制预约 ID
          </button>

          <RouterLink v-if="item.status === 'confirmed'" class="primary-button" :to="checkInRoute(item)">
            预约到店
          </RouterLink>

          <RouterLink v-if="item.status === 'arrived'" class="primary-button" :to="directSeatingRoute(item)">
            预约入座
          </RouterLink>

          <RouterLink
            v-if="item.status === 'arrived'"
            class="secondary-button queue-button"
            :to="queueReservationRoute(item)"
          >
            进入排队
          </RouterLink>

          <span v-if="item.status === 'seated'" class="readonly-action">已入座</span>
          <span v-if="isReadOnlyStatus(item.status)" class="readonly-action">只读</span>
        </div>

        <p v-if="copyStatus[item.reservationId]" class="copy-feedback" aria-live="polite">
          {{ copyStatus[item.reservationId] }}
        </p>
      </article>
    </section>
  </main>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 680px;
  min-height: 100vh;
  padding: 20px 14px 32px;
}

.page-header,
.filter-panel,
.state-panel,
.reservation-card {
  display: grid;
  gap: 12px;
}

.page-header {
  gap: 4px;
}

.eyebrow,
.store-context,
.raw-status,
.date-summary span,
.status-filter p,
.copy-feedback {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.home-link {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  justify-self: start;
  text-decoration: none;
}

h1,
h2,
.date-summary strong {
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

.filter-panel,
.state-panel,
.reservation-card {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
  padding: 14px;
}

label {
  display: grid;
  gap: 6px;
}

label span,
dt,
.card-header span,
.status-filter p {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
}

input {
  background: #fbfcfe;
  border: 1px solid #c8d3e2;
  border-radius: 6px;
  color: #182536;
  min-height: 44px;
  outline: none;
  padding: 10px 11px;
  width: 100%;
}

input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.date-field {
  background: #eaf2ff;
  border: 1px solid #b8cdf6;
  border-radius: 8px;
  padding: 12px;
}

.date-summary {
  display: grid;
  gap: 4px;
}

.status-filter {
  display: grid;
  gap: 8px;
}

.status-options {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.status-options button {
  background: #f8fafc;
  border: 1px solid #c8d3e2;
  border-radius: 999px;
  color: #315f91;
  flex: 0 0 auto;
  font-weight: 800;
  min-height: 40px;
  padding: 0 13px;
}

.status-options button.selected {
  background: #176b4d;
  border-color: #176b4d;
  color: #ffffff;
}

.state-panel p {
  color: #41516a;
  margin: 0;
}

.error-panel {
  border-color: #f4b8b8;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.message-key {
  overflow-wrap: anywhere;
}

.reservation-list {
  display: grid;
  gap: 12px;
}

.reservation-card {
  border-color: #cdd8e7;
}

.card-header {
  align-items: start;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.card-header div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.card-header strong {
  color: #14213d;
  font-size: 1.1rem;
  overflow-wrap: anywhere;
}

.status-pill,
.readonly-action {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font-size: 0.78rem;
  font-weight: 800;
  justify-content: center;
  min-height: 30px;
  padding: 0 10px;
  white-space: nowrap;
}

.status-pill {
  background: #eaf2ff;
  color: #315f91;
}

.status-confirmed {
  background: #eaf2ff;
  color: #315f91;
}

.status-arrived {
  background: #eef6f1;
  color: #176b4d;
}

.status-seated {
  background: #fff7ed;
  color: #c2410c;
}

.status-cancelled,
.status-no-show,
.status-completed {
  background: #f1f5f9;
  color: #475569;
}

.time-line {
  color: #14213d;
  font-size: 1rem;
  font-weight: 900;
  margin: 0;
}

.card-details {
  display: grid;
  gap: 9px;
  margin: 0;
}

.card-details div {
  display: grid;
  gap: 3px;
}

dt,
dd {
  margin: 0;
}

dd {
  color: #1d2736;
  overflow-wrap: anywhere;
}

.reservation-id-row dd {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 0.86rem;
}

.card-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: 1fr;
}

.primary-button,
.secondary-button {
  align-items: center;
  border-radius: 8px;
  display: inline-flex;
  font-weight: 800;
  justify-content: center;
  min-height: 46px;
  padding: 0 14px;
  text-align: center;
  text-decoration: none;
}

.primary-button {
  background: #176b4d;
  border: 1px solid #176b4d;
  color: #ffffff;
}

.secondary-button {
  background: #ffffff;
  border: 1px solid #b8cdf6;
  color: #315f91;
}

.queue-button {
  border-color: #a7d7be;
  color: #176b4d;
}

.readonly-action {
  background: #f1f5f9;
  color: #475569;
  min-height: 46px;
}

.copy-feedback {
  color: #176b4d;
  font-weight: 800;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.28);
  outline-offset: 2px;
}

@media (min-width: 720px) {
  .page-shell {
    padding-top: 36px;
  }

  h1 {
    font-size: 2rem;
  }

  .filter-panel {
    grid-template-columns: minmax(0, 1fr) minmax(0, 0.8fr);
  }

  .status-filter {
    grid-column: 1 / -1;
  }

  .card-actions {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}
</style>
