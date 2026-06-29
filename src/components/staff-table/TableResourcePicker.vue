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

type SelectionMode = 'single' | 'temporary'
type AreaFilterOption = {
  value: string
  label: string
  count: number
}

const props = defineProps<{
  storeId: string
  partySize?: number | null
  selectedTableId?: string | null
  selectedTableGroupId?: string | null
  selectedTemporaryTableIds?: string[] | null
  businessDate?: string | null
  availableOnly?: boolean
  temporarySelectionEnabled?: boolean
  selectionMode?: SelectionMode | null
  showSelectionModeControls?: boolean
  requiredResourceType?: string | null
  requiredResourceId?: string | null
}>()

const emit = defineEmits<{
  'select-table': [tableId: string]
  'select-table-group': [tableGroupId: string]
  'select-temporary-tables': [tableIds: string[]]
  'update:selection-mode': [mode: SelectionMode]
}>()

const resources = ref<TableResourceItem[]>([])
const isLoading = ref(false)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
const selectionMode = ref<SelectionMode>('single')
const selectedArea = ref('all')
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

const displayableResources = computed(() =>
  resources.value.filter(resource => !isTemporaryGroupMember(resource))
)
const normalizedRequiredResourceType = computed(() => props.requiredResourceType?.trim() ?? '')
const normalizedRequiredResourceId = computed(() => props.requiredResourceId?.trim() ?? '')
const hasRequiredResource = computed(
  () => !!normalizedRequiredResourceType.value && !!normalizedRequiredResourceId.value
)
const displayedResources = computed(() =>
  props.availableOnly
    ? displayableResources.value.filter(resource => resource.selectable || matchesRequiredResource(resource))
    : displayableResources.value
)
const tableResources = computed(() =>
  displayedResources.value.filter(resource => resource.resourceType === 'dining_table')
)
const filteredTableResources = computed(() =>
  selectedArea.value === 'all'
    ? tableResources.value
    : tableResources.value.filter(resource => resourceAreaMatches(resource, selectedArea.value))
)
const tableAreaByCode = computed(() => {
  const areaMap = new Map<string, string>()

  for (const resource of displayableResources.value) {
    if (resource.resourceType !== 'dining_table') {
      continue
    }

    const area = areaTitle(resource)
    for (const code of tableIdentityKeys(resource)) {
      areaMap.set(code, area)
    }
  }

  return areaMap
})
const areaFilterOptions = computed<AreaFilterOption[]>(() => {
  const areaCounts = new Map<string, number>()

  for (const resource of tableResources.value) {
    const title = areaTitle(resource)
    areaCounts.set(title, (areaCounts.get(title) ?? 0) + 1)
  }

  return [
    { value: 'all', label: '全部分区', count: tableResources.value.length },
    ...Array.from(areaCounts, ([label, count]) => ({ value: label, label, count }))
  ]
})
const groupedTableResources = computed(() => {
  const groups = new Map<string, TableResourceItem[]>()

  for (const resource of filteredTableResources.value) {
    const title = areaTitle(resource)
    groups.set(title, [...(groups.get(title) ?? []), resource])
  }

  return Array.from(groups, ([title, items]) => ({ title, items }))
})
const groupResources = computed(() =>
  displayedResources.value.filter(resource => resource.resourceType === 'table_group')
)
const temporaryGroupResources = computed(() =>
  groupResources.value.filter(resource => resource.groupType === 'temporary')
)
const visibleGroupResources = computed(() =>
  (selectionMode.value === 'temporary' ? temporaryGroupResources.value : groupResources.value)
    .filter(resource => selectedArea.value === 'all' || filterGroupResourceByArea(resource, selectedArea.value))
)
const selectedTemporaryTableIds = computed(() => props.selectedTemporaryTableIds ?? [])
const selectedTemporaryTableIdSet = computed(() => new Set(selectedTemporaryTableIds.value))
const temporarySelectionCount = computed(() => selectedTemporaryTableIds.value.length)
const showGroupResources = computed(() => visibleGroupResources.value.length > 0)
const displayedResourceCount = computed(() => filteredTableResources.value.length + visibleGroupResources.value.length)
const showEmpty = computed(
  () => !isLoading.value && !apiError.value && displayedResourceCount.value === 0
)
const emptyText = computed(() => {
  if (selectedArea.value !== 'all') {
    return props.availableOnly ? '当前分区暂无可用桌台。' : '当前分区暂无桌台。'
  }

  return props.availableOnly ? '暂无可用桌台。' : '暂无桌台，请先在后台配置桌台。'
})
const pickerSubtitle = computed(() =>
  selectionMode.value === 'temporary'
    ? `临时组合 · 已选 ${temporarySelectionCount.value} 张`
    : '后台已配置资源'
)
const shouldShowSelectionModeControls = computed(
  () => props.temporarySelectionEnabled && props.showSelectionModeControls !== false
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
      setSelectionMode('single', { clearTemporarySelection: false, notifyParent: false })
      return
    }

    if (count > 0) {
      setSelectionMode('temporary', { clearTemporarySelection: false, notifyParent: false })
    }
  },
  { immediate: true }
)

watch(
  () => props.selectionMode,
  mode => {
    if (!mode) {
      return
    }

    setSelectionMode(mode, { clearTemporarySelection: false, notifyParent: false })
  },
  { immediate: true }
)

watch(
  areaFilterOptions,
  options => {
    if (!options.some(option => option.value === selectedArea.value)) {
      selectedArea.value = 'all'
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

  if (selectionMode.value === 'temporary' && resource.resourceType === 'dining_table') {
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
  setSelectionMode(mode, { clearTemporarySelection: mode === 'single', notifyParent: true })
}

function selectArea(area: string): void {
  selectedArea.value = area
}

function setSelectionMode(
  mode: SelectionMode,
  options: { clearTemporarySelection: boolean; notifyParent: boolean }
): void {
  if (mode === 'temporary' && !props.temporarySelectionEnabled) {
    return
  }

  if (selectionMode.value === mode) {
    return
  }

  selectionMode.value = mode

  if (options.notifyParent) {
    emit('update:selection-mode', mode)
  }

  if (mode === 'temporary') {
    emit('select-temporary-tables', selectedTemporaryTableIds.value)
    return
  }

  if (options.clearTemporarySelection && selectedTemporaryTableIds.value.length) {
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
  if (hasRequiredResource.value && !matchesRequiredResource(resource)) {
    return false
  }

  if (!resource.selectable && !canUseRequiredPreassignedResource(resource)) {
    return false
  }

  return (
    selectionMode.value === 'single' ||
    resource.resourceType === 'dining_table' ||
    isTemporaryModeSavedGroup(resource)
  )
}

function matchesRequiredResource(resource: TableResourceItem): boolean {
  return (
    hasRequiredResource.value &&
    resource.resourceType === normalizedRequiredResourceType.value &&
    resource.resourceId === normalizedRequiredResourceId.value
  )
}

function canUseRequiredPreassignedResource(resource: TableResourceItem): boolean {
  return (
    matchesRequiredResource(resource) &&
    resource.selectionDisabledReason?.trim() === 'reservation_preassigned'
  )
}

function selected(resource: TableResourceItem): boolean {
  if (selectionMode.value === 'temporary') {
    if (resource.resourceType === 'dining_table') {
      return selectedTemporaryTableIdSet.value.has(resource.resourceId)
    }

    return isTemporaryModeSavedGroup(resource) && props.selectedTableGroupId === resource.resourceId
  }

  return resource.resourceType === 'dining_table'
    ? props.selectedTableId === resource.resourceId
    : props.selectedTableGroupId === resource.resourceId
}

function isTemporaryModeSavedGroup(resource: TableResourceItem): boolean {
  return selectionMode.value === 'temporary' && resource.resourceType === 'table_group' && resource.groupType === 'temporary'
}

function capacityText(resource: TableResourceItem): string {
  return `${resource.capacityMin}-${resource.capacityMax}人`
}

function statusText(resource: TableResourceItem): string {
  if (matchesRequiredResource(resource)) {
    return resource.selectable || canUseRequiredPreassignedResource(resource)
      ? '预约指定'
      : resourceUnavailableReasonText(resource)
  }

  if (hasRequiredResource.value) {
    return '需使用预约指定桌台'
  }

  return resource.selectable ? statusLabel(resource.status) : resourceUnavailableReasonText(resource)
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function isTemporaryGroupMember(resource: TableResourceItem): boolean {
  return (
    resource.resourceType === 'dining_table' &&
    resource.selectionDisabledReason?.trim() === 'temporary_group_member'
  )
}

function filterGroupResourceByArea(resource: TableResourceItem, area: string): boolean {
  if (resource.areaName?.trim()) {
    return resourceAreaMatches(resource, area)
  }

  return resource.memberTableCodes.some(code => tableAreaByCode.value.get(code) === area)
}

function resourceAreaMatches(resource: TableResourceItem, area: string): boolean {
  return areaTitle(resource) === area
}

function tableIdentityKeys(resource: TableResourceItem): string[] {
  return [resource.code, resource.displayName]
    .map(value => value?.trim())
    .filter((value): value is string => !!value)
}

function resourceUnavailableReasonText(resource: TableResourceItem): string {
  if (isTemporaryGroupMember(resource)) {
    return '临时组占用'
  }

  const reasonLabels: Record<string, string> = {
    status_unavailable: '当前状态不可选',
    capacity_mismatch: '人数不匹配',
    locked: '桌台已锁定',
    occupied: '桌台已占用',
    cleaning: '正在清台',
    reservation_preassigned: '已被预约预留',
    temporary_group_member: '临时组占用'
  }
  const reason = resource.selectionDisabledReason?.trim()
  const reasonText = reason ? reasonLabels[reason] ?? '当前不可选' : '当前不可选'

  return `${statusLabel(resource.status)}，${reasonText}`
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
          v-if="shouldShowSelectionModeControls"
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

    <section v-if="areaFilterOptions.length > 1" class="table-picker__area-filter" aria-label="桌台分区">
      <span>桌台分区</span>
      <div>
        <button
          v-for="option in areaFilterOptions"
          :key="option.value"
          type="button"
          :class="{ selected: selectedArea === option.value }"
          :aria-pressed="selectedArea === option.value"
          @click="selectArea(option.value)"
        >
          {{ option.label }}
          <small>{{ option.count }}</small>
        </button>
      </div>
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
          v-for="resource in visibleGroupResources"
          :key="resource.resourceId"
          class="table-picker__resource table-picker__resource--group"
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
  border-radius: 8px;
  display: inline-flex;
  gap: 2px;
  padding: 2px;
}

.table-picker__mode button,
.table-picker__refresh {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  color: #f97316;
  font-size: 0.72rem;
  font-weight: 900;
  min-height: 30px;
  padding: 0 9px;
}

.table-picker__mode button {
  background: transparent;
  border-color: transparent;
  color: #64748b;
  min-height: 26px;
  padding: 0 8px;
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

.table-picker__area-filter {
  display: grid;
  gap: 8px;
}

.table-picker__area-filter > span {
  color: #14213d;
  font-size: 0.82rem;
  font-weight: 900;
}

.table-picker__area-filter > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-picker__area-filter button {
  align-items: center;
  background: #ffffff;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #1e3a5f;
  display: inline-flex;
  font-size: 0.82rem;
  font-weight: 900;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
}

.table-picker__area-filter button.selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.table-picker__area-filter small {
  font-size: 0.74rem;
  font-weight: 900;
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
