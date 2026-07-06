<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { createReservation, ReservationCreateApiError } from '../../api/reservationCreateApi'
import {
  getReservationShareInfo,
  recordReservationShareIntent,
  ReservationShareInfoApiError
} from '../../api/reservationShareInfoApi'
import { fetchTableResources, TableResourceApiError } from '../../api/tableResourceApi'
import {
  fetchReservationTimeSlots,
  ReservationMealPeriodApiError
} from '../../api/reservationMealPeriodApi'
import {
  saveTemporaryTableGroup,
  TemporaryTableGroupApiError
} from '../../api/temporaryTableGroupApi'
import { reservationWechatShareText } from '../../utils/reservationChannelSharePayload'
import { shareLinkOrCopy } from '../../utils/reservationShareLauncher'
import { copyPlainText } from '../../utils/plainTextClipboard'
import { formatReservationCreateErrorMessage } from '../../utils/reservationCreateMessages'
import StaffGuestContactLookup from '../staff/StaffGuestContactLookup.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../staff/staffGuestContact'
import TableResourcePicker from '../staff-table/TableResourcePicker.vue'
import ReservationShareCopyPanel from './ReservationShareCopyPanel.vue'
import { defaultFutureReservationDateTime } from './reservationCreateTime'
import type {
  CreateReservationRequest,
  CreateReservationResponse,
  ReservationApiErrorResponse
} from '../../types/reservation'
import type { ReservationShareInfo } from '../../types/reservationShareInfo'
import type { ReservationShareIntentChannel } from '../../types/reservationShareInfo'
import type {
  TableResourceApiErrorResponse,
  TableResourceItem
} from '../../types/tableResource'
import type { TemporaryTableGroupApiErrorResponse } from '../../types/temporaryTableGroup'
import type {
  ReservationMealPeriodApiErrorResponse,
  ReservationTimeSlot
} from '../../types/reservationMealPeriod'

const props = withDefaults(
  defineProps<{
    open: boolean
    storeId: string
    selectedDate: string
    minDate?: string
  }>(),
  {
    minDate: ''
  }
)

const emit = defineEmits<{
  'update:open': [open: boolean]
  created: [response: CreateReservationResponse]
}>()

const { t, locale } = useI18n()
const activeLocale = computed(() => String(locale.value || 'zh-CN'))
type TablePickerSelectionMode = 'single' | 'temporary'
type MealPeriodFilterOption = {
  periodKey: string
  displayName: string
  count: number
}

const ALL_MEAL_PERIOD_KEY = 'all'

const form = reactive({
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  businessDate: props.selectedDate,
  selectedStartAt: '',
  partySize: 2,
  tablePreference: 'unassigned',
  temporaryTableIds: [] as string[]
})

const isSubmitting = ref(false)
const apiError = ref<ReservationApiErrorResponse | null>(null)
const temporaryGroupName = ref('')
const isSavingTemporaryGroup = ref(false)
const temporaryGroupApiError = ref<TemporaryTableGroupApiErrorResponse | null>(null)
const tableResourceOptions = ref<TableResourceItem[]>([])
const isLoadingTableResources = ref(false)
const tableResourceApiError = ref<TableResourceApiErrorResponse | null>(null)
const timeSlots = ref<ReservationTimeSlot[]>([])
const isLoadingTimeSlots = ref(false)
const timeSlotApiError = ref<ReservationMealPeriodApiErrorResponse | null>(null)
const selectedMealPeriodKey = ref(ALL_MEAL_PERIOD_KEY)
const isTablePickerOpen = ref(false)
const tablePickerSelectionMode = ref<TablePickerSelectionMode>('single')
const createdReservation = ref<CreateReservationResponse | null>(null)
const createdShareInfo = ref<ReservationShareInfo | null>(null)
const isLoadingCreatedShare = ref(false)
const createdShareErrorText = ref('')
const createdShareShared = ref(false)
const createdShareStatusText = ref('')
const createdShareFallbackText = ref('')
let tableResourceLoadSequence = 0
let timeSlotLoadSequence = 0

const canSubmit = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !isSubmitting.value &&
    !createdReservation.value &&
    !!form.businessDate &&
    !!selectedTimeSlot.value?.selectable &&
    !isBeforeMinDate(form.businessDate) &&
    Number.isInteger(form.partySize) &&
    form.partySize > 0
)
const availableTimeSlots = computed(() => timeSlots.value.filter(slot => slot.selectable))
const mealPeriodFilterOptions = computed<MealPeriodFilterOption[]>(() => {
  const periodOptions = new Map<string, MealPeriodFilterOption>()

  for (const slot of availableTimeSlots.value) {
    const current = periodOptions.get(slot.periodKey)
    if (current) {
      current.count += 1
      continue
    }
    periodOptions.set(slot.periodKey, {
      periodKey: slot.periodKey,
      displayName: slot.displayName,
      count: 1
    })
  }

  return [
    {
      periodKey: ALL_MEAL_PERIOD_KEY,
      displayName: t('reservationWorkbench.createDialog.allMealPeriods'),
      count: availableTimeSlots.value.length
    },
    ...periodOptions.values()
  ]
})
const filteredTimeSlots = computed(() =>
  selectedMealPeriodKey.value === ALL_MEAL_PERIOD_KEY
    ? availableTimeSlots.value
    : availableTimeSlots.value.filter(slot => slot.periodKey === selectedMealPeriodKey.value)
)
const selectedTimeSlot = computed(() =>
  availableTimeSlots.value.find(slot => slot.startAt === form.selectedStartAt) ?? null
)
const selectedResource = computed(() =>
  tableResourceOptions.value.find(
    resource => resource.selectable && resourceOptionValue(resource) === form.tablePreference
  ) ?? null
)
const selectedTableId = computed(() =>
  selectedResource.value?.resourceType === 'dining_table'
    ? selectedResource.value.resourceId
    : null
)
const selectedTableGroupId = computed(() =>
  selectedResource.value?.resourceType === 'table_group'
    ? selectedResource.value.resourceId
    : null
)
const tablePreferenceDisplay = computed(() =>
  selectedResource.value ? resourceDisplayName(selectedResource.value) : t('reservationWorkbench.createDialog.unassigned')
)
const tablePreferenceMeta = computed(() => {
  const resource = selectedResource.value

  if (resource) {
    return `${resourceCapacityText(resource)} · ${resourcePickerStatusText(resource)}`
  }

  if (isLoadingTableResources.value) {
    return t('reservationWorkbench.createDialog.loadingTables')
  }

  if (tableResourceApiError.value) {
    return t('reservationWorkbench.createDialog.tableLoadFailed')
  }

  return t('reservationWorkbench.createDialog.chooseTable')
})
const canSaveTemporaryGroup = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !!form.businessDate &&
    !isSubmitting.value &&
    !isSavingTemporaryGroup.value &&
    temporaryGroupName.value.trim().length > 0 &&
    form.temporaryTableIds.length >= 2
)
const isTemporaryGroupMode = computed(() => tablePickerSelectionMode.value === 'temporary')

watch(
  () => props.open,
  open => {
    if (open) {
      apiError.value = null
      clearCreatedReservationShareState()
      applyDefaultFutureDateTime(props.selectedDate)
    } else {
      isTablePickerOpen.value = false
      clearTemporaryGroupDraft()
      tablePickerSelectionMode.value = 'single'
      clearCreatedReservationShareState()
    }
  },
  { immediate: true }
)

watch(
  () => props.selectedDate,
  selectedDate => {
    if (props.open) {
      applyDefaultFutureDateTime(selectedDate)
    }
  }
)

watch(
  () => props.minDate,
  () => {
    if (props.open) {
      applyDefaultFutureDateTime(props.selectedDate)
    }
  }
)

watch(activeLocale, () => {
  if (createdReservation.value) {
    createdShareInfo.value = null
    resetCreatedShareFeedback()
  }
})

watch(
  [() => props.open, () => props.storeId, () => form.businessDate],
  () => {
    void loadTimeSlots()
    void loadTableResources()
  },
  { immediate: true }
)

watch(
  [() => form.businessDate, () => form.partySize],
  () => {
    clearTemporaryGroupDraft()
  }
)

async function submit(): Promise<void> {
  apiError.value = validateForm()
  if (apiError.value || !canSubmit.value) {
    return
  }

  isSubmitting.value = true

  try {
    const result = await createReservation(props.storeId, toRequest(), createIdempotencyKey())
    emit('created', result)
    createdReservation.value = result
    resetAfterSuccess()
    await loadCreatedShareInfo(result.reservationId)
  } catch (error) {
    apiError.value =
      error instanceof ReservationCreateApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'reservation.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function close(): void {
  if (!isSubmitting.value && !isLoadingCreatedShare.value) {
    emit('update:open', false)
    clearCreatedReservationShareState()
  }
}

function finishCreatedReservationFlow(): void {
  emit('update:open', false)
  clearCreatedReservationShareState()
}

function validateForm(): ReservationApiErrorResponse | null {
  if (!Number.isInteger(form.partySize) || form.partySize <= 0) {
    return createLocalError('INVALID_PARTY_SIZE', 'reservation.invalid_party_size')
  }

  if (!selectedTimeSlot.value) {
    return createLocalError('INVALID_TIME_RANGE', 'reservation.invalid_time_range')
  }

  if (isBeforeMinDate(form.businessDate)) {
    return createLocalError('RESERVATION_START_IN_PAST', 'reservation.start_in_past')
  }

  if (!selectedTimeSlot.value.selectable) {
    return createLocalError('RESERVATION_START_IN_PAST', 'reservation.start_in_past')
  }

  const phone = form.phoneLocal.trim()
  if (phone && !isValidSingaporeLocalPhone(phone)) {
    return createLocalError('INVALID_PHONE_E164', 'reservation.invalid_phone_e164')
  }

  return null
}

function toRequest(): CreateReservationRequest {
  const resource = selectedResource.value

  return {
    partySize: form.partySize,
    reservedStartAt: selectedTimeSlot.value?.startAt ?? '',
    reservedEndAt: null,
    businessDate: form.businessDate,
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerSalutation),
    phoneE164: toSingaporePhoneE164(form.phoneLocal),
    tableId: resource && resource.resourceType === 'dining_table' ? resource.resourceId : null,
    tableGroupId: resource && resource.resourceType === 'table_group' ? resource.resourceId : null
  }
}

async function loadTimeSlots(): Promise<void> {
  const sequence = ++timeSlotLoadSequence
  timeSlotApiError.value = null

  if (!props.open || !props.storeId || !form.businessDate) {
    timeSlots.value = []
    form.selectedStartAt = ''
    selectedMealPeriodKey.value = ALL_MEAL_PERIOD_KEY
    isLoadingTimeSlots.value = false
    return
  }

  isLoadingTimeSlots.value = true

  try {
    const result = await fetchReservationTimeSlots(props.storeId, form.businessDate)

    if (sequence === timeSlotLoadSequence) {
      timeSlots.value = result.slots
      ensureSelectedMealPeriodStillAvailable()
      ensureSelectedTimeSlotStillAvailable()
    }
  } catch (error) {
    if (sequence === timeSlotLoadSequence) {
      timeSlots.value = []
      form.selectedStartAt = ''
      selectedMealPeriodKey.value = ALL_MEAL_PERIOD_KEY
      timeSlotApiError.value =
        error instanceof ReservationMealPeriodApiError
          ? error.response
          : createMealPeriodLocalError('REQUEST_FAILED', 'reservation.meal_period.request_failed')
    }
  } finally {
    if (sequence === timeSlotLoadSequence) {
      isLoadingTimeSlots.value = false
    }
  }
}

function selectTimeSlot(slot: ReservationTimeSlot): void {
  if (slot.selectable && !isSubmitting.value) {
    form.selectedStartAt = slot.startAt
  }
}

function selectMealPeriod(periodKey: string): void {
  if (isSubmitting.value) {
    return
  }
  selectedMealPeriodKey.value = periodKey
  ensureSelectedTimeSlotStillAvailable()
}

function ensureSelectedMealPeriodStillAvailable(): void {
  if (selectedMealPeriodKey.value === ALL_MEAL_PERIOD_KEY) {
    return
  }
  if (mealPeriodFilterOptions.value.some(option => option.periodKey === selectedMealPeriodKey.value)) {
    return
  }
  selectedMealPeriodKey.value = ALL_MEAL_PERIOD_KEY
}

function ensureSelectedTimeSlotStillAvailable(): void {
  if (filteredTimeSlots.value.some(slot => slot.startAt === form.selectedStartAt)) {
    return
  }
  form.selectedStartAt = filteredTimeSlots.value[0]?.startAt ?? availableTimeSlots.value[0]?.startAt ?? ''
}

async function loadTableResources(): Promise<void> {
  const sequence = ++tableResourceLoadSequence
  tableResourceApiError.value = null

  if (
    !props.open ||
    !props.storeId ||
    !form.businessDate
  ) {
    tableResourceOptions.value = []
    isLoadingTableResources.value = false
    form.tablePreference = 'unassigned'
    return
  }

  isLoadingTableResources.value = true

  try {
    const result = await fetchTableResources(props.storeId, {
      includeGroups: true,
      businessDate: form.businessDate
    })

    if (sequence === tableResourceLoadSequence) {
      tableResourceOptions.value = result.resources
      ensureSelectedResourceStillAvailable()
    }
  } catch (error) {
    if (sequence === tableResourceLoadSequence) {
      tableResourceOptions.value = []
      form.tablePreference = 'unassigned'
      tableResourceApiError.value =
        error instanceof TableResourceApiError
          ? error.response
          : createTableResourceLocalError('REQUEST_FAILED', 'table.resources.request_failed')
    }
  } finally {
    if (sequence === tableResourceLoadSequence) {
      isLoadingTableResources.value = false
    }
  }
}

function ensureSelectedResourceStillAvailable(): void {
  if (form.tablePreference === 'unassigned') {
    return
  }

  if (!selectedResource.value) {
    form.tablePreference = 'unassigned'
  }
}

function openTablePicker(): void {
  if (!isSubmitting.value) {
    tablePickerSelectionMode.value = form.temporaryTableIds.length > 0 ? 'temporary' : 'single'
    isTablePickerOpen.value = true
  }
}

function closeTablePicker(): void {
  isTablePickerOpen.value = false
  clearTemporaryGroupDraft()
  tablePickerSelectionMode.value = 'single'
}

function clearTablePreference(): void {
  form.tablePreference = 'unassigned'
  clearTemporaryGroupDraft()
  tablePickerSelectionMode.value = 'single'
}

function selectTable(tableId: string): void {
  form.tablePreference = `dining_table:${tableId}`
  clearTemporaryGroupDraft()
  tablePickerSelectionMode.value = 'single'
  isTablePickerOpen.value = false
}

function selectTableGroup(tableGroupId: string): void {
  form.tablePreference = `table_group:${tableGroupId}`
  clearTemporaryGroupDraft()
  tablePickerSelectionMode.value = 'single'
  isTablePickerOpen.value = false
}

function selectTemporaryTables(tableIds: string[]): void {
  form.tablePreference = 'unassigned'
  form.temporaryTableIds = tableIds
  tablePickerSelectionMode.value = tableIds.length > 0 ? 'temporary' : tablePickerSelectionMode.value
  temporaryGroupApiError.value = null
}

function toggleTemporaryGroupMode(): void {
  if (isTemporaryGroupMode.value) {
    tablePickerSelectionMode.value = 'single'
    clearTemporaryGroupDraft()
    return
  }

  form.tablePreference = 'unassigned'
  temporaryGroupApiError.value = null
  tablePickerSelectionMode.value = 'temporary'
}

async function saveTemporaryGroupForReservation(): Promise<void> {
  const groupName = temporaryGroupName.value.trim()

  if (!props.storeId || !canSaveTemporaryGroup.value) {
    temporaryGroupApiError.value = createTemporaryGroupLocalError(
      'GROUP_NAME_REQUIRED',
      'table.temporary_group.group_name_required'
    )
    return
  }

  temporaryGroupApiError.value = null
  isSavingTemporaryGroup.value = true

  try {
    const result = await saveTemporaryTableGroup(props.storeId, {
      groupName,
      tableIds: form.temporaryTableIds,
      businessDate: form.businessDate
    })

    form.tablePreference = `table_group:${result.tableGroupId}`
    clearTemporaryGroupDraft()
    tablePickerSelectionMode.value = 'single'
    await loadTableResources()
    isTablePickerOpen.value = false
  } catch (error) {
    temporaryGroupApiError.value =
      error instanceof TemporaryTableGroupApiError
        ? error.response
        : createTemporaryGroupLocalError('REQUEST_FAILED', 'table.temporary_group.request_failed')
  } finally {
    isSavingTemporaryGroup.value = false
  }
}

function clearTemporaryGroupDraft(): void {
  form.temporaryTableIds = []
  temporaryGroupName.value = ''
  temporaryGroupApiError.value = null
}

function resourceOptionValue(resource: TableResourceItem): string {
  return `${resource.resourceType}:${resource.resourceId}`
}

function resourceDisplayName(resource: TableResourceItem): string {
  return resource.displayName || resource.code
}

function resourceCapacityText(resource: TableResourceItem): string {
  return t('reservationWorkbench.createDialog.capacity', { count: resource.capacityMax })
}

function resourcePickerStatusText(resource: TableResourceItem): string {
  return resource.selectable ? t('reservationWorkbench.createDialog.available') : statusDisabledText(resource)
}

function statusDisabledText(resource: TableResourceItem): string {
  const reasonLabels: Record<string, string> = {
    status_unavailable: t('reservationWorkbench.createDialog.unavailable'),
    capacity_mismatch: t('reservationWorkbench.createDialog.capacityMismatch'),
    locked: t('reservationWorkbench.createDialog.locked'),
    occupied: t('reservationWorkbench.createDialog.occupied'),
    cleaning: t('reservationWorkbench.createDialog.cleaning'),
    reservation_preassigned: t('reservationWorkbench.createDialog.reserved')
  }
  const reason = resource.selectionDisabledReason?.trim()
  return reason ? reasonLabels[reason] ?? t('reservationWorkbench.createDialog.unavailable') : t('reservationWorkbench.createDialog.unavailable')
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(): string {
  const prefix = 'reservation:create'
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): ReservationApiErrorResponse {
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

function createTableResourceLocalError(
  code: string,
  messageKey: string
): TableResourceApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createMealPeriodLocalError(
  code: string,
  messageKey: string
): ReservationMealPeriodApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

function createTemporaryGroupLocalError(
  code: string,
  messageKey: string
): TemporaryTableGroupApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}

async function loadCreatedShareInfo(reservationId: string): Promise<void> {
  if (!props.storeId || !reservationId) {
    return
  }

  isLoadingCreatedShare.value = true
  createdShareErrorText.value = ''
  createdShareShared.value = false
  createdShareStatusText.value = ''
  createdShareFallbackText.value = ''

  try {
    const response = await getReservationShareInfo(props.storeId, reservationId, activeLocale.value)
    createdShareInfo.value = response.shareInfo
  } catch (error) {
    createdShareInfo.value = null
    createdShareErrorText.value = error instanceof ReservationShareInfoApiError
      ? t('reservationWorkbench.share.loadFailed')
      : t('reservationWorkbench.share.loadFailed')
  } finally {
    isLoadingCreatedShare.value = false
  }
}

async function shareCreatedReservationLink(): Promise<void> {
  await systemShareCreatedReservationLink()
}

async function systemShareCreatedReservationLink(): Promise<void> {
  const info = await ensureCreatedShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    createdShareErrorText.value = t('reservationWorkbench.share.noForwardLink')
    return
  }

  resetCreatedShareFeedback()

  const result = await shareLinkOrCopy({
    title: info.shareTitle,
    text: info.shareSummary || info.shareText,
    url
  })

  if (result === 'cancelled') {
    return
  }

  if (result === 'copied' || result === 'native-share') {
    await recordCreatedShareIntent(result === 'copied' ? 'copy_link' : 'system_share')
    createdShareShared.value = true
    createdShareStatusText.value = result === 'copied'
      ? t('reservationWorkbench.share.copied')
      : t('reservationWorkbench.share.nativeOpened')
    return
  }

  createdShareFallbackText.value = url
  createdShareErrorText.value = t('reservationWorkbench.share.manualShare')
}

async function openWhatsApp(): Promise<void> {
  const info = await ensureCreatedShareInfo()
  if (!info?.canOpenWhatsAppLink || !info.whatsappLink) {
    createdShareErrorText.value = info?.customerPhoneAvailable
      ? t('reservationWorkbench.share.phoneUnavailable')
      : t('reservationWorkbench.share.phoneMissing')
    return
  }

  resetCreatedShareFeedback()
  await recordCreatedShareIntent('whatsapp')
  createdShareShared.value = true
  createdShareStatusText.value = t('reservationWorkbench.share.whatsappOpening', {
    sender: info.senderLabel || t('reservationWorkbench.share.defaultSender')
  })
  window.location.href = info.whatsappLink
}

async function openWechat(): Promise<void> {
  const info = await ensureCreatedShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  const text = info ? reservationWechatShareText(info, url) : ''
  if (!info || !text) {
    createdShareErrorText.value = t('reservationWorkbench.share.noText')
    return
  }

  resetCreatedShareFeedback()
  if (!(await copyPlainText(text))) {
    createdShareFallbackText.value = text
    createdShareErrorText.value = t('reservationWorkbench.share.manualTextCopy')
    return
  }
  await recordCreatedShareIntent('wechat')
  createdShareShared.value = true
  createdShareStatusText.value = t('reservationWorkbench.share.wechatOpening')
  if (info.wechatLink) {
    window.location.href = info.wechatLink
  }
}

async function copyCreatedReservationShareLink(): Promise<void> {
  const info = await ensureCreatedShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    createdShareErrorText.value = t('reservationWorkbench.share.noCopyLink')
    return
  }

  resetCreatedShareFeedback()
  if (await copyPlainText(url)) {
    await recordCreatedShareIntent('copy_link')
    createdShareShared.value = true
    createdShareStatusText.value = t('reservationWorkbench.share.copied')
    return
  }

  createdShareFallbackText.value = url
  createdShareErrorText.value = t('reservationWorkbench.share.manualCopyLink')
}

async function ensureCreatedShareInfo(): Promise<ReservationShareInfo | null> {
  if (!createdShareInfo.value && createdReservation.value) {
    await loadCreatedShareInfo(createdReservation.value.reservationId)
  }

  return createdShareInfo.value
}

async function recordCreatedShareIntent(channel: ReservationShareIntentChannel): Promise<void> {
  if (!createdReservation.value) {
    return
  }
  try {
    await recordReservationShareIntent(
      props.storeId,
      createdReservation.value.reservationId,
      channel,
      activeLocale.value
    )
  } catch {
    // Sending should remain possible even when audit recording is temporarily unavailable.
  }
}

function resetCreatedShareFeedback(): void {
  createdShareErrorText.value = ''
  createdShareShared.value = false
  createdShareStatusText.value = ''
  createdShareFallbackText.value = ''
}

function clearCreatedReservationShareState(): void {
  createdReservation.value = null
  createdShareInfo.value = null
  isLoadingCreatedShare.value = false
  createdShareErrorText.value = ''
  createdShareShared.value = false
  createdShareStatusText.value = ''
  createdShareFallbackText.value = ''
}

function resetAfterSuccess(): void {
  form.customerId = ''
  form.customerName = ''
  form.customerSalutation = ''
  form.phoneLocal = ''
  form.partySize = 2
  form.tablePreference = 'unassigned'
  form.selectedStartAt = ''
  selectedMealPeriodKey.value = ALL_MEAL_PERIOD_KEY
  clearTemporaryGroupDraft()
}

function applyDefaultFutureDateTime(selectedDate: string): void {
  const nextSelectedDate =
    props.minDate && selectedDate < props.minDate ? props.minDate : selectedDate
  const next = defaultFutureReservationDateTime(nextSelectedDate)
  form.businessDate = next.businessDate
  form.selectedStartAt = ''
  selectedMealPeriodKey.value = ALL_MEAL_PERIOD_KEY
}

function isBeforeMinDate(value: string): boolean {
  return !!props.minDate && !!value && value < props.minDate
}

function reservationShareUrl(info: ReservationShareInfo): string {
  const path = info.sharePath?.trim() || (info.shareToken ? `/reservation-share/${info.shareToken}` : '')
  return path ? new URL(path, window.location.origin).toString() : ''
}
</script>

<template>
  <Teleport to="body">
    <section
      v-if="open"
      class="reservation-create-dialog"
      :aria-label="$t('reservationWorkbench.createDialog.aria')"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-create-dialog__backdrop" @click="close"></div>

      <form class="reservation-create-dialog__panel" @submit.prevent="submit">
        <header>
          <h2>{{ $t('reservationWorkbench.createDialog.title') }}</h2>
          <button
            type="button"
            :aria-label="$t('reservationWorkbench.createDialog.closeAria')"
            :disabled="isSubmitting || isLoadingCreatedShare"
            @click="close"
          >
            ×
          </button>
        </header>

        <section v-if="createdReservation" class="reservation-create-dialog__success" aria-live="polite">
          <div>
            <strong>{{ $t('reservationWorkbench.createDialog.success') }}</strong>
            <span>
              {{
                $t('reservationWorkbench.createDialog.successSummary', {
                  code: createdReservation.reservationCode,
                  count: createdReservation.partySize
                })
              }}
            </span>
          </div>

          <ReservationShareCopyPanel
            :share-info="createdShareInfo"
            :loading="isLoadingCreatedShare"
            :shared="createdShareShared"
            :status-text="createdShareStatusText"
            :error-text="createdShareErrorText"
            :fallback-text="createdShareFallbackText"
            @whatsapp-requested="openWhatsApp"
            @wechat-requested="openWechat"
            @system-share-requested="shareCreatedReservationLink"
            @copy-requested="copyCreatedReservationShareLink"
          />

          <footer>
            <button class="reservation-create-dialog__save" type="button" @click="finishCreatedReservationFlow">
              {{ $t('reservationWorkbench.createDialog.done') }}
            </button>
          </footer>
        </section>

        <template v-else>
        <StaffGuestContactLookup
          :store-id="storeId"
          v-model:customer-id="form.customerId"
          v-model:customer-name="form.customerName"
          v-model:salutation="form.customerSalutation"
          v-model:phone-local="form.phoneLocal"
          :disabled="isSubmitting"
        />

        <label>
          <span>{{ $t('reservationWorkbench.createDialog.date') }}</span>
          <input v-model="form.businessDate" :min="minDate" name="businessDate" type="date" />
        </label>

        <div class="reservation-create-dialog__field">
          <span>{{ $t('reservationWorkbench.createDialog.time') }}</span>
          <div
            v-if="mealPeriodFilterOptions.length > 1"
            class="reservation-create-meal-period-filter"
            role="group"
            :aria-label="$t('reservationWorkbench.createDialog.mealFilterAria')"
          >
            <button
              v-for="option in mealPeriodFilterOptions"
              :key="option.periodKey"
              class="reservation-create-meal-period-filter__item"
              :class="{ 'is-selected': option.periodKey === selectedMealPeriodKey }"
              type="button"
              :aria-pressed="option.periodKey === selectedMealPeriodKey"
              :disabled="isSubmitting"
              @click="selectMealPeriod(option.periodKey)"
            >
              <span>{{ option.displayName }}</span>
              <small>{{ option.count }}</small>
            </button>
          </div>
          <div class="reservation-create-time-slots" role="listbox" :aria-label="$t('reservationWorkbench.createDialog.timeSlotsAria')">
            <button
              v-for="slot in filteredTimeSlots"
              :key="slot.startAt"
              class="reservation-create-time-slots__item"
              :class="{ 'is-selected': slot.startAt === form.selectedStartAt }"
              type="button"
              :disabled="isSubmitting"
              @click="selectTimeSlot(slot)"
            >
              <strong>{{ slot.time }}</strong>
              <small>{{ slot.displayName }}{{ slot.nextDay ? $t('reservationWorkbench.createDialog.nextDay') : '' }}</small>
            </button>
          </div>
          <small v-if="isLoadingTimeSlots" class="reservation-create-dialog__hint">
            {{ $t('reservationWorkbench.createDialog.loadingTime') }}
          </small>
          <small v-else-if="timeSlotApiError" class="reservation-create-dialog__hint">
            {{ $t('reservationWorkbench.createDialog.timeLoadFailed') }}
          </small>
          <small v-else-if="filteredTimeSlots.length === 0" class="reservation-create-dialog__hint">
            {{ $t('reservationWorkbench.createDialog.noTimeSlots') }}
          </small>
        </div>

        <label>
          <span>{{ $t('reservationWorkbench.createDialog.partySize') }}</span>
          <input v-model.number="form.partySize" min="1" name="partySize" type="number" />
        </label>

        <div class="reservation-create-dialog__field">
          <span>{{ $t('reservationWorkbench.createDialog.optionalTable') }}</span>
          <div class="reservation-create-dialog__table-field">
            <button
              class="reservation-create-dialog__table-trigger"
              type="button"
              :disabled="isSubmitting"
              @click="openTablePicker"
            >
              <strong>{{ tablePreferenceDisplay }}</strong>
              <small>{{ tablePreferenceMeta }}</small>
            </button>
            <button
              v-if="selectedResource"
              class="reservation-create-dialog__table-clear"
              type="button"
              :disabled="isSubmitting"
              @click="clearTablePreference"
            >
              {{ $t('reservationWorkbench.createDialog.unassigned') }}
            </button>
          </div>
          <small v-if="isLoadingTableResources" class="reservation-create-dialog__hint">
            {{ $t('reservationWorkbench.createDialog.loadingTables') }}
          </small>
          <small v-else-if="tableResourceApiError" class="reservation-create-dialog__hint">
            {{ $t('reservationWorkbench.createDialog.tableLoadFailed') }}
          </small>
        </div>

        <section
          v-if="isTablePickerOpen"
          class="reservation-create-table-picker"
          :aria-label="$t('reservationWorkbench.createDialog.tablePickerAria')"
          aria-modal="true"
          role="dialog"
        >
          <div class="reservation-create-table-picker__backdrop" @click="closeTablePicker"></div>
          <div class="reservation-create-table-picker__panel">
            <header>
              <h3>{{ $t('reservationWorkbench.createDialog.tablePickerTitle') }}</h3>
              <button type="button" :aria-label="$t('reservationWorkbench.createDialog.closeTablePicker')" @click="closeTablePicker">
                ×
              </button>
            </header>

            <p class="reservation-create-table-picker__summary">
              {{ $t('reservationWorkbench.createDialog.currentSelection', { selection: tablePreferenceDisplay }) }}
            </p>

            <section class="reservation-create-table-picker__temporary-panel" :aria-label="$t('reservationWorkbench.createDialog.temporaryGroupAria')">
              <div>
                <strong>{{ $t('reservationWorkbench.createDialog.temporaryGroup') }}</strong>
                <span>
                  {{
                    $t('reservationWorkbench.createDialog.temporarySummary', {
                      date: form.businessDate,
                      count: form.temporaryTableIds.length
                    })
                  }}
                </span>
              </div>
              <label>
                <span>{{ $t('reservationWorkbench.createDialog.groupName') }}</span>
                <input
                  v-model.trim="temporaryGroupName"
                  name="reservationTemporaryGroupName"
                  :placeholder="$t('reservationWorkbench.createDialog.groupNamePlaceholder')"
                  type="text"
                />
              </label>
              <p
                v-if="temporaryGroupApiError"
                class="reservation-create-table-picker__temporary-error"
                aria-live="assertive"
              >
                {{ temporaryGroupApiError.error.messageKey }}
              </p>
              <div class="reservation-create-table-picker__temporary-actions">
                <button
                  type="button"
                  :disabled="isSavingTemporaryGroup"
                  @click="toggleTemporaryGroupMode"
                >
                  {{ isTemporaryGroupMode ? $t('reservationWorkbench.createDialog.exitSelection') : $t('reservationWorkbench.createDialog.composeTables') }}
                </button>
                <button
                  type="button"
                  :disabled="form.temporaryTableIds.length === 0 || isSavingTemporaryGroup"
                  @click="clearTemporaryGroupDraft"
                >
                  {{ $t('common.actions.clear') }}
                </button>
                <button
                  class="reservation-create-table-picker__temporary-save"
                  type="button"
                  :disabled="!canSaveTemporaryGroup"
                  @click="saveTemporaryGroupForReservation"
                >
                  {{ isSavingTemporaryGroup ? $t('reservationWorkbench.createDialog.saveGroupSubmitting') : $t('reservationWorkbench.createDialog.saveGroup') }}
                </button>
              </div>
            </section>

            <TableResourcePicker
              v-model:selection-mode="tablePickerSelectionMode"
              :store-id="storeId"
              :party-size="null"
              :business-date="form.businessDate"
              :selected-table-id="selectedTableId"
              :selected-table-group-id="selectedTableGroupId"
              :selected-temporary-table-ids="form.temporaryTableIds"
              :show-selection-mode-controls="false"
              :available-only="true"
              temporary-selection-enabled
              @select-table="selectTable"
              @select-table-group="selectTableGroup"
              @select-temporary-tables="selectTemporaryTables"
            />

            <footer>
              <button type="button" @click="closeTablePicker">{{ $t('common.actions.cancel') }}</button>
            </footer>
          </div>
        </section>

        <section v-if="apiError" class="reservation-create-dialog__error" aria-live="assertive">
          {{ formatReservationCreateErrorMessage(apiError.error.messageKey) }}
        </section>

        <footer>
          <button class="reservation-create-dialog__save" :disabled="!canSubmit" type="submit">
            {{ isSubmitting ? $t('reservationWorkbench.createDialog.saveSubmitting') : $t('reservationWorkbench.createDialog.save') }}
          </button>
          <button class="reservation-create-dialog__cancel" type="button" :disabled="isSubmitting" @click="close">
            {{ $t('common.actions.cancel') }}
          </button>
        </footer>
        </template>
      </form>
    </section>
  </Teleport>
</template>

<style scoped>
.reservation-create-dialog {
  align-items: center;
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 18px;
  position: fixed;
  z-index: 80;
}

.reservation-create-dialog__backdrop {
  backdrop-filter: blur(4px);
  background: rgba(15, 23, 42, 0.46);
  inset: 0;
  position: absolute;
}

.reservation-create-dialog__panel {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
  display: grid;
  gap: 11px;
  max-height: min(92dvh, 720px);
  max-width: 382px;
  overflow-y: auto;
  padding: 24px 20px 22px;
  position: relative;
  width: min(100%, 382px);
}

.reservation-create-dialog__panel header {
  align-items: center;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-create-dialog__panel h2 {
  color: #14213d;
  font-size: 1.15rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-create-dialog__panel h2::before {
  content: '▦';
  color: #5b7cff;
  font-size: 1rem;
  margin-right: 8px;
}

.reservation-create-dialog__panel header button {
  background: transparent;
  border: 0;
  color: #94a3b8;
  font-size: 1.55rem;
  font-weight: 800;
  height: 32px;
  line-height: 1;
  padding: 0;
  width: 32px;
}

.reservation-create-dialog__panel label,
.reservation-create-dialog__field {
  color: #0f172a;
  display: grid;
  font-size: 0.82rem;
  font-weight: 900;
  gap: 6px;
}

.reservation-create-dialog__panel input,
.reservation-create-dialog__panel select {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  min-height: 36px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.reservation-create-dialog__panel input:focus,
.reservation-create-dialog__panel select:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.reservation-create-dialog__error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  color: #be123c;
  font-size: 0.82rem;
  font-weight: 800;
  padding: 9px 11px;
}

.reservation-create-dialog__success {
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.reservation-create-dialog__success > div {
  display: grid;
  gap: 4px;
}

.reservation-create-dialog__success strong {
  color: #166534;
  font-size: 0.96rem;
  font-weight: 950;
}

.reservation-create-dialog__success span {
  color: #334155;
  font-size: 0.82rem;
  font-weight: 850;
  overflow-wrap: anywhere;
}

.reservation-create-dialog__hint {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 800;
}

.reservation-create-meal-period-filter {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  padding: 4px;
}

.reservation-create-meal-period-filter__item {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #334155;
  display: flex;
  gap: 5px;
  justify-content: center;
  min-height: 30px;
  min-width: 0;
  padding: 0 8px;
}

.reservation-create-meal-period-filter__item span {
  font-size: 0.78rem;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reservation-create-meal-period-filter__item small {
  color: #64748b;
  font-size: 0.68rem;
  font-weight: 900;
}

.reservation-create-meal-period-filter__item.is-selected {
  background: #ffffff;
  border-color: #fdba74;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  color: #c2410c;
}

.reservation-create-meal-period-filter__item.is-selected small {
  color: #ea580c;
}

.reservation-create-time-slots {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 178px;
  overflow-y: auto;
}

.reservation-create-time-slots__item {
  min-height: 48px;
  display: grid;
  align-content: center;
  gap: 2px;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
  padding: 6px 10px;
}

.reservation-create-time-slots__item strong,
.reservation-create-time-slots__item small {
  overflow-wrap: anywhere;
}

.reservation-create-time-slots__item strong {
  font-size: 0.92rem;
  font-weight: 950;
}

.reservation-create-time-slots__item small {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 850;
}

.reservation-create-time-slots__item.is-selected {
  border-color: #f97316;
  background: #fff7ed;
}

.reservation-create-time-slots__item:disabled {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #94a3b8;
  cursor: default;
}

.reservation-create-time-slots__item:disabled small {
  color: #94a3b8;
}

.reservation-create-dialog__table-field {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-create-dialog__table-trigger {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 2px;
  justify-items: start;
  min-height: 46px;
  min-width: 0;
  padding: 7px 12px;
  text-align: left;
  width: 100%;
}

.reservation-create-dialog__table-trigger strong {
  font-size: 0.9rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.reservation-create-dialog__table-trigger small {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.reservation-create-dialog__table-trigger:disabled {
  background: #f8fafc;
  color: #94a3b8;
}

.reservation-create-dialog__table-clear {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #334155;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 46px;
  padding: 0 12px;
}

.reservation-create-dialog__panel footer {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 8px;
}

.reservation-create-dialog__save,
.reservation-create-dialog__cancel {
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 950;
  min-height: 40px;
  padding: 0 16px;
}

.reservation-create-dialog__save {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-create-dialog__save:disabled {
  background: #fdba74;
  border-color: #fdba74;
}

.reservation-create-dialog__cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.reservation-create-dialog__panel button:focus-visible,
.reservation-create-dialog__panel input:focus-visible,
.reservation-create-dialog__panel select:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

.reservation-create-table-picker {
  align-items: center;
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 18px;
  position: fixed;
  z-index: 86;
}

.reservation-create-table-picker__backdrop {
  backdrop-filter: blur(4px);
  background: rgba(15, 23, 42, 0.46);
  inset: 0;
  position: absolute;
}

.reservation-create-table-picker__panel {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
  display: grid;
  gap: 12px;
  max-height: min(88dvh, 640px);
  max-width: 430px;
  overflow-y: auto;
  padding: 24px 20px 22px;
  position: relative;
  width: min(100%, 430px);
}

.reservation-create-table-picker__panel header {
  align-items: center;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
}

.reservation-create-table-picker__panel h3 {
  color: #14213d;
  font-size: 1.05rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-create-table-picker__panel h3::before {
  color: #5b7cff;
  content: '▦';
  font-size: 0.95rem;
  margin-right: 8px;
}

.reservation-create-table-picker__panel header button {
  background: transparent;
  border: 0;
  color: #94a3b8;
  font-size: 1.55rem;
  font-weight: 800;
  height: 32px;
  line-height: 1;
  padding: 0;
  width: 32px;
}

.reservation-create-table-picker__summary {
  color: #334155;
  font-size: 0.84rem;
  font-weight: 800;
  margin: 0;
}

.reservation-create-table-picker__panel footer {
  padding-top: 2px;
}

.reservation-create-table-picker__panel footer button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #334155;
  font-size: 0.92rem;
  font-weight: 950;
  min-height: 40px;
  padding: 0 16px;
  width: 100%;
}

.reservation-create-table-picker__temporary-panel {
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.reservation-create-table-picker__temporary-panel > div:first-child {
  display: grid;
  gap: 3px;
}

.reservation-create-table-picker__temporary-panel strong {
  color: #0f172a;
  font-size: 0.9rem;
  font-weight: 950;
}

.reservation-create-table-picker__temporary-panel span,
.reservation-create-table-picker__temporary-panel label span {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 850;
}

.reservation-create-table-picker__temporary-panel label {
  display: grid;
  gap: 6px;
}

.reservation-create-table-picker__temporary-panel input {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  min-height: 38px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.reservation-create-table-picker__temporary-error {
  color: #be123c;
  font-size: 0.8rem;
  font-weight: 850;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-create-table-picker__temporary-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.reservation-create-table-picker__temporary-actions button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #334155;
  font-size: 0.84rem;
  font-weight: 950;
  min-height: 38px;
  padding: 0 12px;
}

.reservation-create-table-picker__temporary-actions .reservation-create-table-picker__temporary-save {
  background: #fff7ed;
  border-color: #fdba74;
  color: #c2410c;
}

.reservation-create-table-picker__temporary-actions button:disabled {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
}
</style>
