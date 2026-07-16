<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  CleaningApiError,
  completeCleaning,
  startCleaning
} from '../api/cleaningApi'
import {
  completeReservation,
  ReservationStatusActionApiError
} from '../api/reservationStatusActionApi'
import {
  checkInAndSeatConfirmedReservation,
  ReservationArrivedDirectSeatingApiError,
  seatArrivedReservation
} from '../api/reservationArrivedDirectSeatingApi'
import { getReservationCalendarSummary } from '../api/reservationCalendarSummaryApi'
import {
  SeatingFromCalledQueueApiError,
  seatCalledQueueTicket
} from '../api/seatingFromCalledQueueApi'
import {
  fetchTableResources,
  TableResourceApiError
} from '../api/tableResourceApi'
import {
  dissolveTemporaryTableGroup,
  saveTemporaryTableGroup,
  TemporaryTableGroupApiError
} from '../api/temporaryTableGroupApi'
import ReservationTableSwitchDialog from '../components/reservation-workbench/ReservationTableSwitchDialog.vue'
import StaffPrimaryWorkbench from '../components/staff/StaffPrimaryWorkbench.vue'
import StaffBusinessDateSwitcher from '../components/staff/StaffBusinessDateSwitcher.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useStoreContextStore } from '../stores/storeContext'
import { formatAppGateErrorMessage, formatAppGateErrorTitle } from '../utils/appGateErrorMessages'
import type {
  TableResourceApiErrorResponse,
  TableResourceItem,
  TableResourceListResponse
} from '../types/tableResource'
import type { CleaningApiErrorResponse } from '../types/cleaning'
import type { ReservationArrivedDirectSeatingApiErrorResponse } from '../types/reservationArrivedDirectSeating'
import type { ReservationStatusActionApiErrorResponse } from '../types/reservationStatusAction'
import type { SeatingFromCalledQueueApiErrorResponse } from '../types/seatingFromCalledQueue'
import type { SwitchTableResponse } from '../types/tableSwitch'
import type { TemporaryTableGroupApiErrorResponse } from '../types/temporaryTableGroup'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

type StatusFilter = 'all' | 'available' | 'reserved' | 'occupied' | 'cleaning' | 'active'
type AreaFilter = 'all' | string

interface SummaryItem {
  key: StatusFilter
  label: string
  value: number
}

interface AreaFilterOption {
  value: AreaFilter
  label: string
  count: number
}

type TableActionErrorResponse =
  | CleaningApiErrorResponse
  | ReservationArrivedDirectSeatingApiErrorResponse
  | ReservationStatusActionApiErrorResponse
  | SeatingFromCalledQueueApiErrorResponse
  | TemporaryTableGroupApiErrorResponse
type BulkCleaningMode = 'start' | 'complete'

const APP_GATE_BLOCKING_ERROR_CODES = new Set([
  'APP_DISABLED',
  'PERMISSION_DENIED',
  'STORE_APP_NOT_ENABLED',
  'TENANT_APP_EXPIRED',
  'TENANT_APP_NOT_ENABLED'
])

const route = useRoute()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const statusOptions: Array<{ value: StatusFilter; label: string }> = [
  { value: 'all', label: gt('generated.table-resource-list.073') },
  { value: 'available', label: gt('generated.table-resource-list.074') },
  { value: 'reserved', label: gt('generated.table-resource-list.075') },
  { value: 'occupied', label: gt('generated.table-resource-list.076') },
  { value: 'cleaning', label: gt('generated.table-resource-list.077') },
  { value: 'active', label: gt('generated.table-resource-list.078') }
]

const statusLabels: Record<string, string> = {
  available: gt('generated.table-resource-list.079'),
  occupied: gt('generated.table-resource-list.080'),
  cleaning: gt('generated.table-resource-list.081'),
  locked: gt('generated.table-resource-list.082'),
  reserved: gt('generated.table-resource-list.083'),
  inactive: gt('generated.table-resource-list.084'),
  active: gt('generated.table-resource-list.085'),
  created: gt('generated.table-resource-list.086'),
  released: gt('generated.table-resource-list.087'),
  ended: gt('generated.table-resource-list.088')
}

const reservationStatusLabels: Record<string, string> = {
  confirmed: gt('generated.table-resource-list.089'),
  arrived: gt('generated.table-resource-list.090'),
  seated: gt('generated.table-resource-list.091'),
  cancelled: gt('generated.table-resource-list.092'),
  no_show: gt('generated.table-resource-list.093'),
  completed: gt('generated.table-resource-list.094')
}

const queueStatusLabels: Record<string, string> = {
  waiting: gt('generated.table-resource-list.095'),
  called: gt('generated.table-resource-list.096'),
  skipped: gt('generated.table-resource-list.097'),
  rejoined: gt('generated.table-resource-list.098'),
  seated: gt('generated.table-resource-list.099'),
  cancelled: gt('generated.table-resource-list.100'),
  expired: gt('generated.table-resource-list.101')
}

const partySizeOptions = [2, 4, 6, 8, 10, 12]

const selectedStatus = ref<StatusFilter>('all')
const selectedArea = ref<AreaFilter>('all')
const selectedBusinessDate = ref(todayDateInput())
const partySize = ref<number | null>(null)
const temporaryGroupMode = ref(false)
const temporaryGroupName = ref('')
const selectedTemporaryTableIds = ref<string[]>([])
const isLoading = ref(false)
const savingTemporaryGroup = ref(false)
const dissolvingTemporaryGroupId = ref<string | null>(null)
const startingCleaningResourceId = ref<string | null>(null)
const completingCleaningResourceId = ref<string | null>(null)
const bulkCleaningMode = ref<BulkCleaningMode | null>(null)
const bulkCleaningCompletedCount = ref(0)
const bulkCleaningTotalCount = ref(0)
const seatingResourceId = ref<string | null>(null)
const showTableSwitchDialog = ref(false)
const selectedSwitchResource = ref<TableResourceItem | null>(null)
const response = ref<TableResourceListResponse | null>(null)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
const actionError = ref<TableActionErrorResponse | null>(null)
const visibleMonthKey = ref(monthKeyFromDate(selectedBusinessDate.value))
const reservationCounts = ref<Record<string, number>>({})
let loadSequence = 0
let calendarSummaryLoadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isLoading.value ? gt('generated.table-resource-list.102') : gt('generated.table-resource-list.103')))
const resources = computed(() => response.value?.resources ?? [])
const allTableResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'dining_table')
)
const groupResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'table_group')
)
const displayableTableResources = computed(() =>
  allTableResources.value.filter(resource => !isTemporaryGroupMember(resource))
)
const statusFilteredTableResources = computed(() => {
  if (selectedStatus.value === 'active') {
    return []
  }

  if (selectedStatus.value === 'all') {
    return displayableTableResources.value
  }

  if (selectedStatus.value === 'available') {
    return displayableTableResources.value.filter(resource => resource.selectable)
  }

  if (selectedStatus.value === 'cleaning') {
    return displayableTableResources.value.filter(isCleaningWorkflowResource)
  }

  return displayableTableResources.value.filter(resource => resource.status === selectedStatus.value)
})
const tableResources = computed(() =>
  selectedArea.value === 'all'
    ? statusFilteredTableResources.value
    : statusFilteredTableResources.value.filter(resource => areaTitle(resource) === selectedArea.value)
)
const displayedGroupResources = computed(() =>
  selectedStatus.value === 'all' || selectedStatus.value === 'active' ? groupResources.value : []
)
const groupedAreaResources = computed(() => {
  const areaMap = new Map<string, TableResourceItem[]>()

  for (const resource of tableResources.value) {
    const title = areaTitle(resource)
    areaMap.set(title, [...(areaMap.get(title) ?? []), resource])
  }

  return Array.from(areaMap, ([title, items]) => ({ title, items }))
})
const areaFilterOptions = computed<AreaFilterOption[]>(() => {
  const areaCounts = new Map<string, number>()

  for (const resource of statusFilteredTableResources.value) {
    const title = areaTitle(resource)
    areaCounts.set(title, (areaCounts.get(title) ?? 0) + 1)
  }

  return [
    { value: 'all', label: gt('generated.table-resource-list.104'), count: statusFilteredTableResources.value.length },
    ...Array.from(areaCounts, ([label, count]) => ({ value: label, label, count }))
  ]
})
const summaryItems = computed<SummaryItem[]>(() =>
  statusOptions.map(option => ({
    key: option.value,
    label: option.label,
    value: statusFilterCount(option.value)
  }))
)
const displayedResourceCount = computed(() => tableResources.value.length + displayedGroupResources.value.length)
const isSelectedBusinessDateToday = computed(() => selectedBusinessDate.value === todayDateInput())
const bulkStartCleaningResources = computed(() => tableResources.value.filter(canStartCleaning))
const bulkCompleteCleaningResources = computed(() => tableResources.value.filter(canCompleteCleaning))
const tableSwitchDialogItem = computed(() => {
  const resource = selectedSwitchResource.value

  if (!resource?.currentSeatingId) {
    return null
  }

  return {
    reservationCode: resource.code,
    customerName: resource.preassignedReservationId
      ? preassignedCustomerText(resource)
      : resource.displayName || resource.code,
    partySize: resource.currentPartySize ?? resource.preassignedPartySize ?? resource.capacityMin,
    businessDate: selectedBusinessDate.value,
    seatingId: resource.currentSeatingId,
    currentResourceCode: resource.displayName || resource.code
  }
})
const partySizeValue = computed({
  get: () => (partySize.value === null ? '' : String(partySize.value)),
  set: value => {
    partySize.value = value ? Number(value) : null
  }
})
const selectedTemporaryTableIdSet = computed(() => new Set(selectedTemporaryTableIds.value))
const isBulkCleaning = computed(() => bulkCleaningMode.value !== null)
const activeTableActionInProgress = computed(
  () =>
    savingTemporaryGroup.value ||
    !!dissolvingTemporaryGroupId.value ||
    !!startingCleaningResourceId.value ||
    !!completingCleaningResourceId.value ||
    isBulkCleaning.value ||
    !!seatingResourceId.value
)
const canRunBulkStartCleaning = computed(
  () =>
    isSelectedBusinessDateToday.value &&
    bulkStartCleaningResources.value.length > 0 &&
    !activeTableActionInProgress.value
)
const canRunBulkCompleteCleaning = computed(
  () =>
    isSelectedBusinessDateToday.value &&
    bulkCompleteCleaningResources.value.length > 0 &&
    !activeTableActionInProgress.value
)
const bulkCleaningProgressText = computed(() => {
  if (bulkCleaningMode.value) {
    const action = bulkCleaningMode.value === 'start' ? gt('generated.table-resource-list.105') : gt('generated.table-resource-list.106')
    return `${action} ${bulkCleaningCompletedCount.value}/${bulkCleaningTotalCount.value}`
  }

  return `${gt('generated.table-resource-list.067')}${bulkStartCleaningResources.value.length}${gt('generated.table-resource-list.068')}${bulkCompleteCleaningResources.value.length}`
})
const selectedTemporaryTableResources = computed(() =>
  allTableResources.value.filter(resource => selectedTemporaryTableIdSet.value.has(resource.resourceId))
)
const temporaryGroupCapacity = computed(() =>
  selectedTemporaryTableResources.value.reduce(
    (capacity, resource) => ({
      min: capacity.min + resource.capacityMin,
      max: capacity.max + resource.capacityMax
    }),
    { min: 0, max: 0 }
  )
)
const temporaryGroupPartySize = computed(() => {
  const capacity = temporaryGroupCapacity.value

  if (capacity.max <= 0) {
    return partySize.value ?? 0
  }

  const requestedPartySize = partySize.value ?? capacity.min
  return Math.min(Math.max(requestedPartySize, capacity.min), capacity.max)
})
const canSeatTemporaryGroup = computed(
  () =>
    isSelectedBusinessDateToday.value &&
    selectedTemporaryTableIds.value.length >= 2 &&
    temporaryGroupPartySize.value > 0
)
const canSaveTemporaryGroup = computed(
  () =>
    temporaryGroupName.value.trim().length > 0 &&
    selectedTemporaryTableIds.value.length >= 2 &&
    !activeTableActionInProgress.value
)
const temporaryGroupRoute = computed(() => ({
  name: 'walk-in-direct-seating',
  params: {
    storeId: storeId.value
  },
  query: {
    temporaryTableIds: selectedTemporaryTableIds.value,
    partySize: temporaryGroupPartySize.value
  }
}))
const showNoConfiguredResources = computed(
  () => !isLoading.value && !apiError.value && !!response.value && resources.value.length === 0
)
const showFilteredEmpty = computed(
  () =>
    !isLoading.value &&
    !apiError.value &&
    !!response.value &&
    resources.value.length > 0 &&
    displayedResourceCount.value === 0
)
watch(
  [storeId, partySize, selectedBusinessDate],
  () => {
    void loadResources()
  },
  { immediate: true }
)

watch(
  selectedBusinessDate,
  nextBusinessDate => {
    visibleMonthKey.value = monthKeyFromDate(nextBusinessDate)
    temporaryGroupName.value = ''
    selectedTemporaryTableIds.value = []
    actionError.value = null
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
  areaFilterOptions,
  options => {
    if (!options.some(option => option.value === selectedArea.value)) {
      selectedArea.value = 'all'
    }
  }
)

watch(
  allTableResources,
  currentResources => {
    const currentIds = new Set(currentResources.map(resource => resource.resourceId))
    selectedTemporaryTableIds.value = selectedTemporaryTableIds.value.filter(id => currentIds.has(id))
  }
)

watch(
  showTableSwitchDialog,
  open => {
    if (!open) {
      selectedSwitchResource.value = null
    }
  }
)

async function loadResources(): Promise<void> {
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
    const result = await fetchTableResources(currentStoreId, {
      partySize: partySize.value ?? undefined,
      includeGroups: true,
      businessDate: selectedBusinessDate.value
    })

    if (sequence === loadSequence) {
      response.value = result
    }
  } catch (error) {
    if (sequence === loadSequence) {
      apiError.value =
        error instanceof TableResourceApiError
          ? error.response
          : createLocalError('REQUEST_FAILED', 'table.resources.request_failed')
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

function selectStatus(status: StatusFilter): void {
  selectedStatus.value = status
}

function selectArea(area: AreaFilter): void {
  selectedArea.value = area
}

function toggleTemporaryGroupMode(): void {
  temporaryGroupMode.value = !temporaryGroupMode.value

  if (!temporaryGroupMode.value) {
    selectedTemporaryTableIds.value = []
  }
}

function toggleTemporaryTable(resource: TableResourceItem): void {
  if (!canToggleTemporaryTable(resource)) {
    return
  }

  if (isTemporaryTableSelected(resource)) {
    selectedTemporaryTableIds.value = selectedTemporaryTableIds.value.filter(id => id !== resource.resourceId)
  } else {
    selectedTemporaryTableIds.value = [...selectedTemporaryTableIds.value, resource.resourceId]
  }
}

function clearTemporaryGroupSelection(): void {
  selectedTemporaryTableIds.value = []
}

async function saveSelectedTemporaryGroup(): Promise<void> {
  const currentStoreId = storeId.value
  const groupName = temporaryGroupName.value.trim()

  if (!currentStoreId || !canSaveTemporaryGroup.value) {
    actionError.value = createTemporaryGroupLocalError('GROUP_NAME_REQUIRED', 'table.temporary_group.group_name_required')
    return
  }

  actionError.value = null
  savingTemporaryGroup.value = true

  try {
    await saveTemporaryTableGroup(currentStoreId, {
      groupName,
      tableIds: selectedTemporaryTableIds.value,
      businessDate: selectedBusinessDate.value
    })
    temporaryGroupName.value = ''
    selectedTemporaryTableIds.value = []
    temporaryGroupMode.value = false
    selectedStatus.value = 'active'
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof TemporaryTableGroupApiError
        ? error.response
        : createTemporaryGroupLocalError('REQUEST_FAILED', 'table.temporary_group.request_failed')
  } finally {
    savingTemporaryGroup.value = false
  }
}

async function dissolveTemporaryGroup(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !canDissolveTemporaryGroup(resource) || activeTableActionInProgress.value) {
    actionError.value = createTemporaryGroupLocalError('GROUP_NOT_DISSOLVABLE', 'table.temporary_group.group_not_dissolvable')
    return
  }

  actionError.value = null
  dissolvingTemporaryGroupId.value = resource.resourceId

  try {
    await dissolveTemporaryTableGroup(currentStoreId, resource.resourceId)
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof TemporaryTableGroupApiError
        ? error.response
        : createTemporaryGroupLocalError('REQUEST_FAILED', 'table.temporary_group.request_failed')
  } finally {
    dissolvingTemporaryGroupId.value = null
  }
}

async function startResourceCleaning(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value
  const seatingId = resource.currentSeatingId

  if (!currentStoreId || !seatingId || !canStartCleaning(resource) || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('SEATING_NOT_FOUND', 'cleaning.seating_not_found')
    return
  }

  actionError.value = null
  startingCleaningResourceId.value = resource.resourceId

  try {
    await startCleaningForResource(currentStoreId, resource)
    await loadResources()
  } catch (error) {
    actionError.value = tableCleaningActionError(error, 'cleaning.request_failed')
  } finally {
    startingCleaningResourceId.value = null
  }
}

async function startCleaningForResource(currentStoreId: string, resource: TableResourceItem): Promise<void> {
  const seatingId = resource.currentSeatingId

  if (!seatingId) {
    throw createCleaningLocalError('SEATING_NOT_FOUND', 'cleaning.seating_not_found')
  }

  await completeReservationForResourceIfNeeded(currentStoreId, resource)
  await startCleaning(
    currentStoreId,
    seatingId,
    {
      reasonCode: 'staff_table_page_clear',
      note: `table_resource:${resource.resourceType}:${resource.code}`
    },
    createTableActionIdempotencyKey('cleaning-start', resource.resourceId)
  )
}

async function completeReservationForResourceIfNeeded(
  currentStoreId: string,
  resource: TableResourceItem
): Promise<void> {
  const reservationId =
    resource.currentReservationId ??
    (resource.preassignedReservationStatus === 'seated' ? resource.preassignedReservationId : null)

  if (!reservationId) {
    return
  }

  await completeReservation(
    currentStoreId,
    reservationId,
    {
      reasonCode: 'guest_finished',
      note: `table_resource:${resource.resourceType}:${resource.code}`
    },
    createTableActionIdempotencyKey('reservation-complete', resource.resourceId)
  )
}

async function completeResourceCleaning(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value
  const cleaningId = resource.currentCleaningId

  if (!currentStoreId || !cleaningId || !canCompleteCleaning(resource) || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('CLEANING_NOT_FOUND', 'cleaning.not_found')
    return
  }

  actionError.value = null
  completingCleaningResourceId.value = resource.resourceId

  try {
    await completeCleaningForResource(currentStoreId, resource)
    await loadResources()
  } catch (error) {
    actionError.value = tableCleaningActionError(error, 'cleaning.request_failed')
  } finally {
    completingCleaningResourceId.value = null
  }
}

async function completeCleaningForResource(currentStoreId: string, resource: TableResourceItem): Promise<void> {
  const cleaningId = resource.currentCleaningId

  if (!cleaningId) {
    throw createCleaningLocalError('CLEANING_NOT_FOUND', 'cleaning.not_found')
  }

  await completeCleaning(
    currentStoreId,
    cleaningId,
    {
      reasonCode: 'staff_table_page_complete_clear',
      note: `table_resource:${resource.resourceType}:${resource.code}`
    },
    createTableActionIdempotencyKey('cleaning-complete', resource.resourceId)
  )
}

async function startVisibleResourcesCleaning(): Promise<void> {
  const currentStoreId = storeId.value
  const targets = [...bulkStartCleaningResources.value]

  if (!currentStoreId || !targets.length || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('NO_CLEARABLE_TABLES', 'cleaning.no_clearable_tables')
    return
  }

  await runBulkCleaningAction('start', targets, async resource => {
    startingCleaningResourceId.value = resource.resourceId
    await startCleaningForResource(currentStoreId, resource)
  })
}

async function completeVisibleResourcesCleaning(): Promise<void> {
  const currentStoreId = storeId.value
  const targets = [...bulkCompleteCleaningResources.value]

  if (!currentStoreId || !targets.length || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('NO_CLEANING_TABLES', 'cleaning.no_cleaning_tables')
    return
  }

  await runBulkCleaningAction('complete', targets, async resource => {
    completingCleaningResourceId.value = resource.resourceId
    await completeCleaningForResource(currentStoreId, resource)
  })
}

async function runBulkCleaningAction(
  mode: BulkCleaningMode,
  targets: TableResourceItem[],
  action: (resource: TableResourceItem) => Promise<void>
): Promise<void> {
  actionError.value = null
  bulkCleaningMode.value = mode
  bulkCleaningCompletedCount.value = 0
  bulkCleaningTotalCount.value = targets.length

  let failedCount = 0
  let firstError: TableActionErrorResponse | null = null
  let blockedByAppGate = false

  try {
    for (const resource of targets) {
      try {
        await action(resource)
        bulkCleaningCompletedCount.value += 1
      } catch (error) {
        failedCount += 1
        const currentError = tableCleaningActionError(error, 'cleaning.request_failed')
        firstError ??= currentError

        if (isBlockingAppGateError(currentError)) {
          firstError = currentError
          blockedByAppGate = true
          break
        }
      } finally {
        startingCleaningResourceId.value = null
        completingCleaningResourceId.value = null
      }
    }

    if (failedCount > 0) {
      actionError.value =
        (blockedByAppGate || failedCount === targets.length) && firstError
          ? firstError
          : createBulkCleaningLocalError(
              bulkCleaningCompletedCount.value,
              failedCount,
              targets.length,
              mode
            )
    }

    await loadResources()
  } finally {
    bulkCleaningMode.value = null
    bulkCleaningCompletedCount.value = 0
    bulkCleaningTotalCount.value = 0
    startingCleaningResourceId.value = null
    completingCleaningResourceId.value = null
  }
}

async function seatAssignedReservation(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value
  const reservationId = resource.preassignedReservationId

  if (!currentStoreId || !reservationId || !canSeatAssignedReservation(resource) || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('RESERVATION_NOT_FOUND', 'reservation.not_found')
    return
  }

  actionError.value = null
  seatingResourceId.value = resource.resourceId

  try {
    const isConfirmedReservation = resource.preassignedReservationStatus === 'confirmed'
    const seatReservation = isConfirmedReservation ? checkInAndSeatConfirmedReservation : seatArrivedReservation

    await seatReservation(
      currentStoreId,
      reservationId,
      {
        ...resourceSeatRequest(resource),
        overrideReasonCode: null,
        overrideNote: null,
        note: isConfirmedReservation
          ? 'staff_table_resource_preassigned_check_in_seating'
          : 'staff_table_resource_preassigned_seating'
      },
      createTableActionIdempotencyKey(
        isConfirmedReservation ? 'reservation-check-in-seat' : 'reservation-seat',
        resource.resourceId
      )
    )
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof ReservationArrivedDirectSeatingApiError
        ? error.response
        : createCleaningLocalError('REQUEST_FAILED', 'reservation.direct_seating.unknown_error')
  } finally {
    seatingResourceId.value = null
  }
}

async function seatCalledAssignedQueue(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value
  const queueTicketId = resource.preassignedQueueTicketId

  if (!currentStoreId || !queueTicketId || !canSeatCalledQueue(resource) || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('QUEUE_TICKET_NOT_FOUND', 'queue.ticket_not_found')
    return
  }

  actionError.value = null
  seatingResourceId.value = resource.resourceId

  try {
    await seatCalledQueueTicket(
      currentStoreId,
      queueTicketId,
      {
        ...resourceSeatRequest(resource),
        overrideReasonCode: null,
        overrideNote: null,
        note: 'staff_table_resource_called_queue_seating'
      },
      createTableActionIdempotencyKey('queue-seat', resource.resourceId)
    )
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof SeatingFromCalledQueueApiError
        ? error.response
        : createCleaningLocalError('REQUEST_FAILED', 'queue.seat.unknown_error')
  } finally {
    seatingResourceId.value = null
  }
}

function statusFilterCount(status: StatusFilter): number {
  if (status === 'all') {
    return displayableTableResources.value.length
  }

  if (status === 'available') {
    return displayableTableResources.value.filter(resource => resource.selectable).length
  }

  if (status === 'active') {
    return groupResources.value.length
  }

  if (status === 'cleaning') {
    return displayableTableResources.value.filter(isCleaningWorkflowResource).length
  }

  return displayableTableResources.value.filter(resource => resource.status === status).length
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function statusClass(status: string): string {
  return `status-${status.replace(/_/g, '-')}`
}

function isTemporaryGroupMember(resource: TableResourceItem): boolean {
  return (
    resource.resourceType === 'dining_table' &&
    resource.selectionDisabledReason?.trim() === 'temporary_group_member'
  )
}

function isTemporaryGroupResource(resource: TableResourceItem): boolean {
  return (
    resource.resourceType === 'table_group' &&
    resource.groupType === 'temporary' &&
    resource.status === 'created'
  )
}

function resourceDisplayStatusLabel(resource: TableResourceItem): string {
  if (isTemporaryGroupMember(resource)) {
    return gt('generated.table-resource-list.107')
  }

  if (isTemporaryGroupResource(resource)) {
    return gt('generated.table-resource-list.108')
  }

  return statusLabel(resource.status)
}

function resourceDisplayStatusClass(resource: TableResourceItem): string {
  if (isTemporaryGroupMember(resource)) {
    return 'status-temporary-group-member'
  }

  if (isTemporaryGroupResource(resource)) {
    return 'status-temporary-group'
  }

  return statusClass(resource.status)
}

function capacityText(resource: TableResourceItem): string {
  return `${resource.capacityMin}-${resource.capacityMax}${gt('generated.table-resource-list.069')}`
}

function membersText(resource: TableResourceItem): string {
  return resource.memberTableCodes.length ? resource.memberTableCodes.join(' + ') : gt('generated.table-resource-list.109')
}

function preassignedReservationText(resource: TableResourceItem): string {
  if (!resource.preassignedReservationId) {
    return ''
  }

  const customer = preassignedCustomerText(resource)
  const partySize = resource.preassignedPartySize ? `${resource.preassignedPartySize}${gt('generated.table-resource-list.070')}` : ''
  const timeRange = preassignedTimeRange(resource)
  const status = resource.preassignedReservationStatus
    ? reservationStatusLabels[resource.preassignedReservationStatus] ?? resource.preassignedReservationStatus
    : ''

  return [customer, timeRange, partySize, status].filter(Boolean).join(' · ')
}

function preassignedCustomerText(resource: TableResourceItem): string {
  const name = resource.preassignedCustomerName?.trim()
  const phone = resource.preassignedPhoneMasked?.trim()

  if (name && phone) {
    return `${name} ${phone}`
  }
  return name || phone || gt('generated.table-resource-list.110')
}

function preassignedQueueText(resource: TableResourceItem): string {
  if (!resource.preassignedQueueTicketId) {
    return ''
  }

  const numberText =
    typeof resource.preassignedQueueTicketNumber === 'number'
      ? `#${resource.preassignedQueueTicketNumber}`
      : gt('generated.table-resource-list.111')
  const status = resource.preassignedQueueTicketStatus
    ? queueStatusLabels[resource.preassignedQueueTicketStatus] ?? resource.preassignedQueueTicketStatus
    : gt('generated.table-resource-list.112')

  return `${numberText} · ${status}`
}

function preassignedTimeRange(resource: TableResourceItem): string {
  if (!resource.preassignedStartAt || !resource.preassignedEndAt) {
    return ''
  }

  return `${formatStoreTime(resource.preassignedStartAt)}-${formatStoreTime(resource.preassignedEndAt)}`
}

function canStartCleaning(resource: TableResourceItem): boolean {
  return isClearableOccupiedResource(resource)
}

function isCleaningWorkflowResource(resource: TableResourceItem): boolean {
  return resource.status === 'cleaning' || isClearableOccupiedResource(resource)
}

function isClearableOccupiedResource(resource: TableResourceItem): boolean {
  return (
    isSelectedBusinessDateToday.value &&
    resource.status === 'occupied' &&
    !!resource.currentSeatingId
  )
}

function canSwitchResource(resource: TableResourceItem): boolean {
  return (
    resource.status === 'occupied' &&
    !!resource.currentSeatingId &&
    isSelectedBusinessDateToday.value &&
    !activeTableActionInProgress.value
  )
}

function switchResourceTitle(resource: TableResourceItem): string | undefined {
  if (canSwitchResource(resource)) {
    return undefined
  }

  return resource.currentSeatingId ? gt('generated.table-resource-list.113') : gt('generated.table-resource-list.114')
}

function openTableSwitchDialog(resource: TableResourceItem): void {
  if (!canSwitchResource(resource)) {
    return
  }

  actionError.value = null
  selectedSwitchResource.value = resource
  showTableSwitchDialog.value = true
}

function handleTableSwitched(_response: SwitchTableResponse): void {
  void loadResources()
  void loadCalendarSummary()
}

function isStartingCleaning(resource: TableResourceItem): boolean {
  return startingCleaningResourceId.value === resource.resourceId
}

function canCompleteCleaning(resource: TableResourceItem): boolean {
  return isSelectedBusinessDateToday.value && !!resource.currentCleaningId
}

function isCompletingCleaning(resource: TableResourceItem): boolean {
  return completingCleaningResourceId.value === resource.resourceId
}

function canSeatAssignedReservation(resource: TableResourceItem): boolean {
  return (
    isSelectedBusinessDateToday.value &&
    resource.status === 'reserved' &&
    (resource.preassignedReservationStatus === 'arrived' ||
      resource.preassignedReservationStatus === 'confirmed') &&
    !resource.preassignedQueueTicketId &&
    !!resource.preassignedReservationId
  )
}

function canSeatCalledQueue(resource: TableResourceItem): boolean {
  return (
    isSelectedBusinessDateToday.value &&
    resource.status === 'reserved' &&
    resource.preassignedQueueTicketStatus === 'called' &&
    !!resource.preassignedQueueTicketId
  )
}

function canSeatWalkInResource(resource: TableResourceItem): boolean {
  return isSelectedBusinessDateToday.value && resource.selectable
}

function canToggleTemporaryTable(resource: TableResourceItem): boolean {
  return (
    resource.resourceType === 'dining_table' &&
    (resource.selectable || isTemporaryTableSelected(resource))
  )
}

function canDissolveTemporaryGroup(resource: TableResourceItem): boolean {
  return (
    resource.resourceType === 'table_group' &&
    resource.groupType === 'temporary' &&
    resource.status === 'created'
  )
}

function isDissolvingTemporaryGroup(resource: TableResourceItem): boolean {
  return dissolvingTemporaryGroupId.value === resource.resourceId
}

function isTemporaryTableSelected(resource: TableResourceItem): boolean {
  return selectedTemporaryTableIdSet.value.has(resource.resourceId)
}

function temporaryGroupCapacityText(): string {
  const capacity = temporaryGroupCapacity.value

  return capacity.max > 0 ? `${capacity.min}-${capacity.max}${gt('generated.table-resource-list.071')}` : gt('generated.table-resource-list.115')
}

function isSeatingResource(resource: TableResourceItem): boolean {
  return seatingResourceId.value === resource.resourceId
}

function seatAssignedReservationActionText(resource: TableResourceItem): string {
  if (isSeatingResource(resource)) {
    return gt('generated.table-resource-list.116')
  }

  return resource.preassignedReservationStatus === 'confirmed' ? gt('generated.table-resource-list.117') : gt('generated.table-resource-list.118')
}

function preassignedPendingActionText(resource: TableResourceItem): string {
  if (resource.preassignedQueueTicketStatus) {
    return queueStatusLabels[resource.preassignedQueueTicketStatus] ?? resource.preassignedQueueTicketStatus
  }

  if (resource.preassignedReservationStatus) {
    return reservationStatusLabels[resource.preassignedReservationStatus] ?? resource.preassignedReservationStatus
  }

  return gt('generated.table-resource-list.119')
}

function walkInDirectSeatingRoute(resource: TableResourceItem): Record<string, unknown> {
  return {
    name: 'walk-in-direct-seating',
    params: {
      storeId: storeId.value
    },
    query:
      resource.resourceType === 'dining_table'
        ? { tableId: resource.resourceId, partySize: resource.capacityMin }
        : { tableGroupId: resource.resourceId, partySize: resource.capacityMin }
  }
}

function walkInActionText(resource: TableResourceItem): string {
  return isTemporaryGroupResource(resource) ? gt('generated.table-resource-list.120') : gt('generated.table-resource-list.121')
}

function resourceSeatRequest(resource: TableResourceItem): {
  tableId?: string | null
  tableGroupId?: string | null
} {
  return resource.resourceType === 'dining_table'
    ? { tableId: resource.resourceId, tableGroupId: null }
    : { tableId: null, tableGroupId: resource.resourceId }
}

function areaTitle(resource: TableResourceItem): string {
  const areaName = resource.areaName?.trim()
  return areaName || gt('generated.table-resource-list.122')
}

function createLocalError(code: string, messageKey: string): TableResourceApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createCleaningLocalError(code: string, messageKey: string): CleaningApiErrorResponse {
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

function createBulkCleaningLocalError(
  completedCount: number,
  failedCount: number,
  totalCount: number,
  mode: BulkCleaningMode
): CleaningApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'BULK_CLEANING_PARTIAL_FAILED',
      messageKey: 'cleaning.bulk_partial_failed',
      details: {
        completedCount,
        failedCount,
        mode,
        totalCount
      }
    },
    idempotency: {
      status: 'failed'
    }
  }
}

function tableCleaningActionError(error: unknown, fallbackMessageKey: string): TableActionErrorResponse {
  if (
    error instanceof CleaningApiError ||
    error instanceof ReservationStatusActionApiError
  ) {
    return error.response
  }

  if (isTableActionErrorResponse(error)) {
    return error
  }

  return createCleaningLocalError('REQUEST_FAILED', fallbackMessageKey)
}

function isTableActionErrorResponse(value: unknown): value is TableActionErrorResponse {
  return (
    !!value &&
    typeof value === 'object' &&
    (value as { success?: unknown }).success === false &&
    typeof (value as { error?: { code?: unknown } }).error?.code === 'string'
  )
}

function isBlockingAppGateError(response: TableActionErrorResponse): boolean {
  return (
    APP_GATE_BLOCKING_ERROR_CODES.has(response.error.code) ||
    response.error.messageKey.startsWith('appgate.')
  )
}

function createTemporaryGroupLocalError(code: string, messageKey: string): TemporaryTableGroupApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createTableActionIdempotencyKey(action: string, resourceId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `table:${action}:${resourceId}:${randomValue}`
}

function handleVisibleMonthChanged(month: string): void {
  visibleMonthKey.value = month
}

function monthKeyFromDate(value: string): string {
  const [year, month] = value.split('-')
  if (!year || !month) {
    return ''
  }
  return `${year}-${month}`
}

function formatStoreTime(value: string): string {
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
    return gt('generated.table-resource-list.123')
  }

  return `${gt('generated.table-resource-list.072')}${value.slice(0, 8)}`
}
</script>

<template>
  <StaffPrimaryWorkbench :store-id="storeId" active-tab="table" class="table-page">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="selectedBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <button type="button" :disabled="isLoading" @click="loadResources">
          {{ isLoading ? gt('generated.table-resource-list.001') : gt('generated.table-resource-list.002') }}
        </button>
      </template>
    </StaffHomeTopBar>

    <div class="table-page-body">
      <StaffBusinessDateSwitcher
        :calendar-label="gt('generated.table-resource-list.003')"
        :today-date="currentBusinessDate"
        :reservation-counts="reservationCounts"
        v-model:selected-date="selectedBusinessDate"
        @visible-month-changed="handleVisibleMonthChanged"
      />

      <section class="summary-row" :aria-label="gt('generated.table-resource-list.004')">
        <button
          v-for="item in summaryItems"
          :key="item.key"
          class="summary-row__item"
          :class="[`summary-row__item--${item.key}`, { selected: selectedStatus === item.key }]"
          :aria-pressed="selectedStatus === item.key"
          type="button"
          @click="selectStatus(item.key)"
        >
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </button>
      </section>

    <section class="temporary-group-panel" :aria-label="gt('generated.table-resource-list.005')">
      <div>
        <strong>{{ gt('generated.table-resource-list.006') }}</strong>
        <span>{{ temporaryGroupCapacityText() }} {{ gt('generated.table-resource-list.007') }} {{ selectedTemporaryTableIds.length }} {{ gt('generated.table-resource-list.008') }}</span>
      </div>
      <label class="temporary-group-panel__name">
        <span>{{ gt('generated.table-resource-list.009') }}</span>
        <input
          v-model.trim="temporaryGroupName"
          name="temporaryGroupName"
          :placeholder="gt('generated.table-resource-list.010')"
          type="text"
        />
      </label>
      <div class="temporary-group-panel__actions">
        <button type="button" @click="toggleTemporaryGroupMode">
          {{ temporaryGroupMode ? gt('generated.table-resource-list.011') : gt('generated.table-resource-list.012') }}
        </button>
        <button
          type="button"
          :disabled="!selectedTemporaryTableIds.length"
          @click="clearTemporaryGroupSelection"
        > {{ gt('generated.table-resource-list.013') }} </button>
        <button
          class="temporary-group-panel__save"
          type="button"
          :disabled="!canSaveTemporaryGroup"
          @click="saveSelectedTemporaryGroup"
        >
          {{ savingTemporaryGroup ? gt('generated.table-resource-list.014') : gt('generated.table-resource-list.015') }}
        </button>
        <RouterLink
          v-if="canSeatTemporaryGroup"
          class="temporary-group-panel__seat"
          :to="temporaryGroupRoute"
        > {{ gt('generated.table-resource-list.016') }} </RouterLink>
        <button v-else class="temporary-group-panel__seat" disabled type="button">{{ gt('generated.table-resource-list.017') }}</button>
      </div>
    </section>

    <section v-if="isLoading" class="state-panel" aria-live="polite"> {{ gt('generated.table-resource-list.018') }} </section>

    <section v-if="apiError" class="state-panel error-panel" aria-live="assertive">
      <strong>{{ formatAppGateErrorTitle(apiError.error, gt('generated.table-resource-list.019')) }}</strong>
      <span>{{ formatAppGateErrorMessage(apiError.error, gt('generated.table-resource-list.020')) }}</span>
    </section>

    <section v-if="actionError" class="state-panel error-panel" aria-live="assertive">
      <strong>{{ formatAppGateErrorTitle(actionError.error, gt('generated.table-resource-list.021')) }}</strong>
      <span>{{ formatAppGateErrorMessage(actionError.error, gt('generated.table-resource-list.022')) }}</span>
    </section>

    <section v-if="showNoConfiguredResources" class="state-panel" aria-live="polite"> {{ gt('generated.table-resource-list.023') }} </section>

    <section v-if="showFilteredEmpty" class="state-panel" aria-live="polite"> {{ gt('generated.table-resource-list.024') }} </section>

    <section v-if="groupedAreaResources.length" class="table-page__area-list" :aria-label="gt('generated.table-resource-list.025')">
      <header class="table-page__section-heading table-page__area-heading">
        <h2>{{ gt('generated.table-resource-list.026') }}</h2>
        <div class="table-page__area-tools">
          <label class="table-area-party-filter">
            <span>{{ gt('generated.table-resource-list.027') }}</span>
            <select v-model="partySizeValue" name="partySize">
              <option value="">{{ gt('generated.table-resource-list.028') }}</option>
              <option v-for="option in partySizeOptions" :key="option" :value="String(option)">
                {{ option }} {{ gt('generated.table-resource-list.029') }} </option>
            </select>
          </label>
          <span>{{ gt('generated.table-resource-list.030') }} {{ tableResources.length }} {{ gt('generated.table-resource-list.031') }}</span>
          <div class="table-page__bulk-actions" :aria-label="gt('generated.table-resource-list.032')">
            <span>{{ bulkCleaningProgressText }}</span>
            <button
              class="table-page__bulk-action table-page__bulk-action--danger"
              :disabled="!canRunBulkStartCleaning"
              type="button"
              @click="startVisibleResourcesCleaning"
            >
              {{ bulkCleaningMode === 'start' ? gt('generated.table-resource-list.034') : `${gt('generated.table-resource-list.033')}${bulkStartCleaningResources.length}` }}
            </button>
            <button
              class="table-page__bulk-action table-page__bulk-action--primary"
              :disabled="!canRunBulkCompleteCleaning"
              type="button"
              @click="completeVisibleResourcesCleaning"
            >
              {{ bulkCleaningMode === 'complete' ? gt('generated.table-resource-list.036') : `${gt('generated.table-resource-list.035')}${bulkCompleteCleaningResources.length}` }}
            </button>
          </div>
        </div>
      </header>

      <div class="table-page__area-filter" :aria-label="gt('generated.table-resource-list.037')">
        <button
          v-for="option in areaFilterOptions"
          :key="option.value"
          :aria-pressed="selectedArea === option.value"
          :class="{ selected: selectedArea === option.value }"
          type="button"
          @click="selectArea(option.value)"
        >
          <span>{{ option.label }}</span>
          <strong>{{ option.count }}</strong>
        </button>
      </div>

      <section
        v-for="area in groupedAreaResources"
        :key="area.title"
        class="table-page__area-section"
        :aria-label="`${area.title}${gt('generated.table-resource-list.038')}`"
      >
        <header>
          <h3>{{ area.title }}</h3>
          <span>{{ area.items.length }} {{ gt('generated.table-resource-list.039') }}</span>
        </header>

        <div class="table-page__resource-grid">
          <article
            v-for="resource in area.items"
            :key="resource.resourceId"
            class="table-page__resource-card"
            :class="[
              resourceDisplayStatusClass(resource),
              { 'table-page__resource-card--temp-selected': isTemporaryTableSelected(resource) }
            ]"
          >
            <div class="table-page__resource-title">
              <strong>{{ resource.displayName || resource.code }}</strong>
              <span class="table-page__resource-badge">{{ resourceDisplayStatusLabel(resource) }}</span>
            </div>
            <p class="table-page__resource-meta">{{ capacityText(resource) }}</p>
            <section
              v-if="resource.preassignedReservationId"
              class="table-page__assignment"
              :aria-label="gt('generated.table-resource-list.040')"
            >
              <span>{{ gt('generated.table-resource-list.041') }}</span>
              <strong>{{ preassignedReservationText(resource) }}</strong>
              <small v-if="preassignedQueueText(resource)">{{ preassignedQueueText(resource) }}</small>
            </section>
            <div class="table-page__resource-actions" :aria-label="gt('generated.table-resource-list.042')">
              <button
                v-if="temporaryGroupMode"
                class="table-page__resource-action"
                :class="{ 'table-page__resource-action--primary': isTemporaryTableSelected(resource) }"
                :disabled="!canToggleTemporaryTable(resource)"
                type="button"
                @click="toggleTemporaryTable(resource)"
              >
                {{ isTemporaryTableSelected(resource) ? gt('generated.table-resource-list.043') : gt('generated.table-resource-list.044') }}
              </button>
              <button
                v-if="canSeatCalledQueue(resource)"
                class="table-page__resource-action table-page__resource-action--primary"
                :disabled="isSeatingResource(resource)"
                type="button"
                @click="seatCalledAssignedQueue(resource)"
              >
                {{ isSeatingResource(resource) ? gt('generated.table-resource-list.045') : gt('generated.table-resource-list.046') }}
              </button>
              <button
                v-else-if="canSeatAssignedReservation(resource)"
                class="table-page__resource-action table-page__resource-action--primary"
                :disabled="isSeatingResource(resource)"
                type="button"
                @click="seatAssignedReservation(resource)"
              >
                {{ seatAssignedReservationActionText(resource) }}
              </button>
              <button
                v-else-if="resource.status === 'reserved'"
                class="table-page__resource-action"
                disabled
                type="button"
              >
                {{ preassignedPendingActionText(resource) }}
              </button>
              <RouterLink
                v-if="canSeatWalkInResource(resource)"
                class="table-page__resource-action table-page__resource-action--primary"
                :to="walkInDirectSeatingRoute(resource)"
              >
                {{ walkInActionText(resource) }}
              </RouterLink>
              <div v-if="resource.status === 'occupied'" class="table-page__resource-action-pair">
                <button
                  class="table-page__resource-action"
                  :disabled="!canSwitchResource(resource)"
                  :title="switchResourceTitle(resource)"
                  type="button"
                  @click="openTableSwitchDialog(resource)"
                > {{ gt('generated.table-resource-list.047') }} </button>
                <button
                  class="table-page__resource-action table-page__resource-action--danger"
                  :disabled="!canStartCleaning(resource) || isStartingCleaning(resource)"
                  type="button"
                  @click="startResourceCleaning(resource)"
                >
                  {{ isStartingCleaning(resource) ? gt('generated.table-resource-list.048') : gt('generated.table-resource-list.049') }}
                </button>
              </div>
              <button
                v-if="resource.status === 'cleaning'"
                class="table-page__resource-action table-page__resource-action--primary"
                :disabled="!canCompleteCleaning(resource) || isCompletingCleaning(resource)"
                type="button"
                @click="completeResourceCleaning(resource)"
              >
                {{ isCompletingCleaning(resource) ? gt('generated.table-resource-list.050') : gt('generated.table-resource-list.051') }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </section>

    <section
      v-if="displayedGroupResources.length"
      class="table-page__group-section"
      :aria-label="gt('generated.table-resource-list.052')"
    >
      <header class="table-page__section-heading">
        <h2>{{ gt('generated.table-resource-list.053') }}</h2>
        <span>{{ displayedGroupResources.length }} {{ gt('generated.table-resource-list.054') }}</span>
      </header>

      <div class="table-page__resource-grid table-page__resource-grid--groups">
        <article
          v-for="resource in displayedGroupResources"
          :key="resource.resourceId"
          class="table-page__resource-card table-page__resource-card--group"
          :class="resourceDisplayStatusClass(resource)"
        >
          <div class="table-page__resource-title">
            <strong>{{ resource.displayName || resource.code }}</strong>
            <span class="table-page__resource-badge">{{ resourceDisplayStatusLabel(resource) }}</span>
          </div>
          <p class="table-page__resource-meta">{{ capacityText(resource) }}</p>
          <p class="table-page__resource-members">{{ membersText(resource) }}</p>
          <section
            v-if="resource.preassignedReservationId"
            class="table-page__assignment"
            :aria-label="gt('generated.table-resource-list.055')"
          >
            <span>{{ gt('generated.table-resource-list.056') }}</span>
            <strong>{{ preassignedReservationText(resource) }}</strong>
            <small v-if="preassignedQueueText(resource)">{{ preassignedQueueText(resource) }}</small>
          </section>
          <div class="table-page__resource-actions" :aria-label="gt('generated.table-resource-list.057')">
            <button
              v-if="canDissolveTemporaryGroup(resource)"
              class="table-page__resource-action table-page__resource-action--danger"
              :disabled="isDissolvingTemporaryGroup(resource) || activeTableActionInProgress"
              type="button"
              @click="dissolveTemporaryGroup(resource)"
            >
              {{ isDissolvingTemporaryGroup(resource) ? gt('generated.table-resource-list.058') : gt('generated.table-resource-list.059') }}
            </button>
            <button
              v-if="canSeatCalledQueue(resource)"
              class="table-page__resource-action table-page__resource-action--primary"
              :disabled="isSeatingResource(resource)"
              type="button"
              @click="seatCalledAssignedQueue(resource)"
            >
              {{ isSeatingResource(resource) ? gt('generated.table-resource-list.060') : gt('generated.table-resource-list.061') }}
            </button>
            <button
              v-else-if="canSeatAssignedReservation(resource)"
              class="table-page__resource-action table-page__resource-action--primary"
              :disabled="isSeatingResource(resource)"
              type="button"
              @click="seatAssignedReservation(resource)"
            >
              {{ seatAssignedReservationActionText(resource) }}
            </button>
            <button
              v-else-if="resource.status === 'reserved'"
              class="table-page__resource-action"
              disabled
              type="button"
            >
              {{ preassignedPendingActionText(resource) }}
            </button>
            <RouterLink
              v-if="canSeatWalkInResource(resource)"
              class="table-page__resource-action table-page__resource-action--primary"
              :to="walkInDirectSeatingRoute(resource)"
            >
              {{ walkInActionText(resource) }}
            </RouterLink>
            <div v-if="resource.status === 'occupied'" class="table-page__resource-action-pair">
              <button
                class="table-page__resource-action"
                :disabled="!canSwitchResource(resource)"
                :title="switchResourceTitle(resource)"
                type="button"
                @click="openTableSwitchDialog(resource)"
              > {{ gt('generated.table-resource-list.062') }} </button>
              <button
                class="table-page__resource-action table-page__resource-action--danger"
                :disabled="!canStartCleaning(resource) || isStartingCleaning(resource)"
                type="button"
                @click="startResourceCleaning(resource)"
              >
                {{ isStartingCleaning(resource) ? gt('generated.table-resource-list.063') : gt('generated.table-resource-list.064') }}
              </button>
            </div>
            <button
              v-if="resource.status === 'cleaning'"
              class="table-page__resource-action table-page__resource-action--primary"
              :disabled="!canCompleteCleaning(resource) || isCompletingCleaning(resource)"
              type="button"
              @click="completeResourceCleaning(resource)"
            >
              {{ isCompletingCleaning(resource) ? gt('generated.table-resource-list.065') : gt('generated.table-resource-list.066') }}
            </button>
          </div>
        </article>
      </div>
    </section>

    </div>

    <ReservationTableSwitchDialog
      v-model:open="showTableSwitchDialog"
      :business-date="selectedBusinessDate"
      :item="tableSwitchDialogItem"
      :store-id="storeId"
      @switched="handleTableSwitched"
    />

  </StaffPrimaryWorkbench>
</template>

<style scoped>
.table-page-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(128px + env(safe-area-inset-bottom));
}

.table-area-party-filter span,
.table-page__section-heading span,
.table-page__area-section header span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
  margin: 0;
}

h1,
h2,
h3 {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.35rem;
  line-height: 1.15;
}

h2 {
  font-size: 0.98rem;
}

h3 {
  font-size: 0.9rem;
}

.table-page__area-filter button {
  border: 1px solid #fed7aa;
  border-radius: 999px;
  font-weight: 900;
  min-height: 38px;
  padding: 0 13px;
}

.summary-row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.summary-row__item {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  color: inherit;
  cursor: pointer;
  display: grid;
  font: inherit;
  gap: 4px;
  min-height: 64px;
  padding: 10px 6px;
  place-items: center;
  text-align: center;
}

.summary-row__item.selected {
  box-shadow: inset 0 0 0 2px rgba(249, 115, 22, 0.28);
}

.summary-row__item.selected span {
  color: #0f172a;
}

.summary-row__item strong {
  color: #f97316;
  font-size: 1.22rem;
  line-height: 1;
}

.summary-row__item span {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 900;
}

.summary-row__item--available {
  background: #ecfdf5;
  border-color: #86efac;
}

.summary-row__item--available strong {
  color: #047857;
}

.summary-row__item--reserved {
  background: #fef3c7;
  border-color: #f59e0b;
}

.summary-row__item--reserved strong {
  color: #92400e;
}

.summary-row__item--occupied {
  background: #eef5ff;
  border-color: #93c5fd;
}

.summary-row__item--occupied strong {
  color: #1d4ed8;
}

.summary-row__item--cleaning {
  background: #fff7ed;
  border-color: #fdba74;
}

.summary-row__item--cleaning strong {
  color: #c2410c;
}

.summary-row__item--active {
  background: #f4f0ff;
  border-color: #c4b5fd;
}

.summary-row__item--active strong {
  color: #6d28d9;
}

.temporary-group-panel,
.table-page__area-list,
.table-page__group-section {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.temporary-group-panel {
  align-items: center;
  grid-template-columns: minmax(0, 1fr);
}

.temporary-group-panel div:first-child {
  display: grid;
  gap: 3px;
}

.temporary-group-panel strong {
  color: #0f172a;
  font-size: 0.95rem;
  font-weight: 950;
}

.temporary-group-panel span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
}

.temporary-group-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.temporary-group-panel__name {
  display: grid;
  gap: 6px;
}

.temporary-group-panel__name input {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 850;
  min-height: 38px;
  padding: 0 12px;
}

.temporary-group-panel__actions button,
.temporary-group-panel__seat {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d1dae7;
  border-radius: 8px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.72rem;
  font-weight: 950;
  justify-content: center;
  min-height: 30px;
  padding: 0 10px;
  text-decoration: none;
}

.temporary-group-panel__seat {
  background: #f0fdf4;
  border-color: #86efac;
  color: #047857;
}

.temporary-group-panel__save {
  background: #fff7ed;
  border-color: #fb923c;
  color: #c2410c;
}

.temporary-group-panel__actions button:disabled {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
}

.temporary-group-panel__seat[disabled] {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
}

.table-page__area-filter button {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d1dae7;
  border-radius: 8px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.72rem;
  gap: 5px;
  min-height: 30px;
  padding: 0 10px;
}

.table-page__area-filter button strong {
  font-size: 0.68rem;
}

.table-page__area-filter button.selected {
  background: #fff7ed;
  border-color: #fb923c;
  box-shadow: inset 0 0 0 1px rgba(251, 146, 60, 0.24);
  color: #c2410c;
}

.table-area-party-filter {
  align-items: center;
  display: flex;
  gap: 6px;
}

.table-area-party-filter span {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 850;
}

.table-area-party-filter select {
  appearance: none;
  background: #ffffff;
  border: 1px solid #d1dae7;
  border-radius: 8px;
  color: #0f172a;
  font-size: 0.72rem;
  font-weight: 850;
  min-height: 30px;
  padding: 0 22px 0 10px;
  width: 76px;
}

.state-panel {
  background: #ffffff;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #64748b;
  display: grid;
  gap: 4px;
  font-size: 0.88rem;
  font-weight: 800;
  padding: 14px;
}

.error-panel {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.table-page__section-heading,
.table-page__area-section header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.table-page__area-heading {
  gap: 10px;
}

.table-page__area-tools {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.table-page__bulk-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.table-page__bulk-actions > span {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 900;
  white-space: nowrap;
}

.table-page__bulk-action {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d1dae7;
  border-radius: 8px;
  color: #315f91;
  display: inline-flex;
  font-size: 0.72rem;
  font-weight: 950;
  justify-content: center;
  min-height: 30px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-page__bulk-action--primary {
  background: #ecfdf5;
  border-color: #86efac;
  color: #047857;
}

.table-page__bulk-action--danger {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.table-page__bulk-action:disabled {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
}

.table-page__area-section {
  display: grid;
  gap: 10px;
}

.table-page__area-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.table-page__resource-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.table-page__resource-grid--groups {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.table-page__resource-card {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  min-height: 112px;
  min-width: 0;
  padding: 12px;
}

.table-page__resource-card--temp-selected {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.28);
}

.table-page__resource-title {
  align-items: start;
  display: grid;
  gap: 6px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.table-page__resource-title strong {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.table-page__resource-badge {
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 950;
  line-height: 1;
  padding: 5px 8px;
  white-space: nowrap;
}

.table-page__resource-meta,
.table-page__resource-members {
  color: #315f91;
  font-size: 0.78rem;
  font-weight: 850;
  margin: 0;
}

.table-page__assignment {
  background: rgba(255, 247, 237, 0.78);
  border: 1px solid #fed7aa;
  border-radius: 8px;
  display: grid;
  gap: 3px;
  padding: 8px 9px;
}

.table-page__assignment span {
  color: #c2410c;
  font-size: 0.7rem;
  font-weight: 900;
}

.table-page__assignment strong,
.table-page__assignment small {
  color: #315f91;
  font-size: 0.75rem;
  font-weight: 850;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.table-page__assignment strong {
  color: #14213d;
}

.table-page__resource-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
}

.table-page__resource-action-pair {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.table-page__resource-action {
  align-items: center;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  color: #1d4ed8;
  display: inline-flex;
  font-size: 0.75rem;
  font-weight: 950;
  justify-content: center;
  min-height: 30px;
  padding: 0 12px;
  text-decoration: none;
}

.table-page__resource-action-pair .table-page__resource-action {
  font-size: 0.68rem;
  min-width: 0;
  padding: 0 4px;
  white-space: nowrap;
}

.table-page__resource-action--primary {
  background: #ecfdf5;
  border-color: #bbf7d0;
  color: #047857;
}

.table-page__resource-action--danger {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.table-page__resource-action:disabled {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
}

.status-available {
  background: #ecfdf5;
  border-color: #86efac;
}

.status-available .table-page__resource-badge {
  background: #d1fae5;
  color: #047857;
}

.status-occupied {
  background: #eef5ff;
  border-color: #93c5fd;
}

.status-occupied .table-page__resource-badge {
  background: #dbeafe;
  color: #1d4ed8;
}

.status-cleaning {
  background: #fff7ed;
  border-color: #fdba74;
}

.status-cleaning .table-page__resource-badge {
  background: #fed7aa;
  color: #c2410c;
}

.status-active {
  background: #f4f0ff;
  border-color: #c4b5fd;
}

.status-active .table-page__resource-badge {
  background: #ede9fe;
  color: #6d28d9;
}

.status-temporary-group {
  background: #eef2ff;
  border-color: #a5b4fc;
}

.status-temporary-group .table-page__resource-badge {
  background: #e0e7ff;
  color: #3730a3;
}

.status-temporary-group-member {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.status-temporary-group-member .table-page__resource-badge {
  background: #e2e8f0;
  color: #475569;
}

.status-locked,
.status-reserved {
  background: #fef3c7;
  border-color: #f59e0b;
}

.status-locked .table-page__resource-badge,
.status-reserved .table-page__resource-badge {
  background: #fde68a;
  color: #92400e;
}

.status-inactive,
.status-deleted,
.status-released,
.status-ended {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.status-inactive .table-page__resource-badge,
.status-deleted .table-page__resource-badge,
.status-released .table-page__resource-badge,
.status-ended .table-page__resource-badge {
  background: #e2e8f0;
  color: #475569;
}

button:focus-visible,
a:focus-visible,
select:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .summary-row {
    gap: 6px;
  }

  .summary-row__item {
    min-height: 58px;
    padding: 8px 4px;
  }

  .summary-row__item strong {
    font-size: 1.08rem;
  }

  .table-page__resource-grid {
    gap: 8px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-page__resource-grid--groups {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-page__area-tools,
  .table-page__bulk-actions {
    justify-content: flex-start;
  }
}

@media (min-width: 768px) {
  .table-page-body {
    padding: 16px 18px 24px;
  }

  .table-page__resource-grid,
  .table-page__resource-grid--groups {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .temporary-group-panel {
    grid-template-columns: minmax(160px, 0.8fr) minmax(220px, 1fr) minmax(260px, 1.4fr);
  }

  .temporary-group-panel__actions {
    justify-content: flex-end;
  }

  .table-page__resource-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .table-page__resource-grid--groups {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
