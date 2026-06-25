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

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isSubmitting.value ? '入座中' : '排队入座'))
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

  return null
})
const resourceSelectionHint = computed(() => {
  if (!hasQueueTicketId.value || selectedResourceCount.value !== 0) {
    return ''
  }

  return '请选择桌台、桌组，或在临时组合中选择至少 2 张桌台'
})
const canSubmit = computed(
  () =>
    !isSubmitting.value &&
    !!storeId.value &&
    hasQueueTicketId.value &&
    hasExactlyOneResource.value
)
const queueTicketContextText = computed(() =>
  hasQueueTicketId.value ? '已从排队列表带入' : '请从排队列表选择已叫号排队票'
)

watch(
  () => route.query.queueTicketId,
  value => {
    const queueTicketId = queryValue(value)

    if (queueTicketId) {
      form.queueTicketId = queueTicketId
    }
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

  return null
}

function selectTable(tableId: string): void {
  clearSubmissionError()
  form.tableId = tableId
  form.tableGroupId = ''
  form.temporaryTableIds = []
}

function selectTableGroup(tableGroupId: string): void {
  clearSubmissionError()
  form.tableGroupId = tableGroupId
  form.tableId = ''
  form.temporaryTableIds = []
}

function selectTemporaryTables(tableIds: string[]): void {
  clearSubmissionError()
  form.temporaryTableIds = tableIds
  form.tableId = ''
  form.tableGroupId = ''
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
    return '默认门店'
  }

  return `门店 ${value.slice(0, 8)}`
}

function queryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
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
            <p>排队管理</p>
            <h1>排队入座</h1>
          </div>
          <span class="queue-source-pill">{{ queueTicketContextText }}</span>
        </header>

        <section class="queue-ticket-context" aria-label="排队票来源">
          <span>入座来源</span>
          <strong>{{ hasQueueTicketId ? '排队列表已选中' : '缺少排队票' }}</strong>
        </section>

        <section class="resource-panel" aria-label="桌台选择">
          <TableResourcePicker
            :store-id="storeId"
            :available-only="true"
            :selected-table-id="form.tableId"
            :selected-table-group-id="form.tableGroupId"
            :selected-temporary-table-ids="form.temporaryTableIds"
            temporary-selection-enabled
            @select-table="selectTable"
            @select-table-group="selectTableGroup"
            @select-temporary-tables="selectTemporaryTables"
          />
          <p v-if="resourceSelectionError" class="resource-error">
            错误代码：{{ resourceSelectionError.code }}<br />
            消息键：{{ resourceSelectionError.messageKey }}
          </p>
          <p v-if="resourceSelectionHint" class="resource-hint">
            {{ resourceSelectionHint }}
          </p>
        </section>

        <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
          <h2>入座失败</h2>
          <p class="error-code">错误代码：{{ apiError.error.code }}</p>
          <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
        </section>

        <button class="submit-button" :disabled="!canSubmit" type="submit">
          {{ isSubmitting ? '入座中...' : '确认入座' }}
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
.queue-ticket-context span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 0;
}

h1,
h2,
.queue-ticket-context strong {
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
