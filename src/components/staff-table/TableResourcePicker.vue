<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  fetchTableResources,
  TableResourceApiError
} from '../../api/tableResourceApi'
import type {
  TableResourceApiErrorResponse,
  TableResourceItem
} from '../../types/tableResource'

const props = defineProps<{
  storeId: string
  partySize?: number | null
  selectedTableId?: string | null
  selectedTableGroupId?: string | null
  selectedTemporaryTableIds?: string[] | null
  businessDate?: string | null
  availableOnly?: boolean
  temporarySelectionEnabled?: boolean
}>()

const emit = defineEmits<{
  'select-table': [tableId: string]
  'select-table-group': [tableGroupId: string]
  'select-temporary-tables': [tableIds: string[]]
}>()

type SelectionMode = 'single' | 'temporary'

const resources = ref<TableResourceItem[]>([])
const isLoading = ref(false)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
const selectionMode = ref<SelectionMode>('single')
let loadSequence = 0

const statusLabels: Record<string, string> = {
  available: '可用',
  occupied: '占用',
  cleaning: '清台中',
  locked: '锁定',
  reserved: '预留',
  inactive: '停用',
  active: '分组',
  created: '已创建',
  released: '已释放',
  ended: '已结束'
}

const displayedResources = computed(() =>
  props.availableOnly ? resources.value.filter(resource => resource.selectable) : resources.value
)
const tableResources = computed(() =>
  displayedResources.value.filter(resource => resource.resourceType === 'dining_table')
)
const groupedTableResources = computed(() => {
  const groups = new Map<string, TableResourceItem[]>()

  for (const resource of tableResources.value) {
    const title = areaTitle(resource)
    groups.set(title, [...(groups.get(title) ?? []), resource])
  }

  return Array.from(groups, ([title, items]) => ({ title, items }))
})
const groupResources = computed(() =>
  displayedResources.value.filter(resource => resource.resourceType === 'table_group')
)
const selectedTemporaryTableIds = computed(() => props.selectedTemporaryTableIds ?? [])
const selectedTemporaryTableIdSet = computed(() => new Set(selectedTemporaryTableIds.value))
const temporarySelectionCount = computed(() => selectedTemporaryTableIds.value.length)
const showGroupResources = computed(() => selectionMode.value === 'single' && groupResources.value.length > 0)
const showEmpty = computed(
  () => !isLoading.value && !apiError.value && displayedResources.value.length === 0
)
const emptyText = computed(() => (props.availableOnly ? '暂无可用桌台。' : '暂无桌台，请先在后台配置桌台。'))
const pickerSubtitle = computed(() =>
  selectionMode.value === 'temporary'
    ? `临时组合 · 已选 ${temporarySelectionCount.value} 张`
    : '后台已配置资源'
)

watch(
  () => [props.storeId, props.partySize, props.businessDate] as const,
  () => {
    void loadResources()
  },
  { immediate: true }
)

watch(
  () => [props.temporarySelectionEnabled, temporarySelectionCount.value] as const,
  ([enabled, count]) => {
    if (!enabled) {
      selectionMode.value = 'single'
      return
    }

    if (count > 0) {
      selectionMode.value = 'temporary'
    }
  },
  { immediate: true }
)

async function loadResources(): Promise<void> {
  const sequence = ++loadSequence
  resources.value = []
  apiError.value = null

  if (!props.storeId) {
    isLoading.value = false
    return
  }

  isLoading.value = true

  try {
    const result = await fetchTableResources(props.storeId, {
      partySize: props.partySize ?? undefined,
      includeGroups: true,
      businessDate: props.businessDate?.trim() || undefined
    })

    if (sequence === loadSequence) {
      resources.value = result.resources
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

function chooseResource(resource: TableResourceItem): void {
  if (!canChooseResource(resource)) {
    return
  }

  if (selectionMode.value === 'temporary') {
    toggleTemporaryTable(resource.resourceId)
    return
  }

  if (resource.resourceType === 'dining_table') {
    emit('select-table', resource.resourceId)
    return
  }

  emit('select-table-group', resource.resourceId)
}

function switchSelectionMode(mode: SelectionMode): void {
  if (mode === 'temporary' && !props.temporarySelectionEnabled) {
    return
  }

  if (selectionMode.value === mode) {
    return
  }

  selectionMode.value = mode

  if (mode === 'temporary') {
    emit('select-temporary-tables', selectedTemporaryTableIds.value)
    return
  }

  if (selectedTemporaryTableIds.value.length) {
    emit('select-temporary-tables', [])
  }
}

function toggleTemporaryTable(tableId: string): void {
  const selected = new Set(selectedTemporaryTableIds.value)

  if (selected.has(tableId)) {
    selected.delete(tableId)
  } else {
    selected.add(tableId)
  }

  emit('select-temporary-tables', Array.from(selected))
}

function canChooseResource(resource: TableResourceItem): boolean {
  return (
    resource.selectable &&
    (selectionMode.value === 'single' || resource.resourceType === 'dining_table')
  )
}

function selected(resource: TableResourceItem): boolean {
  if (selectionMode.value === 'temporary') {
    return resource.resourceType === 'dining_table' && selectedTemporaryTableIdSet.value.has(resource.resourceId)
  }

  return resource.resourceType === 'dining_table'
    ? props.selectedTableId === resource.resourceId
    : props.selectedTableGroupId === resource.resourceId
}

function capacityText(resource: TableResourceItem): string {
  return `${resource.capacityMin}-${resource.capacityMax}人`
}

function statusText(resource: TableResourceItem): string {
  return resource.selectable ? statusLabel(resource.status) : `${statusLabel(resource.status)}，当前不可选`
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function areaTitle(resource: TableResourceItem): string {
  const areaName = resource.areaName?.trim()
  return areaName || '未分区'
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
</script>

<template>
  <section class="table-picker" aria-label="桌号及分组选取">
    <header class="table-picker__header">
      <div>
        <p>桌号及分组</p>
        <strong>{{ pickerSubtitle }}</strong>
      </div>
      <div class="table-picker__header-actions">
        <div
          v-if="temporarySelectionEnabled"
          class="table-picker__mode"
          aria-label="资源选择模式"
          role="group"
        >
          <button
            type="button"
            :class="{ selected: selectionMode === 'single' }"
            :aria-pressed="selectionMode === 'single'"
            @click="switchSelectionMode('single')"
          >
            单桌/桌组
          </button>
          <button
            type="button"
            :class="{ selected: selectionMode === 'temporary' }"
            :aria-pressed="selectionMode === 'temporary'"
            @click="switchSelectionMode('temporary')"
          >
            临时组合
          </button>
        </div>
        <button class="table-picker__refresh" type="button" :disabled="isLoading" @click="loadResources">刷新</button>
      </div>
    </header>

    <section v-if="isLoading" class="table-picker__state" aria-live="polite">
      正在读取桌台资源
    </section>

    <section v-if="apiError" class="table-picker__state table-picker__state--error" aria-live="assertive">
      {{ apiError.error.messageKey }}
    </section>

    <section v-if="showEmpty" class="table-picker__state" aria-live="polite">
      {{ emptyText }}
    </section>

    <section
      v-for="group in groupedTableResources"
      :key="group.title"
      class="table-picker__section"
      :aria-label="`${group.title}桌号`"
    >
      <p class="table-picker__section-title">{{ group.title }}</p>
      <div class="table-picker__grid">
        <button
          v-for="resource in group.items"
          :key="resource.resourceId"
          class="table-picker__resource"
          :class="{
            selected: selected(resource),
            unavailable: !canChooseResource(resource),
            'table-picker__resource--temporary': selectionMode === 'temporary'
          }"
          type="button"
          :disabled="!canChooseResource(resource)"
          @click="chooseResource(resource)"
        >
          <span>{{ resource.displayName || resource.code }}</span>
          <strong>{{ capacityText(resource) }}</strong>
          <small>{{ statusText(resource) }}</small>
        </button>
      </div>
    </section>

    <section v-if="showGroupResources" class="table-picker__section" aria-label="桌组">
      <p class="table-picker__section-title">分组</p>
      <div class="table-picker__grid">
        <button
          v-for="resource in groupResources"
          :key="resource.resourceId"
          class="table-picker__resource table-picker__resource--group"
          :class="{ selected: selected(resource), unavailable: !resource.selectable }"
          type="button"
          :disabled="!resource.selectable"
          @click="chooseResource(resource)"
        >
          <span>{{ resource.displayName || resource.code }}</span>
          <strong>{{ capacityText(resource) }}</strong>
          <small>{{ resource.memberTableCodes.join(' + ') || statusText(resource) }}</small>
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.table-picker {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.table-picker__header {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.table-picker__header p,
.table-picker__section-title {
  color: #14213d;
  font-size: 0.92rem;
  font-weight: 900;
  margin: 0;
}

.table-picker__header strong {
  color: #64748b;
  display: block;
  font-size: 0.78rem;
  margin-top: 3px;
}

.table-picker__header-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: end;
}

.table-picker__mode {
  background: #f8fafc;
  border: 1px solid #d8e0eb;
  border-radius: 999px;
  display: inline-flex;
  gap: 2px;
  padding: 2px;
}

.table-picker__mode button,
.table-picker__refresh {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #f97316;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 12px;
}

.table-picker__mode button {
  background: transparent;
  border-color: transparent;
  color: #64748b;
  min-height: 30px;
  padding: 0 10px;
}

.table-picker__mode button.selected {
  background: #ffffff;
  border-color: #fed7aa;
  color: #c2410c;
}

.table-picker__refresh:disabled {
  color: #94a3b8;
}

.table-picker__state {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #64748b;
  font-size: 0.86rem;
  font-weight: 800;
  padding: 12px;
}

.table-picker__state--error {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.table-picker__section {
  display: grid;
  gap: 8px;
}

.table-picker__grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.table-picker__resource {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 4px;
  justify-items: start;
  min-height: 86px;
  min-width: 0;
  padding: 10px;
  text-align: left;
}

.table-picker__resource span {
  font-size: 1rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.table-picker__resource strong,
.table-picker__resource small {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
}

.table-picker__resource.selected {
  background: #fff7ed;
  border-color: #f97316;
  box-shadow: inset 0 0 0 1px #f97316;
}

.table-picker__resource--group.selected {
  background: #ecfdf5;
  border-color: #10b981;
  box-shadow: inset 0 0 0 1px #10b981;
}

.table-picker__resource--temporary.selected {
  background: #eff6ff;
  border-color: #2563eb;
  box-shadow: inset 0 0 0 1px #2563eb;
}

.table-picker__resource.unavailable {
  background: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
}

.table-picker__resource.unavailable strong,
.table-picker__resource.unavailable small {
  color: #94a3b8;
}

.table-picker button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (min-width: 560px) {
  .table-picker__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
