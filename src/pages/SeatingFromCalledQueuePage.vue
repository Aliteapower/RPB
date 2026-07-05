<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  SeatingFromCalledQueueApiError,
  seatCalledQueueTicket
} from '../api/seatingFromCalledQueueApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import StaffHomeWorkflowStrip from '../components/staff-home/StaffHomeWorkflowStrip.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import TableResourcePicker from '../components/staff-table/TableResourcePicker.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  SeatCalledQueueTicketRequest,
  SeatingFromCalledQueueApiErrorResponse
} from '../types/seatingFromCalledQueue'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const router = useRouter()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const form = reactive({
  queueTicketId: '',
  tableId: '',
  tableGroupId: '',
  temporaryTableIds: [] as string[]
})

const isSubmitting = ref(false)
const apiError = ref<SeatingFromCalledQueueApiErrorResponse | null>(null)
const queuePartySize = ref<number | null>(null)
const assignedResourceType = ref('')
const assignedResourceId = ref('')
const assignedResourceCode = ref('')
const assignedResourceLabel = ref('')
const assignedResourceAreaName = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isSubmitting.value ? gt('generated.seating-from-called-queue.019') : gt('generated.seating-from-called-queue.020')))
const tableResourceListRoute = computed(() => ({
  name: 'table-resource-list',
  params: {
    storeId: storeId.value || ''
  }
}))
const hasQueueTicketId = computed(() => !!form.queueTicketId.trim())
const hasTableId = computed(() => !!form.tableId.trim())
const hasTableGroupId = computed(() => !!form.tableGroupId.trim())
const hasTemporaryTables = computed(() => form.temporaryTableIds.length > 0)
const selectedResourceCount = computed(
  () => Number(hasTableId.value) + Number(hasTableGroupId.value) + Number(hasTemporaryTables.value)
)
const hasAssignedResource = computed(() => !!assignedResourceType.value && !!assignedResourceId.value)
const assignedResourceSelectionSatisfied = computed(() => {
  if (!hasAssignedResource.value) {
    return true
  }

  if (assignedResourceType.value === 'dining_table') {
    return form.tableId === assignedResourceId.value && !hasTableGroupId.value && !hasTemporaryTables.value
  }

  if (assignedResourceType.value === 'table_group') {
    return form.tableGroupId === assignedResourceId.value && !hasTableId.value && !hasTemporaryTables.value
  }

  return false
})
const hasValidTemporaryTables = computed(
  () => !hasTemporaryTables.value || form.temporaryTableIds.length >= 2
)
const hasExactlyOneResource = computed(
  () => selectedResourceCount.value === 1 && hasValidTemporaryTables.value
)
const resourceSelectionError = computed(() => {
  if (!hasQueueTicketId.value) {
    return null
  }

  if (selectedResourceCount.value > 1) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'queue.seat.resource_selection_conflict')
      .error
  }

  if (form.temporaryTableIds.length === 1) {
    return createLocalError(
      'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED',
      'queue.seat.temporary_table_group_member_required'
    ).error
  }

  if (
    hasAssignedResource.value &&
    selectedResourceCount.value > 0 &&
    !assignedResourceSelectionSatisfied.value
  ) {
    return createLocalError('ASSIGNED_RESOURCE_REQUIRED', 'queue.seat.assigned_resource_required')
      .error
  }

  return null
})
const resourceSelectionHint = computed(() => {
  if (hasAssignedResource.value) {
    return assignedResourceSelectionSatisfied.value
      ? gt('generated.seating-from-called-queue.021')
      : gt('generated.seating-from-called-queue.022')
  }

  if (!hasQueueTicketId.value || selectedResourceCount.value !== 0) {
    return ''
  }

  return gt('generated.seating-from-called-queue.023')
})
const canSubmit = computed(
  () =>
    !isSubmitting.value &&
    !!storeId.value &&
    hasQueueTicketId.value &&
    hasExactlyOneResource.value &&
    assignedResourceSelectionSatisfied.value
)
const queueTicketContextText = computed(() =>
  hasQueueTicketId.value ? gt('generated.seating-from-called-queue.024') : gt('generated.seating-from-called-queue.025')
)
const assignedResourceDisplayText = computed(() => {
  const code = assignedResourceLabel.value || assignedResourceCode.value || assignedResourceId.value
  const prefix = assignedResourceType.value === 'table_group' ? gt('generated.seating-from-called-queue.026') : gt('generated.seating-from-called-queue.027')
  const area = assignedResourceAreaName.value ? ` · ${assignedResourceAreaName.value}` : ''

  return `${prefix} ${code}${area}`
})

watch(
  () => [
    route.query.queueTicketId,
    route.query.partySize,
    route.query.assignedResourceType,
    route.query.assignedResourceId,
    route.query.assignedResourceCode,
    route.query.assignedResourceLabel,
    route.query.assignedResourceAreaName
  ] as const,
  () => {
    const queueTicketId = queryValue(route.query.queueTicketId)

    if (queueTicketId && queueTicketId !== form.queueTicketId) {
      form.tableId = ''
      form.tableGroupId = ''
      form.temporaryTableIds = []
    }

    if (queueTicketId) {
      form.queueTicketId = queueTicketId
    }

    queuePartySize.value = parsePartySize(queryValue(route.query.partySize))
    assignedResourceType.value = normalizeAssignedResourceType(queryValue(route.query.assignedResourceType))
    assignedResourceId.value = queryValue(route.query.assignedResourceId)
    assignedResourceCode.value = queryValue(route.query.assignedResourceCode)
    assignedResourceLabel.value = queryValue(route.query.assignedResourceLabel)
    assignedResourceAreaName.value = queryValue(route.query.assignedResourceAreaName)
    applyAssignedResourceSelection()
  },
  { immediate: true }
)

async function submitQueueSeating(): Promise<void> {
  apiError.value = validateForm()

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  isSubmitting.value = true

  try {
    await seatCalledQueueTicket(
      storeId.value,
      form.queueTicketId.trim(),
      toRequest(),
      idempotencyKey
    )
    await router.push(tableResourceListRoute.value)
  } catch (error) {
    apiError.value =
      error instanceof SeatingFromCalledQueueApiError
        ? error.response
        : createLocalError('UNKNOWN_ERROR', 'queue.seat.unknown_error')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): SeatingFromCalledQueueApiErrorResponse | null {
  if (!storeId.value) {
    return createLocalError('STORE_SCOPE_MISMATCH', 'queue.seat.store_scope_mismatch')
  }

  if (!form.queueTicketId.trim()) {
    return createLocalError('INVALID_COMMAND', 'queue.seat.queue_ticket_id_required')
  }

  if (selectedResourceCount.value === 0) {
    return createLocalError('RESOURCE_SELECTION_REQUIRED', 'queue.seat.resource_selection_required')
  }

  if (selectedResourceCount.value > 1) {
    return createLocalError('RESOURCE_SELECTION_CONFLICT', 'queue.seat.resource_selection_conflict')
  }

  if (form.temporaryTableIds.length === 1) {
    return createLocalError(
      'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED',
      'queue.seat.temporary_table_group_member_required'
    )
  }

  if (hasAssignedResource.value && !assignedResourceSelectionSatisfied.value) {
    return createLocalError('ASSIGNED_RESOURCE_REQUIRED', 'queue.seat.assigned_resource_required')
  }

  return null
}

function selectTable(tableId: string): void {
  clearSubmissionError()
  if (!isAssignedResourceSelectionAllowed('dining_table', tableId)) {
    apiError.value = createLocalError('ASSIGNED_RESOURCE_REQUIRED', 'queue.seat.assigned_resource_required')
    applyAssignedResourceSelection()
    return
  }

  form.tableId = tableId
  form.tableGroupId = ''
  form.temporaryTableIds = []
}

function selectTableGroup(tableGroupId: string): void {
  clearSubmissionError()
  if (!isAssignedResourceSelectionAllowed('table_group', tableGroupId)) {
    apiError.value = createLocalError('ASSIGNED_RESOURCE_REQUIRED', 'queue.seat.assigned_resource_required')
    applyAssignedResourceSelection()
    return
  }

  form.tableGroupId = tableGroupId
  form.tableId = ''
  form.temporaryTableIds = []
}

function selectTemporaryTables(tableIds: string[]): void {
  clearSubmissionError()
  if (hasAssignedResource.value) {
    apiError.value = createLocalError('ASSIGNED_RESOURCE_REQUIRED', 'queue.seat.assigned_resource_required')
    applyAssignedResourceSelection()
    return
  }

  form.temporaryTableIds = tableIds
  form.tableId = ''
  form.tableGroupId = ''
}

function applyAssignedResourceSelection(): void {
  if (!hasAssignedResource.value) {
    return
  }

  if (assignedResourceType.value === 'table_group') {
    form.tableGroupId = assignedResourceId.value
    form.tableId = ''
    form.temporaryTableIds = []
    return
  }

  form.tableId = assignedResourceId.value
  form.tableGroupId = ''
  form.temporaryTableIds = []
}

function isAssignedResourceSelectionAllowed(resourceType: string, resourceId: string): boolean {
  return (
    !hasAssignedResource.value ||
    (assignedResourceType.value === resourceType && assignedResourceId.value === resourceId)
  )
}

function clearSubmissionError(): void {
  apiError.value = null
}

function toRequest(): SeatCalledQueueTicketRequest {
  return {
    tableId: optionalValue(form.tableId),
    tableGroupId: optionalValue(form.tableGroupId),
    temporaryTableIds: form.temporaryTableIds.length ? form.temporaryTableIds : null
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(): string {
  const prefix = 'queue:seat'
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(
  code: string,
  messageKey: string
): SeatingFromCalledQueueApiErrorResponse {
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

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return gt('generated.seating-from-called-queue.028')
  }

  return `${gt('generated.seating-from-called-queue.018')}${value.slice(0, 8)}`
}

function queryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}

function parsePartySize(value: string): number | null {
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function normalizeAssignedResourceType(value: string): string {
  return value === 'dining_table' || value === 'table_group' ? value : ''
}
</script>

<template>
  <main class="staff-workbench-shell queue-seating-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="queue-seating-workbench-body">
      <StaffHomeWorkflowStrip />

      <form class="queue-seating-form" @submit.prevent="submitQueueSeating">
        <header class="queue-seating-heading">
          <div>
            <p>{{ gt('generated.seating-from-called-queue.001') }}</p>
            <h1>{{ gt('generated.seating-from-called-queue.002') }}</h1>
          </div>
          <span class="queue-source-pill">{{ queueTicketContextText }}</span>
        </header>

        <section class="queue-ticket-context" :aria-label="gt('generated.seating-from-called-queue.003')">
          <span>{{ gt('generated.seating-from-called-queue.004') }}</span>
          <strong>{{ hasQueueTicketId ? gt('generated.seating-from-called-queue.005') : gt('generated.seating-from-called-queue.006') }}</strong>
        </section>

        <section v-if="hasAssignedResource" class="assigned-resource-context" :aria-label="gt('generated.seating-from-called-queue.007')">
          <span>{{ gt('generated.seating-from-called-queue.008') }}</span>
          <strong>{{ assignedResourceDisplayText }}</strong>
          <small>{{ gt('generated.seating-from-called-queue.009') }}</small>
        </section>

        <section class="resource-panel" :aria-label="gt('generated.seating-from-called-queue.010')">
          <TableResourcePicker
            :store-id="storeId"
            :party-size="queuePartySize"
            :business-date="currentBusinessDate"
            :available-only="true"
            :selected-table-id="form.tableId"
            :selected-table-group-id="form.tableGroupId"
            :selected-temporary-table-ids="form.temporaryTableIds"
            :required-resource-type="assignedResourceType"
            :required-resource-id="assignedResourceId"
            :temporary-selection-enabled="!hasAssignedResource"
            :show-selection-mode-controls="!hasAssignedResource"
            @select-table="selectTable"
            @select-table-group="selectTableGroup"
            @select-temporary-tables="selectTemporaryTables"
          />
          <p v-if="resourceSelectionError" class="resource-error"> {{ gt('generated.seating-from-called-queue.011') }}{{ resourceSelectionError.code }}<br /> {{ gt('generated.seating-from-called-queue.012') }}{{ resourceSelectionError.messageKey }}
          </p>
          <p v-if="resourceSelectionHint" class="resource-hint">
            {{ resourceSelectionHint }}
          </p>
        </section>

        <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
          <h2>{{ gt('generated.seating-from-called-queue.013') }}</h2>
          <p class="error-code">{{ gt('generated.seating-from-called-queue.014') }}{{ apiError.error.code }}</p>
          <p class="message-key">{{ gt('generated.seating-from-called-queue.015') }}{{ apiError.error.messageKey }}</p>
        </section>

        <button class="submit-button" :disabled="!canSubmit" type="submit">
          {{ isSubmitting ? gt('generated.seating-from-called-queue.016') : gt('generated.seating-from-called-queue.017') }}
        </button>
      </form>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="queue" />
  </main>
</template>

<style scoped>
.queue-seating-workbench-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.queue-seating-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
}

.queue-seating-form {
  display: grid;
  gap: 14px;
  padding: 14px;
}

.queue-seating-heading {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.queue-seating-heading > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.queue-seating-heading p,
.queue-ticket-context span,
.assigned-resource-context span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 0;
}

h1,
h2,
.queue-ticket-context strong,
.assigned-resource-context strong {
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

.queue-source-pill {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 0.76rem;
  font-weight: 900;
  justify-content: center;
  min-height: 30px;
  padding: 0 10px;
  white-space: nowrap;
}

.queue-ticket-context {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
}

.queue-ticket-context strong {
  font-size: 0.96rem;
}

.assigned-resource-context {
  background: #ecfdf5;
  border: 1px solid #5eead4;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
}

.assigned-resource-context strong {
  color: #0f766e;
  font-size: 1.08rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.assigned-resource-context small {
  color: #0f766e;
  font-size: 0.78rem;
  font-weight: 800;
}

.resource-panel {
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  display: grid;
  gap: 12px;
  padding: 10px 12px;
}

.resource-error {
  color: #b42318;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

.resource-hint {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #475569;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
  padding: 9px 10px;
}

.submit-button {
  background: #197a55;
  border: 0;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 900;
  min-height: 52px;
  padding: 0 16px;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.result-panel {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.error-panel {
  border-color: #fecaca;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  margin: 0;
}

.message-key {
  color: #41516a;
  margin: 0;
  overflow-wrap: anywhere;
}

.idempotency-key {
  overflow-wrap: anywhere;
}

@media (min-width: 720px) {
  .queue-seating-workbench-body {
    padding-top: 16px;
  }
}
</style>
