<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import { createReservation, ReservationCreateApiError } from '../../api/reservationCreateApi'
import {
  getReservationShareInfo,
  ReservationShareInfoApiError
} from '../../api/reservationShareInfoApi'
import { fetchTableResources, TableResourceApiError } from '../../api/tableResourceApi'
import {
  saveTemporaryTableGroup,
  TemporaryTableGroupApiError
} from '../../api/temporaryTableGroupApi'
import { shareLinkOrCopy } from '../../utils/reservationShareLauncher'
import { formatReservationCreateErrorMessage } from '../../utils/reservationCreateMessages'
import StaffGuestContactLookup from '../staff/StaffGuestContactLookup.vue'
import StaffTimeWheelPicker from '../staff/StaffTimeWheelPicker.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../staff/staffGuestContact'
import TableResourcePicker from '../staff-table/TableResourcePicker.vue'
import ReservationShareCopyPanel from './ReservationShareCopyPanel.vue'
import {
  defaultFutureReservationDateTime,
  isReservationStartInPast
} from './reservationCreateTime'
import type {
  CreateReservationRequest,
  CreateReservationResponse,
  ReservationApiErrorResponse
} from '../../types/reservation'
import type { ReservationShareInfo } from '../../types/reservationShareInfo'
import type {
  TableResourceApiErrorResponse,
  TableResourceItem
} from '../../types/tableResource'
import type { TemporaryTableGroupApiErrorResponse } from '../../types/temporaryTableGroup'

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

type TablePickerSelectionMode = 'single' | 'temporary'

const form = reactive({
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  businessDate: props.selectedDate,
  time: '',
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

const canSubmit = computed(
  () =>
    props.open &&
    !!props.storeId &&
    !isSubmitting.value &&
    !createdReservation.value &&
    !!form.businessDate &&
    !!form.time &&
    !isBeforeMinDate(form.businessDate) &&
    Number.isInteger(form.partySize) &&
    form.partySize > 0
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
  selectedResource.value ? resourceDisplayName(selectedResource.value) : '未指定'
)
const tablePreferenceMeta = computed(() => {
  const resource = selectedResource.value

  if (resource) {
    return `${resourceCapacityText(resource)} · ${resourcePickerStatusText(resource)}`
  }

  if (isLoadingTableResources.value) {
    return '正在读取桌台'
  }

  if (tableResourceApiError.value) {
    return '桌台列表读取失败'
  }

  return '点击选择桌号'
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

watch(
  [() => props.open, () => props.storeId, () => form.businessDate],
  () => {
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

  if (!toIsoInstant()) {
    return createLocalError('INVALID_TIME_RANGE', 'reservation.invalid_time_range')
  }

  if (isBeforeMinDate(form.businessDate)) {
    return createLocalError('RESERVATION_START_IN_PAST', 'reservation.start_in_past')
  }

  if (isReservationStartInPast(form.businessDate, form.time)) {
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
    reservedStartAt: toIsoInstant() ?? '',
    reservedEndAt: null,
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerSalutation),
    phoneE164: toSingaporePhoneE164(form.phoneLocal),
    tableId: resource && resource.resourceType === 'dining_table' ? resource.resourceId : null,
    tableGroupId: resource && resource.resourceType === 'table_group' ? resource.resourceId : null
  }
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
  return `${resource.capacityMax}人`
}

function resourcePickerStatusText(resource: TableResourceItem): string {
  return resource.selectable ? '空闲' : statusDisabledText(resource)
}

function statusDisabledText(resource: TableResourceItem): string {
  const reasonLabels: Record<string, string> = {
    status_unavailable: '不可选',
    capacity_mismatch: '人数不匹配',
    locked: '已锁定',
    occupied: '已占用',
    cleaning: '清台中',
    reservation_preassigned: '已预留'
  }
  const reason = resource.selectionDisabledReason?.trim()
  return reason ? reasonLabels[reason] ?? '不可选' : '不可选'
}

function toIsoInstant(): string | null {
  if (!form.businessDate || !form.time) {
    return null
  }

  const timestamp = new Date(`${form.businessDate}T${form.time}`).getTime()
  return Number.isNaN(timestamp) ? null : new Date(timestamp).toISOString()
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
    const response = await getReservationShareInfo(props.storeId, reservationId)
    createdShareInfo.value = response.shareInfo
  } catch (error) {
    createdShareInfo.value = null
    createdShareErrorText.value =
      error instanceof ReservationShareInfoApiError
        ? '订位信息读取失败'
        : '订位信息读取失败'
  } finally {
    isLoadingCreatedShare.value = false
  }
}

async function shareCreatedReservationLink(): Promise<void> {
  if (!createdShareInfo.value && createdReservation.value) {
    await loadCreatedShareInfo(createdReservation.value.reservationId)
  }

  const info = createdShareInfo.value
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    createdShareErrorText.value = '暂无可转发链接'
    return
  }

  createdShareErrorText.value = ''
  createdShareShared.value = false
  createdShareStatusText.value = ''
  createdShareFallbackText.value = ''

  const result = await shareLinkOrCopy({
    title: info.shareTitle,
    text: info.shareSummary || info.shareText,
    url
  })

  if (result === 'cancelled') {
    return
  }

  if (result === 'copied' || result === 'native-share') {
    createdShareShared.value = true
    createdShareStatusText.value = result === 'copied' ? '链接已复制' : '已打开转发'
    return
  }

  createdShareFallbackText.value = url
  createdShareErrorText.value = '当前浏览器限制转发，请手动复制下方链接'
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
  clearTemporaryGroupDraft()
}

function applyDefaultFutureDateTime(selectedDate: string): void {
  const nextSelectedDate =
    props.minDate && selectedDate < props.minDate ? props.minDate : selectedDate
  const next = defaultFutureReservationDateTime(nextSelectedDate)
  form.businessDate = next.businessDate
  form.time = next.time
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
      aria-label="新增预约弹窗"
      aria-modal="true"
      role="dialog"
    >
      <div class="reservation-create-dialog__backdrop" @click="close"></div>

      <form class="reservation-create-dialog__panel" @submit.prevent="submit">
        <header>
          <h2>新增预约</h2>
          <button
            type="button"
            aria-label="关闭新增预约"
            :disabled="isSubmitting || isLoadingCreatedShare"
            @click="close"
          >
            ×
          </button>
        </header>

        <section v-if="createdReservation" class="reservation-create-dialog__success" aria-live="polite">
          <div>
            <strong>预约已创建</strong>
            <span>{{ createdReservation.reservationCode }} · {{ createdReservation.partySize }}人</span>
          </div>

          <ReservationShareCopyPanel
            :share-info="createdShareInfo"
            :loading="isLoadingCreatedShare"
            :shared="createdShareShared"
            :status-text="createdShareStatusText"
            :error-text="createdShareErrorText"
            :fallback-text="createdShareFallbackText"
            button-text="转发订位链接"
            @share-requested="shareCreatedReservationLink"
          />

          <footer>
            <button class="reservation-create-dialog__save" type="button" @click="finishCreatedReservationFlow">
              完成
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
          <span>日期</span>
          <input v-model="form.businessDate" :min="minDate" name="businessDate" type="date" />
        </label>

        <StaffTimeWheelPicker v-model="form.time" label="时间" name="reservationTime" />

        <label>
          <span>人数</span>
          <input v-model.number="form.partySize" min="1" name="partySize" type="number" />
        </label>

        <div class="reservation-create-dialog__field">
          <span>桌号（可选）</span>
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
              未指定
            </button>
          </div>
          <small v-if="isLoadingTableResources" class="reservation-create-dialog__hint">
            正在读取桌台
          </small>
          <small v-else-if="tableResourceApiError" class="reservation-create-dialog__hint">
            桌台列表读取失败
          </small>
        </div>

        <section
          v-if="isTablePickerOpen"
          class="reservation-create-table-picker"
          aria-label="选择预约桌号弹窗"
          aria-modal="true"
          role="dialog"
        >
          <div class="reservation-create-table-picker__backdrop" @click="closeTablePicker"></div>
          <div class="reservation-create-table-picker__panel">
            <header>
              <h3>选择预约桌号</h3>
              <button type="button" aria-label="关闭桌号选择" @click="closeTablePicker">
                ×
              </button>
            </header>

            <p class="reservation-create-table-picker__summary">
              当前选择：{{ tablePreferenceDisplay }}
            </p>

            <section class="reservation-create-table-picker__temporary-panel" aria-label="预约临时分组">
              <div>
                <strong>临时分组</strong>
                <span>{{ form.businessDate }} · 已选 {{ form.temporaryTableIds.length }} 张</span>
              </div>
              <label>
                <span>组名</span>
                <input
                  v-model.trim="temporaryGroupName"
                  name="reservationTemporaryGroupName"
                  placeholder="例如 A区临组1"
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
                  {{ isTemporaryGroupMode ? '退出选择' : '组合桌台' }}
                </button>
                <button
                  type="button"
                  :disabled="form.temporaryTableIds.length === 0 || isSavingTemporaryGroup"
                  @click="clearTemporaryGroupDraft"
                >
                  清空
                </button>
                <button
                  class="reservation-create-table-picker__temporary-save"
                  type="button"
                  :disabled="!canSaveTemporaryGroup"
                  @click="saveTemporaryGroupForReservation"
                >
                  {{ isSavingTemporaryGroup ? '保存中' : '保存分组' }}
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
              <button type="button" @click="closeTablePicker">取消</button>
            </footer>
          </div>
        </section>

        <section v-if="apiError" class="reservation-create-dialog__error" aria-live="assertive">
          {{ formatReservationCreateErrorMessage(apiError.error.messageKey) }}
        </section>

        <footer>
          <button class="reservation-create-dialog__save" :disabled="!canSubmit" type="submit">
            {{ isSubmitting ? '保存中...' : '保存' }}
          </button>
          <button class="reservation-create-dialog__cancel" type="button" :disabled="isSubmitting" @click="close">
            取消
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
