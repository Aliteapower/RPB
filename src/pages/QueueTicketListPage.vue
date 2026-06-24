<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import { fetchMeApps } from '../api/meAppsApi'
import { callQueueTicket, QueueCallApiError } from '../api/queueCallApi'
import { cancelQueueTicket, QueueCancelApiError } from '../api/queueCancelApi'
import { listQueueTickets, QueueTicketListApiError } from '../api/queueTicketListApi'
import { QueueRejoinApiError, rejoinQueueTicket } from '../api/queueRejoinApi'
import { QueueSkipApiError, skipQueueTicket } from '../api/queueSkipApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type { MeAppEntry } from '../types/meApps'
import type {
  CallQueueTicketResponse,
  QueueCallApiErrorResponse
} from '../types/queueCall'
import type {
  CancelQueueTicketResponse,
  QueueCancelApiErrorResponse
} from '../types/queueCancel'
import type {
  QueueTicketListApiErrorResponse,
  QueueTicketListItem,
  QueueTicketListQuery,
  QueueTicketListResponse
} from '../types/queueTicketList'
import type {
  QueueRejoinApiErrorResponse,
  RejoinQueueTicketResponse
} from '../types/queueRejoin'
import type {
  QueueSkipApiErrorResponse,
  SkipQueueTicketResponse
} from '../types/queueSkip'

type UiStatusFilter =
  | 'all'
  | 'waiting'
  | 'called'
  | 'skipped'
  | 'rejoined'
  | 'seated'
  | 'cancelled'
  | 'expired'

const route = useRoute()
const storeContext = useStoreContextStore()

const defaultLimit = 50
const storeTimezone = 'Asia/Singapore'

const statusOptions: Array<{ value: UiStatusFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'waiting', label: '等待中' },
  { value: 'called', label: '已叫号' },
  { value: 'skipped', label: '已过号' },
  { value: 'rejoined', label: '已重回' },
  { value: 'seated', label: '已入座' },
  { value: 'cancelled', label: '已取消' },
  { value: 'expired', label: '已过期' }
]

const seatActionLabel = { label: '入桌' }

const statusLabels: Record<string, string> = {
  waiting: '等待中',
  called: '已叫号',
  seated: '已入座',
  skipped: '已过号',
  rejoined: '已重回',
  expired: '已过期',
  cancelled: '已取消'
}

const selectedStatus = ref<UiStatusFilter>('all')
const limit = ref(defaultLimit)
const offset = ref(0)
const isLoading = ref(false)
const response = ref<QueueTicketListResponse | null>(null)
const apiError = ref<QueueTicketListApiErrorResponse | null>(null)
const apps = ref<MeAppEntry[]>([])
const appsLoading = ref(false)
const callApiError = ref<QueueCallApiErrorResponse | null>(null)
const callSuccess = ref<CallQueueTicketResponse | null>(null)
const callInFlightTicketIds = ref<Set<string>>(new Set())
const skipApiError = ref<QueueSkipApiErrorResponse | null>(null)
const skipSuccess = ref<SkipQueueTicketResponse | null>(null)
const skipInFlightTicketId = ref<string | null>(null)
const skipInFlightTicketIds = ref<Set<string>>(new Set())
const rejoinApiError = ref<QueueRejoinApiErrorResponse | null>(null)
const rejoinSuccess = ref<RejoinQueueTicketResponse | null>(null)
const rejoinInFlightTicketId = ref<string | null>(null)
const rejoinInFlightTicketIds = ref<Set<string>>(new Set())
const cancelApiError = ref<QueueCancelApiErrorResponse | null>(null)
const cancelSuccess = ref<CancelQueueTicketResponse | null>(null)
const cancelInFlightTicketIds = ref<Set<string>>(new Set())
let loadSequence = 0
let appsLoadSequence = 0

const appGateSkipErrorCodes = new Set([
  'TENANT_APP_NOT_ENABLED',
  'STORE_APP_NOT_ENABLED',
  'PERMISSION_DENIED'
])
const skipErrorCodesThatRefresh = new Set(['QUEUE_TICKET_STATUS_NOT_CALLED'])
const appGateRejoinErrorCodes = new Set([
  'TENANT_APP_NOT_ENABLED',
  'STORE_APP_NOT_ENABLED',
  'PERMISSION_DENIED'
])
const rejoinErrorCodesThatRefresh = new Set(['QUEUE_TICKET_STATUS_NOT_SKIPPED'])
const appGateCallErrorCodes = new Set([
  'TENANT_APP_NOT_ENABLED',
  'STORE_APP_NOT_ENABLED',
  'PERMISSION_DENIED'
])
const callErrorCodesThatRefresh = new Set(['QUEUE_TICKET_STATUS_NOT_WAITING'])
const appGateCancelErrorCodes = new Set([
  'TENANT_APP_NOT_ENABLED',
  'STORE_APP_NOT_ENABLED',
  'PERMISSION_DENIED'
])
const cancelErrorCodesThatRefresh = new Set([
  'QUEUE_TICKET_CANNOT_CANCEL_SEATED',
  'QUEUE_TICKET_CANNOT_CANCEL_EXPIRED'
])

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const items = computed(() => response.value?.items ?? [])
const page = computed(() => response.value?.page ?? { limit: limit.value, offset: offset.value, total: 0 })
const reservationQueueEntry = computed(() =>
  apps.value.find(app => app.appKey === 'reservation_queue' && app.entryVisible)
)
const canSkipQueueTicket = computed(
  () => reservationQueueEntry.value?.permissions.includes('queue.skip') ?? false
)
const canRejoinQueueTicket = computed(
  () => reservationQueueEntry.value?.permissions.includes('queue.rejoin') ?? false
)
const canCallQueueTicket = computed(
  () => reservationQueueEntry.value?.permissions.includes('queue.call') ?? false
)
const canSeatCalledQueueTicket = computed(
  () => reservationQueueEntry.value?.permissions.includes('queue.seat') ?? false
)
const canCancelQueueTicket = computed(
  () => reservationQueueEntry.value?.permissions.includes('queue.cancel') ?? false
)
const showEmptyState = computed(
  () => !isLoading.value && !apiError.value && !!response.value && items.value.length === 0
)
const canGoPrevious = computed(() => offset.value > 0 && !isLoading.value)
const canGoNext = computed(
  () =>
    !isLoading.value &&
    !!response.value &&
    items.value.length > 0 &&
    page.value.offset + items.value.length < page.value.total
)
const pageRangeText = computed(() => {
  if (!response.value) {
    return '尚未加载'
  }

  if (page.value.total === 0) {
    return '0 / 0'
  }

  return `${page.value.offset + 1}-${page.value.offset + items.value.length} / ${page.value.total}`
})

watch(
  [storeId, selectedStatus, limit, offset],
  () => {
    void loadQueueTickets()
  },
  { immediate: true }
)

watch(
  storeId,
  async nextStoreId => {
    const sequence = ++appsLoadSequence
    apps.value = []

    if (!nextStoreId) {
      appsLoading.value = false
      return
    }

    appsLoading.value = true

    try {
      const result = await fetchMeApps(nextStoreId)

      if (sequence === appsLoadSequence) {
        apps.value = result.apps
      }
    } catch {
      if (sequence === appsLoadSequence) {
        apps.value = []
      }
    } finally {
      if (sequence === appsLoadSequence) {
        appsLoading.value = false
      }
    }
  },
  { immediate: true }
)

async function loadQueueTickets(): Promise<QueueTicketListResponse | null> {
  const currentStoreId = storeId.value
  const sequence = ++loadSequence
  response.value = null
  apiError.value = null

  if (!currentStoreId) {
    isLoading.value = false
    return null
  }

  isLoading.value = true
  let loadedResponse: QueueTicketListResponse | null = null

  const query: QueueTicketListQuery = {
    limit: limit.value,
    offset: offset.value
  }

  if (selectedStatus.value !== 'all') {
    query.status = selectedStatus.value
  }

  try {
    const result = await listQueueTickets(currentStoreId, query)

    if (sequence === loadSequence) {
      response.value = result
      loadedResponse = result
    }
  } catch (error) {
    if (sequence === loadSequence) {
      apiError.value =
        error instanceof QueueTicketListApiError
          ? error.response
          : createLocalError('UNKNOWN_ERROR', 'queue.list.unknown_error')
    }
  } finally {
    if (sequence === loadSequence) {
      isLoading.value = false
    }
  }

  return loadedResponse
}

function selectStatus(status: UiStatusFilter): void {
  if (selectedStatus.value === status) {
    return
  }

  selectedStatus.value = status
  offset.value = 0
}

function goPrevious(): void {
  if (!canGoPrevious.value) {
    return
  }

  offset.value = Math.max(0, offset.value - page.value.limit)
}

function goNext(): void {
  if (!canGoNext.value) {
    return
  }

  offset.value += page.value.limit
}

function refresh(): void {
  void loadQueueTickets()
}

function canShowSkipAction(item: QueueTicketListItem): boolean {
  return canSkipQueueTicket.value && item.queueTicketStatus === 'called'
}

function canShowRejoinAction(item: QueueTicketListItem): boolean {
  return canRejoinQueueTicket.value && item.queueTicketStatus === 'skipped'
}

function canShowCallAction(item: QueueTicketListItem): boolean {
  return canCallQueueTicket.value && ['waiting', 'rejoined'].includes(item.queueTicketStatus)
}

function canShowSeatAction(item: QueueTicketListItem): boolean {
  return canSeatCalledQueueTicket.value && item.queueTicketStatus === 'called'
}

function canShowCancelAction(item: QueueTicketListItem): boolean {
  return (
    canCancelQueueTicket.value &&
    ['waiting', 'called', 'skipped', 'rejoined'].includes(item.queueTicketStatus)
  )
}

function canShowAnyQueueAction(item: QueueTicketListItem): boolean {
  return (
    canShowCallAction(item) ||
    canShowSkipAction(item) ||
    canShowRejoinAction(item) ||
    canShowSeatAction(item) ||
    canShowCancelAction(item)
  )
}

function isCallingTicket(item: QueueTicketListItem): boolean {
  return callInFlightTicketIds.value.has(item.queueTicketId)
}

function isSkippingTicket(item: QueueTicketListItem): boolean {
  return skipInFlightTicketIds.value.has(item.queueTicketId)
}

function isRejoiningTicket(item: QueueTicketListItem): boolean {
  return rejoinInFlightTicketIds.value.has(item.queueTicketId)
}

function isCancellingTicket(item: QueueTicketListItem): boolean {
  return cancelInFlightTicketIds.value.has(item.queueTicketId)
}

function isTicketActionBusy(item: QueueTicketListItem): boolean {
  return (
    isCallingTicket(item) ||
    isSkippingTicket(item) ||
    isRejoiningTicket(item) ||
    isCancellingTicket(item)
  )
}

function confirmCallTicket(item: QueueTicketListItem): void {
  if (!canShowCallAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认叫号 #${item.queueTicketNumber}？`)) {
    return
  }

  void executeConfirmedCall(item)
}

function confirmSkipTicket(item: QueueTicketListItem): void {
  if (!canShowSkipAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认过号 #${item.queueTicketNumber}？`)) {
    return
  }

  void executeConfirmedSkip(item)
}

function confirmRejoinTicket(item: QueueTicketListItem): void {
  if (!canShowRejoinAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认重新入队 #${item.queueTicketNumber}？`)) {
    return
  }

  void executeConfirmedRejoin(item)
}

function confirmCancelTicket(item: QueueTicketListItem): void {
  if (!canShowCancelAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认取消 #${item.queueTicketNumber}？`)) {
    return
  }

  void executeConfirmedCancel(item)
}

async function executeConfirmedCall(item: QueueTicketListItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canShowCallAction(item) || isTicketActionBusy(item)) {
    return
  }

  const idempotencyKey = createCallIdempotencyKey()
  setCallLoading(item.queueTicketId, true)

  try {
    const result = await callQueueTicket(currentStoreId, item.queueTicketId, {}, idempotencyKey)
    callSuccess.value = result
    await refreshAfterQueueActionSuccess()
  } catch (error) {
    const resolvedError =
      error instanceof QueueCallApiError
        ? error.response
        : createCallLocalError('UNKNOWN_ERROR', 'queue.call.unknown_error')

    callApiError.value = resolvedError

    if (shouldRefreshAfterCallError(resolvedError)) {
      void loadQueueTickets()
    }
  } finally {
    setCallLoading(item.queueTicketId, false)
  }
}

async function executeConfirmedSkip(item: QueueTicketListItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canShowSkipAction(item) || isTicketActionBusy(item)) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  setSkipLoading(item.queueTicketId, true)

  try {
    const result = await skipQueueTicket(currentStoreId, item.queueTicketId, idempotencyKey)
    skipSuccess.value = result
    await refreshAfterSkipSuccess()
  } catch (error) {
    const resolvedError =
      error instanceof QueueSkipApiError
        ? error.response
        : createSkipLocalError('UNKNOWN_ERROR', 'queue.skip.unknown_error')

    skipApiError.value = resolvedError

    if (shouldRefreshAfterSkipError(resolvedError)) {
      void loadQueueTickets()
    }
  } finally {
    setSkipLoading(item.queueTicketId, false)
  }
}

async function executeConfirmedRejoin(item: QueueTicketListItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canShowRejoinAction(item) || isTicketActionBusy(item)) {
    return
  }

  const idempotencyKey = createRejoinIdempotencyKey()
  setRejoinLoading(item.queueTicketId, true)

  try {
    const result = await rejoinQueueTicket(currentStoreId, item.queueTicketId, idempotencyKey)
    rejoinSuccess.value = result
    await refreshAfterRejoinSuccess()
  } catch (error) {
    const resolvedError =
      error instanceof QueueRejoinApiError
        ? error.response
        : createRejoinLocalError('UNKNOWN_ERROR', 'queue.rejoin.unknown_error')

    rejoinApiError.value = resolvedError

    if (shouldRefreshAfterRejoinError(resolvedError)) {
      void loadQueueTickets()
    }
  } finally {
    setRejoinLoading(item.queueTicketId, false)
  }
}

async function executeConfirmedCancel(item: QueueTicketListItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canShowCancelAction(item) || isTicketActionBusy(item)) {
    return
  }

  const idempotencyKey = createCancelIdempotencyKey()
  setCancelLoading(item.queueTicketId, true)

  try {
    const result = await cancelQueueTicket(currentStoreId, item.queueTicketId, {}, idempotencyKey)
    cancelSuccess.value = result
    await refreshAfterQueueActionSuccess()
  } catch (error) {
    const resolvedError =
      error instanceof QueueCancelApiError
        ? error.response
        : createCancelLocalError('UNKNOWN_ERROR', 'queue.cancel.unknown_error')

    cancelApiError.value = resolvedError

    if (shouldRefreshAfterCancelError(resolvedError)) {
      void loadQueueTickets()
    }
  } finally {
    setCancelLoading(item.queueTicketId, false)
  }
}

async function refreshAfterQueueActionSuccess(): Promise<void> {
  const refreshed = await loadQueueTickets()

  if (refreshed && refreshed.items.length === 0 && offset.value > 0) {
    offset.value = Math.max(0, offset.value - refreshed.page.limit)
    await loadQueueTickets()
  }
}

async function refreshAfterSkipSuccess(): Promise<void> {
  await refreshAfterQueueActionSuccess()
}

async function refreshAfterRejoinSuccess(): Promise<void> {
  await refreshAfterQueueActionSuccess()
}

function shouldRefreshAfterCallError(error: QueueCallApiErrorResponse): boolean {
  const code = error.error.code

  if (appGateCallErrorCodes.has(code)) {
    return false
  }

  return callErrorCodesThatRefresh.has(code)
}

function shouldRefreshAfterSkipError(error: QueueSkipApiErrorResponse): boolean {
  const code = error.error.code

  if (appGateSkipErrorCodes.has(code)) {
    return false
  }

  return skipErrorCodesThatRefresh.has(code)
}

function shouldRefreshAfterRejoinError(error: QueueRejoinApiErrorResponse): boolean {
  const code = error.error.code

  if (appGateRejoinErrorCodes.has(code)) {
    return false
  }

  return rejoinErrorCodesThatRefresh.has(code)
}

function shouldRefreshAfterCancelError(error: QueueCancelApiErrorResponse): boolean {
  const code = error.error.code

  if (appGateCancelErrorCodes.has(code)) {
    return false
  }

  return cancelErrorCodesThatRefresh.has(code)
}

function setCallLoading(queueTicketId: string, loading: boolean): void {
  const nextIds = new Set(callInFlightTicketIds.value)

  if (loading) {
    nextIds.add(queueTicketId)
  } else {
    nextIds.delete(queueTicketId)
  }

  callInFlightTicketIds.value = nextIds
}

function setSkipLoading(queueTicketId: string, loading: boolean): void {
  const nextIds = new Set(skipInFlightTicketIds.value)

  if (loading) {
    nextIds.add(queueTicketId)
    skipInFlightTicketId.value = queueTicketId
  } else {
    nextIds.delete(queueTicketId)

    if (skipInFlightTicketId.value === queueTicketId) {
      skipInFlightTicketId.value = nextIds.values().next().value ?? null
    }
  }

  skipInFlightTicketIds.value = nextIds
}

function setRejoinLoading(queueTicketId: string, loading: boolean): void {
  const nextIds = new Set(rejoinInFlightTicketIds.value)

  if (loading) {
    nextIds.add(queueTicketId)
    rejoinInFlightTicketId.value = queueTicketId
  } else {
    nextIds.delete(queueTicketId)

    if (rejoinInFlightTicketId.value === queueTicketId) {
      rejoinInFlightTicketId.value = nextIds.values().next().value ?? null
    }
  }

  rejoinInFlightTicketIds.value = nextIds
}

function setCancelLoading(queueTicketId: string, loading: boolean): void {
  const nextIds = new Set(cancelInFlightTicketIds.value)

  if (loading) {
    nextIds.add(queueTicketId)
  } else {
    nextIds.delete(queueTicketId)
  }

  cancelInFlightTicketIds.value = nextIds
}

function clearQueueActionMessages(): void {
  callApiError.value = null
  callSuccess.value = null
  skipApiError.value = null
  skipSuccess.value = null
  rejoinApiError.value = null
  rejoinSuccess.value = null
  cancelApiError.value = null
  cancelSuccess.value = null
}

function createCallIdempotencyKey(): string {
  const prefix = 'queue:call'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createIdempotencyKey(): string {
  const prefix = 'queue:skip'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createRejoinIdempotencyKey(): string {
  const prefix = 'queue:rejoin'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createCancelIdempotencyKey(): string {
  const prefix = 'queue:cancel'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function queueSeatRoute(item: QueueTicketListItem): RouteLocationRaw {
  return {
    name: 'seating-from-called-queue',
    params: {
      storeId: storeId.value
    },
    query: {
      queueTicketId: item.queueTicketId
    }
  }
}

function formatStoreDateTime(value: string | null | undefined): string {
  if (!value) {
    return '未返回'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: storeTimezone,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function statusClass(status: string): string {
  return `status-${status.replace(/_/g, '-')}`
}

function optionalDisplay(value: string | null | undefined): string {
  return value?.trim() ? value : '未返回'
}

function assignedResourceText(item: QueueTicketListItem): string {
  const code = item.assignedResourceCode?.trim()

  if (!code) {
    return '未指定'
  }

  return item.assignedResourceType === 'table_group' ? `桌组 ${code}` : `桌号 ${code}`
}

function isCalledTicket(item: QueueTicketListItem): boolean {
  return item.queueTicketStatus === 'called'
}

function createLocalError(code: string, messageKey: string): QueueTicketListApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createCallLocalError(code: string, messageKey: string): QueueCallApiErrorResponse {
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

function createSkipLocalError(code: string, messageKey: string): QueueSkipApiErrorResponse {
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

function createCancelLocalError(code: string, messageKey: string): QueueCancelApiErrorResponse {
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

function createRejoinLocalError(code: string, messageKey: string): QueueRejoinApiErrorResponse {
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
  <main class="staff-workbench-shell staff-workbench-shell--padded page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>排队列表</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <section class="filter-panel" aria-label="排队列表筛选">
      <section class="status-filter" aria-label="状态筛选">
        <p>状态筛选</p>
        <div class="status-options">
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
        </div>
      </section>

      <div class="page-summary" aria-live="polite">
        <span>分页</span>
        <strong>{{ pageRangeText }}</strong>
        <small>limit {{ page.limit }} / offset {{ page.offset }} / total {{ page.total }}</small>
      </div>
    </section>

    <section v-if="isLoading" class="state-panel" aria-live="polite">
      <h2>加载中...</h2>
      <p>正在读取当前门店排队票。</p>
    </section>

    <section v-if="apiError" class="state-panel error-panel" aria-live="assertive">
      <h2>加载失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <section v-if="callApiError" class="state-panel error-panel" aria-live="assertive">
      <h2>叫号失败</h2>
      <p class="error-code">错误代码：{{ callApiError.error.code }}</p>
      <p class="message-key">消息键：{{ callApiError.error.messageKey }}</p>
    </section>

    <section v-if="skipApiError" class="state-panel error-panel" aria-live="assertive">
      <h2>过号失败</h2>
      <p class="error-code">错误代码：{{ skipApiError.error.code }}</p>
      <p class="message-key">消息键：{{ skipApiError.error.messageKey }}</p>
    </section>

    <section v-if="rejoinApiError" class="state-panel error-panel" aria-live="assertive">
      <h2>重新入队失败</h2>
      <p class="error-code">错误代码：{{ rejoinApiError.error.code }}</p>
      <p class="message-key">消息键：{{ rejoinApiError.error.messageKey }}</p>
    </section>

    <section v-if="cancelApiError" class="state-panel error-panel" aria-live="assertive">
      <h2>取消失败</h2>
      <p class="error-code">错误代码：{{ cancelApiError.error.code }}</p>
      <p class="message-key">消息键：{{ cancelApiError.error.messageKey }}</p>
    </section>

    <section v-if="callSuccess" class="state-panel success-panel" aria-live="polite">
      <h2>{{ callSuccess.alreadyCalled ? '叫号已完成' : '叫号成功' }}</h2>
      <p>排队号码 #{{ callSuccess.queueTicketNumber }}，保留到 {{ formatStoreDateTime(callSuccess.holdUntilAt) }}</p>
    </section>

    <section v-if="skipSuccess" class="state-panel success-panel" aria-live="polite">
      <h2>{{ skipSuccess.alreadySkipped ? '过号已完成' : '过号成功' }}</h2>
      <p>排队号码 #{{ skipSuccess.queueTicketNumber }}</p>
    </section>

    <section v-if="rejoinSuccess" class="state-panel success-panel" aria-live="polite">
      <h2>{{ rejoinSuccess.alreadyRejoined ? '重新入队已完成' : '重新入队成功' }}</h2>
      <p>排队号码 #{{ rejoinSuccess.queueTicketNumber }}</p>
    </section>

    <section v-if="cancelSuccess" class="state-panel success-panel" aria-live="polite">
      <h2>{{ cancelSuccess.alreadyCancelled ? '取消已完成' : '取消成功' }}</h2>
      <p>排队号码 #{{ cancelSuccess.queueTicketNumber }}</p>
    </section>

    <section v-if="showEmptyState" class="state-panel" aria-live="polite">
      <h2>暂无排队票</h2>
      <p>可以切换状态筛选或刷新列表。</p>
    </section>

    <section v-if="items.length" class="queue-list" aria-label="排队票列表">
      <article v-for="item in items" :key="item.queueTicketId" class="queue-card">
        <header class="card-header">
          <div>
            <span>排队号码</span>
            <strong>#{{ item.queueTicketNumber }}</strong>
          </div>
          <span class="status-pill" :class="statusClass(item.queueTicketStatus)">
            {{ statusLabel(item.queueTicketStatus) }}
          </span>
        </header>

        <section v-if="isCalledTicket(item)" class="hold-highlight" aria-label="叫号保留时间">
          <span>保留到</span>
          <strong>{{ formatStoreDateTime(item.holdUntilAt) }}</strong>
        </section>

        <p class="raw-status">状态代码：{{ item.queueTicketStatus }}</p>

        <section
          v-if="canShowAnyQueueAction(item)"
          class="card-actions"
          aria-label="排队票操作"
        >
          <button
            v-if="canShowCallAction(item)"
            class="call-button"
            type="button"
            :disabled="isTicketActionBusy(item)"
            @click="confirmCallTicket(item)"
          >
            {{ isCallingTicket(item) ? '叫号中...' : '叫号' }}
          </button>
          <button
            v-if="canShowSkipAction(item)"
            class="skip-button"
            type="button"
            :disabled="isTicketActionBusy(item)"
            @click="confirmSkipTicket(item)"
          >
            {{ isSkippingTicket(item) ? '过号中...' : '过号' }}
          </button>
          <button
            v-if="canShowRejoinAction(item)"
            class="rejoin-button"
            type="button"
            :disabled="isTicketActionBusy(item)"
            @click="confirmRejoinTicket(item)"
          >
            {{ isRejoiningTicket(item) ? '入队中...' : '重新入队' }}
          </button>
          <RouterLink
            v-if="canShowSeatAction(item)"
            class="seat-link"
            :to="queueSeatRoute(item)"
          >
            {{ seatActionLabel.label }}
          </RouterLink>
          <button
            v-if="canShowCancelAction(item)"
            class="cancel-button"
            type="button"
            :disabled="isTicketActionBusy(item)"
            @click="confirmCancelTicket(item)"
          >
            {{ isCancellingTicket(item) ? '取消中...' : '取消' }}
          </button>
        </section>

        <dl class="card-details">
          <div>
            <dt>人数</dt>
            <dd>{{ item.partySize }}</dd>
          </div>
          <div>
            <dt>人数分组</dt>
            <dd>{{ item.partySizeGroup }}</dd>
          </div>
          <div>
            <dt>预约编号</dt>
            <dd>{{ optionalDisplay(item.reservationCode) }}</dd>
          </div>
          <div>
            <dt>预约状态</dt>
            <dd>{{ optionalDisplay(item.reservationStatus) }}</dd>
          </div>
          <div>
            <dt>指定桌号</dt>
            <dd>{{ assignedResourceText(item) }}</dd>
          </div>
          <div>
            <dt>客户姓名</dt>
            <dd>{{ optionalDisplay(item.customerName) }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ optionalDisplay(item.customerPhoneMasked) }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatStoreDateTime(item.createdAt) }}</dd>
          </div>
          <div>
            <dt>叫号时间</dt>
            <dd>{{ formatStoreDateTime(item.calledAt) }}</dd>
          </div>
          <div>
            <dt>保留到</dt>
            <dd>{{ formatStoreDateTime(item.holdUntilAt) }}</dd>
          </div>
          <div class="debug-row">
            <dt>排队票 ID</dt>
            <dd>{{ item.queueTicketId }}</dd>
          </div>
          <div class="debug-row">
            <dt>预约 ID</dt>
            <dd>{{ optionalDisplay(item.reservationId) }}</dd>
          </div>
          <div class="debug-row">
            <dt>过期时间</dt>
            <dd>{{ formatStoreDateTime(item.expiresAt) }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <nav class="pagination-controls" aria-label="排队列表分页">
      <button type="button" :disabled="!canGoPrevious" @click="goPrevious">上一页</button>
      <button type="button" :disabled="isLoading" @click="refresh">刷新</button>
      <button type="button" :disabled="!canGoNext" @click="goNext">下一页</button>
    </nav>

    <StaffBottomNav :store-id="storeId" active-tab="queue" />
  </main>
</template>

<style scoped>
.page-header,
.filter-panel,
.state-panel,
.queue-card {
  display: grid;
  gap: 12px;
}

.page-header {
  gap: 4px;
}

.eyebrow,
.store-context,
.raw-status,
.status-filter p,
.page-summary span,
.page-summary small {
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
.page-summary strong {
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
.queue-card {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
  padding: 14px;
}

.status-filter,
.page-summary {
  display: grid;
  gap: 8px;
}

.status-options {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.status-options button,
.pagination-controls button {
  background: #f8fafc;
  border: 1px solid #c8d3e2;
  border-radius: 8px;
  color: #315f91;
  font-weight: 800;
  min-height: 42px;
}

.status-options button {
  flex: 0 0 auto;
  padding: 0 13px;
}

.status-options button.selected {
  background: #176b4d;
  border-color: #176b4d;
  color: #ffffff;
}

.page-summary {
  border-top: 1px solid #e4e9f2;
  padding-top: 12px;
}

.page-summary small {
  overflow-wrap: anywhere;
}

.state-panel p {
  color: #41516a;
  margin: 0;
}

.error-panel {
  border-color: #f4b8b8;
}

.success-panel {
  border-color: #a7d7be;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.message-key {
  overflow-wrap: anywhere;
}

.card-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.call-button,
.skip-button,
.rejoin-button,
.seat-link,
.cancel-button {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fdba74;
  border-radius: 8px;
  color: #c2410c;
  display: inline-flex;
  font-weight: 800;
  justify-content: center;
  min-height: 42px;
  padding: 0 14px;
  text-align: center;
  text-decoration: none;
}

.call-button {
  background: #eaf2ff;
  border-color: #bfdbfe;
  color: #315f91;
}

.rejoin-button {
  background: #eef6f1;
  border-color: #a7d7be;
  color: #176b4d;
}

.seat-link {
  background: #ecfdf3;
  border-color: #86efac;
  color: #047857;
}

.cancel-button {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.call-button:disabled,
.skip-button:disabled,
.rejoin-button:disabled,
.cancel-button:disabled {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #94a3b8;
  cursor: not-allowed;
}

.queue-list {
  display: grid;
  gap: 12px;
}

.queue-card {
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

.card-header span,
.hold-highlight span,
dt {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
}

.card-header strong {
  color: #14213d;
  font-size: 1.85rem;
  line-height: 1.05;
  overflow-wrap: anywhere;
}

.status-pill {
  align-items: center;
  background: #eaf2ff;
  border-radius: 999px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.78rem;
  font-weight: 800;
  justify-content: center;
  min-height: 30px;
  padding: 0 10px;
  white-space: nowrap;
}

.status-waiting {
  background: #eaf2ff;
  color: #315f91;
}

.status-called {
  background: #fff7ed;
  color: #c2410c;
}

.status-seated {
  background: #eef6f1;
  color: #176b4d;
}

.status-skipped,
.status-rejoined,
.status-expired,
.status-cancelled {
  background: #f1f5f9;
  color: #475569;
}

.hold-highlight {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  padding: 12px;
}

.hold-highlight strong {
  color: #c2410c;
  font-size: 1.15rem;
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

.debug-row dd {
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 0.84rem;
}

.pagination-controls {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.pagination-controls button {
  min-width: 0;
  padding: 0 8px;
}

.pagination-controls button:disabled {
  background: #eef2f7;
  color: #94a3b8;
  cursor: not-allowed;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.28);
  outline-offset: 2px;
}

@media (min-width: 720px) {
  h1 {
    font-size: 2rem;
  }

  .filter-panel {
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 0.7fr);
  }

  .page-summary {
    border-left: 1px solid #e4e9f2;
    border-top: 0;
    padding-left: 12px;
    padding-top: 0;
  }
}
</style>
