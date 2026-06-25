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
import { fetchTableResources } from '../api/tableResourceApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
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
import type { TableResourceItem } from '../types/tableResource'

type UiStatusFilter =
  | 'all'
  | 'waiting'
  | 'called'
  | 'skipped'
  | 'rejoined'
  | 'seated'
  | 'cancelled'
  | 'expired'

type QueueTicketDisplaySource = {
  queueTicketId: string
  queueTicketNumber: number
  queueTicketDisplayNumber?: string | null
}

const route = useRoute()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const todayListLimit = 100
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
const allTableAreasValue = 'all'
const unassignedTableAreaValue = '__unassigned__'
const unassignedTableAreaLabel = '未指定分区'
const allPartySizeGroupsValue = 'all'

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
const selectedTableArea = ref(allTableAreasValue)
const selectedPartySizeGroup = ref(allPartySizeGroupsValue)
const phoneFilter = ref('')
const isLoading = ref(false)
const response = ref<QueueTicketListResponse | null>(null)
const apiError = ref<QueueTicketListApiErrorResponse | null>(null)
const apps = ref<MeAppEntry[]>([])
const tableResources = ref<TableResourceItem[]>([])
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
const actionDisplayNumbers = ref<Record<string, string>>({})
let loadSequence = 0
let appsLoadSequence = 0
let tableResourcesLoadSequence = 0

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
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const items = computed(() => response.value?.items ?? [])
const visibleItems = computed(() => {
  if (selectedPartySizeGroup.value === allPartySizeGroupsValue) {
    return items.value
  }

  return items.value.filter(item => queueGroupKey(item) === selectedPartySizeGroup.value)
})
const resultCountLabel = computed(() => formatQueueGroupCount(visibleItems.value.length, totalPartySize(visibleItems.value)))
const normalizedPhoneFilter = computed(() => phoneFilter.value.replace(/\D/g, '').slice(0, 16))
const tableAreaOptions = computed(() => {
  const areaCounts = new Map<string, number>()

  for (const resource of tableResources.value) {
    if (resource.resourceType !== 'dining_table') {
      continue
    }

    const title = areaTitle(resource)
    areaCounts.set(title, (areaCounts.get(title) ?? 0) + 1)
  }

  const areaOptions = Array.from(areaCounts, ([label, count]) => ({
    value: label,
    label,
    count
  })).sort((first, second) => first.label.localeCompare(second.label, 'zh-CN'))

  return [
    {
      value: allTableAreasValue,
      label: '全部分区',
      count: tableResources.value.filter(resource => resource.resourceType === 'dining_table').length
    },
    {
      value: unassignedTableAreaValue,
      label: unassignedTableAreaLabel
    },
    ...areaOptions
  ]
})
const queueGroupOptions = computed(() => {
  const groupCounts = new Map<string, { label: string; partyCount: number; peopleCount: number }>()

  for (const item of items.value) {
    const value = queueGroupKey(item)
    const existing = groupCounts.get(value)

    if (existing) {
      existing.partyCount += 1
      existing.peopleCount += item.partySize
      continue
    }

    groupCounts.set(value, {
      label: queueGroupLabel(item.partySizeGroup),
      partyCount: 1,
      peopleCount: item.partySize
    })
  }

  if (selectedPartySizeGroup.value !== allPartySizeGroupsValue && !groupCounts.has(selectedPartySizeGroup.value)) {
    groupCounts.set(selectedPartySizeGroup.value, {
      label: selectedPartySizeGroup.value,
      partyCount: 0,
      peopleCount: 0
    })
  }

  const options = Array.from(groupCounts, ([value, option]) => ({
    value,
    ...option
  })).sort((first, second) => compareQueueGroups(first.label, second.label))

  return [
    {
      value: allPartySizeGroupsValue,
      label: '全部人数组',
      partyCount: items.value.length,
      peopleCount: totalPartySize(items.value)
    },
    ...options
  ]
})
const activeFilterCount = computed(
  () =>
    Number(selectedTableArea.value !== allTableAreasValue) +
    Number(selectedPartySizeGroup.value !== allPartySizeGroupsValue) +
    Number(!!normalizedPhoneFilter.value)
)
const reservationQueueEntry = computed(() =>
  apps.value.find(app => app.appKey === 'reservation_queue' && app.entryVisible)
)
const appStatusLabel = computed(() => {
  if (appsLoading.value) {
    return '检查中'
  }

  return reservationQueueEntry.value ? '排队' : '暂无应用'
})
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
  () => !isLoading.value && !apiError.value && !!response.value && visibleItems.value.length === 0
)

watch(
  [storeId, selectedStatus, selectedTableArea, normalizedPhoneFilter],
  () => {
    void loadQueueTickets()
  },
  { immediate: true }
)

watch(
  storeId,
  nextStoreId => {
    void loadTableResources(nextStoreId)
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
    limit: todayListLimit,
    offset: 0
  }

  if (selectedStatus.value !== 'all') {
    query.status = selectedStatus.value
  }

  if (selectedTableArea.value !== allTableAreasValue) {
    query.tableArea = selectedTableArea.value
  }

  if (normalizedPhoneFilter.value) {
    query.phone = normalizedPhoneFilter.value
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

async function loadTableResources(nextStoreId = storeId.value): Promise<void> {
  const sequence = ++tableResourcesLoadSequence
  tableResources.value = []

  if (!nextStoreId) {
    return
  }

  try {
    const result = await fetchTableResources(nextStoreId)

    if (sequence === tableResourcesLoadSequence) {
      tableResources.value = result.resources
    }
  } catch {
    if (sequence === tableResourcesLoadSequence) {
      tableResources.value = []
    }
  }
}

function selectStatus(status: UiStatusFilter): void {
  if (selectedStatus.value === status) {
    return
  }

  selectedStatus.value = status
}

function selectTableArea(tableArea: string): void {
  if (selectedTableArea.value === tableArea) {
    return
  }

  selectedTableArea.value = tableArea
}

function selectPartySizeGroup(partySizeGroup: string): void {
  if (selectedPartySizeGroup.value === partySizeGroup) {
    return
  }

  selectedPartySizeGroup.value = partySizeGroup
}

function resetReceptionFilters(): void {
  selectedTableArea.value = allTableAreasValue
  selectedPartySizeGroup.value = allPartySizeGroupsValue
  phoneFilter.value = ''
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
  return canCallQueueTicket.value && ['waiting', 'called', 'rejoined'].includes(item.queueTicketStatus)
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

  const actionLabel = item.queueTicketStatus === 'called' ? '重复叫号' : '叫号'
  if (!globalThis.confirm(`确认${actionLabel} ${queueTicketDisplayText(item)}？`)) {
    return
  }

  void executeConfirmedCall(item)
}

function confirmSkipTicket(item: QueueTicketListItem): void {
  if (!canShowSkipAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认过号 ${queueTicketDisplayText(item)}？`)) {
    return
  }

  void executeConfirmedSkip(item)
}

function confirmRejoinTicket(item: QueueTicketListItem): void {
  if (!canShowRejoinAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认重新入队 ${queueTicketDisplayText(item)}？`)) {
    return
  }

  void executeConfirmedRejoin(item)
}

function confirmCancelTicket(item: QueueTicketListItem): void {
  if (!canShowCancelAction(item) || isTicketActionBusy(item)) {
    return
  }

  clearQueueActionMessages()

  if (!globalThis.confirm(`确认取消 ${queueTicketDisplayText(item)}？`)) {
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
  rememberQueueTicketDisplayNumber(item)
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
  rememberQueueTicketDisplayNumber(item)
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
  rememberQueueTicketDisplayNumber(item)
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
  rememberQueueTicketDisplayNumber(item)
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
  await loadQueueTickets()
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

function customerDisplayName(value: string | null | undefined): string {
  if (value?.trim()) {
    return value.trim()
  }

  return '顾客'
}

function queueTicketDisplayValue(item: QueueTicketListItem): string {
  return item.queueTicketDisplayNumber?.trim() || String(item.queueTicketNumber)
}

function queueTicketDisplayText(item: QueueTicketListItem): string {
  return `#${queueTicketDisplayValue(item)}`
}

function rememberQueueTicketDisplayNumber(item: QueueTicketListItem): void {
  actionDisplayNumbers.value = {
    ...actionDisplayNumbers.value,
    [item.queueTicketId]: queueTicketDisplayValue(item)
  }
}

function queueTicketDisplayTextByResult(result: QueueTicketDisplaySource): string {
  const displayNumber =
    result.queueTicketDisplayNumber?.trim() ||
    actionDisplayNumbers.value[result.queueTicketId]?.trim() ||
    items.value.find(item => item.queueTicketId === result.queueTicketId)?.queueTicketDisplayNumber?.trim() ||
    String(result.queueTicketNumber)

  return `#${displayNumber}`
}

function totalPartySize(queueItems: QueueTicketListItem[]): number {
  return queueItems.reduce((total, item) => total + item.partySize, 0)
}

function queueGroupKey(item: QueueTicketListItem): string {
  return queueGroupLabel(item.partySizeGroup)
}

function queueGroupLabel(value: string | null | undefined): string {
  return value?.trim() ? value.trim() : '未分组'
}

function formatQueueGroupCount(partyCount: number, peopleCount: number): string {
  return `${partyCount}组 / ${peopleCount}人`
}

function compareQueueGroups(first: string, second: string): number {
  const firstMin = leadingNumber(first)
  const secondMin = leadingNumber(second)

  if (firstMin !== null && secondMin !== null && firstMin !== secondMin) {
    return firstMin - secondMin
  }

  if (firstMin !== null && secondMin === null) {
    return -1
  }

  if (firstMin === null && secondMin !== null) {
    return 1
  }

  return first.localeCompare(second, 'zh-CN')
}

function leadingNumber(value: string): number | null {
  const match = value.match(/\d+/)
  return match ? Number(match[0]) : null
}

function queueTimeText(item: QueueTicketListItem): string {
  if (item.calledAt) {
    return `叫号 ${formatStoreDateTime(item.calledAt)}`
  }

  return `取号 ${formatStoreDateTime(item.createdAt)}`
}

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return '默认门店'
  }

  return `门店 ${value.slice(0, 8)}`
}

function assignedResourceText(item: QueueTicketListItem): string {
  const label = item.assignedResourceLabel?.trim()

  if (label) {
    return label
  }

  const code = item.assignedResourceCode?.trim()

  if (!code) {
    return '未指定'
  }

  return item.assignedResourceType === 'table_group' ? `桌组 ${code}` : `桌号 ${code}`
}

function assignedAreaText(item: QueueTicketListItem): string {
  const areaName = item.assignedResourceAreaName?.trim()
  return areaName ? `分区 ${areaName}` : '分区未指定'
}

function areaTitle(resource: TableResourceItem): string {
  const areaName = resource.areaName?.trim()
  return areaName || '未分区'
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
  <main class="staff-workbench-shell queue-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="queue-workbench-body">
      <section class="queue-management-panel" aria-label="当日排队管理">
        <header class="queue-panel-heading">
          <div>
            <p class="section-kicker">营业日期 {{ currentBusinessDate }}</p>
            <h1>当日排队管理</h1>
          </div>
          <button type="button" :disabled="isLoading" @click="refresh">
            {{ isLoading ? '刷新中' : '刷新' }}
          </button>
        </header>

        <div class="queue-toolbar today-queue-management">
          <section class="queue-status-tabs" aria-label="状态筛选">
            <p>当日队列</p>
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

          <section class="queue-list-filters compact-queue-filters" aria-label="叫号筛选">
            <div class="table-area-filter compact-table-area-filter" aria-label="桌台分区">
              <div class="compact-filter-heading">
                <p>桌台分区</p>
                <span>{{ activeFilterCount ? `${resultCountLabel} · 已选 ${activeFilterCount} 项` : resultCountLabel }}</span>
              </div>
              <div class="filter-chip-row">
                <button
                  v-for="option in tableAreaOptions"
                  :key="option.value"
                  :aria-pressed="selectedTableArea === option.value"
                  :class="{ selected: selectedTableArea === option.value }"
                  class="filter-chip"
                  type="button"
                  @click="selectTableArea(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div class="party-group-filter" aria-label="人数组">
              <div class="compact-filter-heading">
                <p>人数组</p>
                <span>当前队列</span>
              </div>
              <div class="filter-chip-row">
                <button
                  v-for="option in queueGroupOptions"
                  :key="option.value"
                  :aria-pressed="selectedPartySizeGroup === option.value"
                  :class="{ selected: selectedPartySizeGroup === option.value }"
                  class="filter-chip queue-group-chip"
                  type="button"
                  @click="selectPartySizeGroup(option.value)"
                >
                  <strong>{{ option.label }}</strong>
                  <span>{{ formatQueueGroupCount(option.partyCount, option.peopleCount) }}</span>
                </button>
              </div>
            </div>

            <div class="queue-filter-controls compact-filter-controls">
              <label class="queue-phone-filter">
                <span class="visually-hidden">手机号</span>
                <input
                  v-model="phoneFilter"
                  autocomplete="off"
                  inputmode="numeric"
                  name="queuePhoneFilter"
                  placeholder="手机号"
                  type="search"
                />
              </label>

              <button class="reset-filter-button" type="button" @click="resetReceptionFilters">
                重置
              </button>
            </div>
          </section>
        </div>
      </section>

      <section class="queue-message-stack" aria-label="排队操作反馈">
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
          <p>排队号码 {{ queueTicketDisplayTextByResult(callSuccess) }}，保留到 {{ formatStoreDateTime(callSuccess.holdUntilAt) }}</p>
        </section>

        <section v-if="skipSuccess" class="state-panel success-panel" aria-live="polite">
          <h2>{{ skipSuccess.alreadySkipped ? '过号已完成' : '过号成功' }}</h2>
          <p>排队号码 {{ queueTicketDisplayTextByResult(skipSuccess) }}</p>
        </section>

        <section v-if="rejoinSuccess" class="state-panel success-panel" aria-live="polite">
          <h2>{{ rejoinSuccess.alreadyRejoined ? '重新入队已完成' : '重新入队成功' }}</h2>
          <p>排队号码 {{ queueTicketDisplayTextByResult(rejoinSuccess) }}</p>
        </section>

        <section v-if="cancelSuccess" class="state-panel success-panel" aria-live="polite">
          <h2>{{ cancelSuccess.alreadyCancelled ? '取消已完成' : '取消成功' }}</h2>
          <p>排队号码 {{ queueTicketDisplayTextByResult(cancelSuccess) }}</p>
        </section>
      </section>

      <section v-if="showEmptyState" class="empty-queue-panel" aria-live="polite">
        <h2>暂无排队票</h2>
        <p>当前状态下暂无当日排队票，可以切换状态筛选或刷新列表。</p>
      </section>

      <section v-if="visibleItems.length" class="queue-list" aria-label="当日排队列表">
        <article v-for="item in visibleItems" :key="item.queueTicketId" class="compact-ticket-card">
          <div class="compact-ticket-main">
            <div class="compact-ticket-number">
              {{ queueTicketDisplayText(item) }}
            </div>

            <div class="compact-ticket-info">
              <div class="compact-ticket-title">
                <strong>{{ customerDisplayName(item.customerName) }}</strong>
                <span class="status-pill" :class="statusClass(item.queueTicketStatus)">
                  {{ statusLabel(item.queueTicketStatus) }}
                </span>
              </div>

              <p>
                {{ item.partySize }}人 · {{ item.partySizeGroup }} ·
                {{ optionalDisplay(item.customerPhoneMasked) }}
              </p>
              <p>{{ assignedAreaText(item) }} · {{ assignedResourceText(item) }}</p>
              <p>{{ queueTimeText(item) }}</p>
            </div>
          </div>

          <p v-if="isCalledTicket(item)" class="hold-highlight">保留到 {{ formatStoreDateTime(item.holdUntilAt) }}</p>

          <section
            v-if="canShowAnyQueueAction(item)"
            class="compact-ticket-actions"
            aria-label="排队票操作"
          >
            <button
              v-if="canShowCallAction(item)"
              class="call-button"
              type="button"
              :disabled="isTicketActionBusy(item)"
              @click="confirmCallTicket(item)"
            >
              {{ isCallingTicket(item) ? '叫号中...' : item.queueTicketStatus === 'called' ? '重复叫号' : '叫号' }}
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
        </article>
      </section>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="queue" />
  </main>
</template>

<style scoped>
.queue-workbench-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.queue-management-panel,
.state-panel,
.empty-queue-panel,
.compact-ticket-card {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
}

.queue-management-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.queue-panel-heading {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.queue-panel-heading > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.section-kicker,
.queue-status-tabs p,
.compact-ticket-info p {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 0;
}

h1,
h2,
.compact-ticket-info strong {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.24rem;
  line-height: 1.15;
}

h2 {
  font-size: 0.98rem;
}

.queue-panel-heading button,
.status-options button,
.filter-chip,
.reset-filter-button,
.call-button,
.skip-button,
.rejoin-button,
.seat-link,
.cancel-button {
  align-items: center;
  border-radius: 10px;
  display: inline-flex;
  font-weight: 900;
  justify-content: center;
  letter-spacing: 0;
  min-height: 42px;
  text-align: center;
  text-decoration: none;
}

.queue-panel-heading button {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  color: #315f91;
  padding: 0 14px;
}

.queue-toolbar {
  display: grid;
  gap: 12px;
}

.queue-status-tabs,
.today-queue-management {
  display: grid;
  gap: 9px;
}

.status-options,
.filter-chip-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.status-options button,
.filter-chip {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  color: #315f91;
  flex: 0 0 auto;
  padding: 0 13px;
}

.status-options button.selected,
.filter-chip.selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.queue-list-filters {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 8px;
  padding: 8px;
}

.compact-filter-heading {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.compact-filter-heading p {
  color: #14213d;
  font-size: 0.86rem;
  font-weight: 900;
  margin: 0;
}

.compact-filter-heading span,
.filter-chip span {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 900;
}

.filter-chip.selected span {
  color: #ffffff;
}

.table-area-filter,
.party-group-filter {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.queue-filter-controls {
  display: grid;
  gap: 8px;
}

.queue-phone-filter {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.queue-phone-filter input {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #0f172a;
  font-weight: 800;
  min-height: 38px;
  outline: none;
  padding: 0 14px;
  width: 100%;
}

.queue-group-chip {
  align-items: flex-start;
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  min-height: 46px;
  padding: 6px 13px;
}

.queue-group-chip strong {
  font-size: 0.88rem;
  line-height: 1.1;
}

.queue-phone-filter input:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.14);
}

.compact-filter-controls {
  align-items: center;
  grid-template-columns: minmax(0, 1fr) auto;
}

.visually-hidden {
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  height: 1px;
  overflow: hidden;
  position: absolute;
  white-space: nowrap;
  width: 1px;
}

.reset-filter-button {
  background: #eef2f7;
  border: 1px solid #cbd5e1;
  color: #315f91;
  min-height: 38px;
  padding: 0 13px;
}

.queue-message-stack {
  display: grid;
  gap: 10px;
}

.state-panel,
.empty-queue-panel {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.state-panel p,
.empty-queue-panel p {
  color: #475569;
  margin: 0;
}

.error-panel {
  border-color: #fecaca;
}

.success-panel {
  border-color: #bbf7d0;
}

.error-code {
  color: #b42318;
  font-weight: 900;
  overflow-wrap: anywhere;
}

.message-key {
  overflow-wrap: anywhere;
}

.queue-list {
  display: grid;
  gap: 8px;
}

.compact-ticket-card {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.compact-ticket-main {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: auto minmax(0, 1fr);
}

.compact-ticket-number {
  color: #f97316;
  font-size: 1.62rem;
  font-weight: 950;
  line-height: 1.05;
  min-width: 44px;
  overflow-wrap: anywhere;
}

.compact-ticket-info {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.compact-ticket-title {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  min-width: 0;
}

.compact-ticket-info strong {
  font-size: 0.98rem;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.status-pill {
  align-items: center;
  background: #eaf2ff;
  border-radius: 999px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.76rem;
  font-weight: 900;
  justify-content: center;
  min-height: 28px;
  padding: 0 9px;
  white-space: nowrap;
}

.status-waiting {
  background: #fff7ed;
  color: #c2410c;
}

.status-called {
  background: #fef3c7;
  color: #b45309;
}

.status-seated {
  background: #dcfce7;
  color: #047857;
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
  border-radius: 10px;
  color: #c2410c;
  font-size: 0.82rem;
  font-weight: 900;
  margin: 0;
  padding: 8px 10px;
}

.compact-ticket-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.call-button,
.skip-button,
.rejoin-button,
.seat-link,
.cancel-button {
  border: 1px solid transparent;
  min-width: 0;
  padding: 0 10px;
}

.call-button {
  background: #ffedd5;
  border-color: #fdba74;
  color: #c2410c;
}

.skip-button {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #475569;
}

.rejoin-button {
  background: #eaf2ff;
  border-color: #bfdbfe;
  color: #315f91;
}

.seat-link {
  background: #dcfce7;
  border-color: #86efac;
  color: #047857;
}

.cancel-button {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

button:disabled,
.call-button:disabled,
.skip-button:disabled,
.rejoin-button:disabled,
.cancel-button:disabled {
  background: #eef2f7;
  border-color: #cbd5e1;
  color: #94a3b8;
  cursor: not-allowed;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (min-width: 720px) {
  .queue-workbench-body {
    padding-top: 16px;
  }

  .queue-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .compact-filter-controls {
    align-items: end;
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
