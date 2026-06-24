<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  CleaningApiError,
  completeCleaning,
  startCleaning
} from '../api/cleaningApi'
import {
  fetchTableResources,
  TableResourceApiError
} from '../api/tableResourceApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  TableResourceApiErrorResponse,
  TableResourceItem,
  TableResourceListResponse
} from '../types/tableResource'
import type { CleaningApiErrorResponse } from '../types/cleaning'

type StatusFilter = 'all' | 'available' | 'occupied' | 'cleaning' | 'active'
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

const route = useRoute()
const storeContext = useStoreContextStore()

const statusOptions: Array<{ value: StatusFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'available', label: '可用' },
  { value: 'occupied', label: '占用' },
  { value: 'cleaning', label: '清台' },
  { value: 'active', label: '分组' }
]

const statusLabels: Record<string, string> = {
  available: '可用',
  occupied: '占用',
  cleaning: '清台',
  locked: '锁定',
  reserved: '预留',
  inactive: '停用',
  active: '分组',
  created: '已创建',
  released: '已释放',
  ended: '已结束'
}

const partySizeOptions = [2, 4, 6, 8, 10, 12]

const selectedStatus = ref<StatusFilter>('all')
const selectedArea = ref<AreaFilter>('all')
const partySize = ref<number | null>(null)
const isLoading = ref(false)
const startingCleaningResourceId = ref<string | null>(null)
const completingCleaningResourceId = ref<string | null>(null)
const response = ref<TableResourceListResponse | null>(null)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
const actionError = ref<CleaningApiErrorResponse | null>(null)
let loadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const resources = computed(() => response.value?.resources ?? [])
const allTableResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'dining_table')
)
const groupResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'table_group')
)
const statusFilteredTableResources = computed(() => {
  if (selectedStatus.value === 'active') {
    return []
  }

  if (selectedStatus.value === 'all') {
    return allTableResources.value
  }

  return allTableResources.value.filter(resource => resource.status === selectedStatus.value)
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
    { value: 'all', label: '全部分区', count: statusFilteredTableResources.value.length },
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
const partySizeValue = computed({
  get: () => (partySize.value === null ? '' : String(partySize.value)),
  set: value => {
    partySize.value = value ? Number(value) : null
  }
})
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
  [storeId, partySize],
  () => {
    void loadResources()
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
      includeGroups: true
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

function selectStatus(status: StatusFilter): void {
  selectedStatus.value = status
}

function selectArea(area: AreaFilter): void {
  selectedArea.value = area
}

async function startResourceCleaning(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !resource.currentSeatingId || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('SEATING_NOT_FOUND', 'cleaning.seating_not_found')
    return
  }

  actionError.value = null
  startingCleaningResourceId.value = resource.resourceId

  try {
    await startCleaning(
      currentStoreId,
      resource.currentSeatingId,
      {
        reasonCode: 'staff_table_page_clear',
        note: `table_resource:${resource.resourceType}:${resource.code}`
      },
      createTableActionIdempotencyKey('cleaning-start', resource.resourceId)
    )
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof CleaningApiError
        ? error.response
        : createCleaningLocalError('REQUEST_FAILED', 'cleaning.request_failed')
  } finally {
    startingCleaningResourceId.value = null
  }
}

async function completeResourceCleaning(resource: TableResourceItem): Promise<void> {
  const currentStoreId = storeId.value

  if (!currentStoreId || !resource.currentCleaningId || activeTableActionInProgress.value) {
    actionError.value = createCleaningLocalError('CLEANING_NOT_FOUND', 'cleaning.not_found')
    return
  }

  actionError.value = null
  completingCleaningResourceId.value = resource.resourceId

  try {
    await completeCleaning(
      currentStoreId,
      resource.currentCleaningId,
      {
        reasonCode: 'staff_table_page_complete_clear',
        note: `table_resource:${resource.resourceType}:${resource.code}`
      },
      createTableActionIdempotencyKey('cleaning-complete', resource.resourceId)
    )
    await loadResources()
  } catch (error) {
    actionError.value =
      error instanceof CleaningApiError
        ? error.response
        : createCleaningLocalError('REQUEST_FAILED', 'cleaning.request_failed')
  } finally {
    completingCleaningResourceId.value = null
  }
}

function statusFilterCount(status: StatusFilter): number {
  if (status === 'all') {
    return allTableResources.value.length
  }

  if (status === 'active') {
    return groupResources.value.length
  }

  return allTableResources.value.filter(resource => resource.status === status).length
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function statusClass(status: string): string {
  return `status-${status.replace(/_/g, '-')}`
}

function capacityText(resource: TableResourceItem): string {
  return `${resource.capacityMin}-${resource.capacityMax}人`
}

function membersText(resource: TableResourceItem): string {
  return resource.memberTableCodes.length ? resource.memberTableCodes.join(' + ') : '暂无成员'
}

function canStartCleaning(resource: TableResourceItem): boolean {
  return resource.status === 'occupied' && !!resource.currentSeatingId
}

const activeTableActionInProgress = computed(
  () => !!startingCleaningResourceId.value || !!completingCleaningResourceId.value
)

function isStartingCleaning(resource: TableResourceItem): boolean {
  return startingCleaningResourceId.value === resource.resourceId
}

function canCompleteCleaning(resource: TableResourceItem): boolean {
  return !!resource.currentCleaningId
}

function isCompletingCleaning(resource: TableResourceItem): boolean {
  return completingCleaningResourceId.value === resource.resourceId
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

function selectionReasonText(resource: TableResourceItem): string {
  if (resource.selectable) {
    return '可入桌'
  }

  const reasonLabels: Record<string, string> = {
    status_unavailable: '当前状态不可选',
    capacity_mismatch: '人数不匹配',
    locked: '桌台已锁定',
    occupied: '桌台已占用',
    cleaning: '正在清台'
  }
  const reason = resource.selectionDisabledReason?.trim()
  return reason ? reasonLabels[reason] ?? '当前不可选' : '当前不可选'
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

function createTableActionIdempotencyKey(action: string, resourceId: string): string {
  const randomValue =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return `table:${action}:${resourceId}:${randomValue}`
}
</script>

<template>
  <main class="staff-workbench-shell staff-workbench-shell--padded table-page">
    <section class="top-bar">
      <div>
        <p>食刻 · 管理</p>
        <h1>桌台</h1>
      </div>
      <button type="button" :disabled="isLoading" @click="loadResources">刷新</button>
    </section>

    <section class="flow-strip" aria-label="桌台资源来源">
      <span>后台开台</span>
      <span>桌号</span>
      <span>桌组</span>
      <span>员工选择</span>
    </section>

    <section class="summary-row" aria-label="桌台概览">
      <div
        v-for="item in summaryItems"
        :key="item.key"
        class="summary-row__item"
        :class="`summary-row__item--${item.key}`"
      >
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </div>
    </section>

    <section class="filter-panel" aria-label="桌台筛选">
      <div class="status-options">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          :aria-pressed="selectedStatus === option.value"
          :class="{ selected: selectedStatus === option.value }"
          type="button"
          @click="selectStatus(option.value)"
        >
          <span>{{ option.label }}</span>
          <strong>{{ statusFilterCount(option.value) }}</strong>
        </button>
      </div>

      <label class="party-size-field">
        <span>人数</span>
        <select v-model="partySizeValue" name="partySize">
          <option value="">不限</option>
          <option v-for="option in partySizeOptions" :key="option" :value="String(option)">
            {{ option }} 人
          </option>
        </select>
      </label>

      <p class="filter-panel__total">共 {{ displayedResourceCount }} 个</p>
    </section>

    <section v-if="isLoading" class="state-panel" aria-live="polite">
      正在读取后台已配置桌台
    </section>

    <section v-if="apiError" class="state-panel error-panel" aria-live="assertive">
      <strong>{{ apiError.error.code }}</strong>
      <span>{{ apiError.error.messageKey }}</span>
    </section>

    <section v-if="actionError" class="state-panel error-panel" aria-live="assertive">
      <strong>{{ actionError.error.code }}</strong>
      <span>{{ actionError.error.messageKey }}</span>
    </section>

    <section v-if="showNoConfiguredResources" class="state-panel" aria-live="polite">
      暂无桌台，请先在后台配置桌台。
    </section>

    <section v-if="showFilteredEmpty" class="state-panel" aria-live="polite">
      当前筛选下暂无可显示桌台。
    </section>

    <section v-if="groupedAreaResources.length" class="table-page__area-list" aria-label="桌台分区">
      <header class="table-page__section-heading">
        <h2>桌台分区</h2>
        <span>{{ tableResources.length }} 个</span>
      </header>

      <div class="table-page__area-filter" aria-label="分区过滤">
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
        :aria-label="`${area.title}桌号`"
      >
        <header>
          <h3>{{ area.title }}</h3>
          <span>{{ area.items.length }} 个</span>
        </header>

        <div class="table-page__resource-grid">
          <article
            v-for="resource in area.items"
            :key="resource.resourceId"
            class="table-page__resource-card"
            :class="statusClass(resource.status)"
          >
            <div class="table-page__resource-title">
              <strong>{{ resource.displayName || resource.code }}</strong>
              <span class="table-page__resource-badge">{{ statusLabel(resource.status) }}</span>
            </div>
            <p class="table-page__resource-meta">{{ capacityText(resource) }}</p>
            <p class="table-page__resource-note">
              状态：{{ statusLabel(resource.status) }}，{{ selectionReasonText(resource) }}
            </p>
            <div class="table-page__resource-actions" aria-label="桌台操作">
              <RouterLink
                v-if="resource.selectable"
                class="table-page__resource-action table-page__resource-action--primary"
                :to="walkInDirectSeatingRoute(resource)"
              >
                入桌
              </RouterLink>
              <button
                v-if="resource.status === 'occupied'"
                class="table-page__resource-action"
                disabled
                title="换桌需后端换桌契约"
                type="button"
              >
                换桌
              </button>
              <button
                v-if="resource.status === 'occupied'"
                class="table-page__resource-action table-page__resource-action--danger"
                :disabled="!canStartCleaning(resource) || isStartingCleaning(resource)"
                type="button"
                @click="startResourceCleaning(resource)"
              >
                {{ isStartingCleaning(resource) ? '清桌中' : '清桌' }}
              </button>
              <button
                v-if="resource.status === 'cleaning'"
                class="table-page__resource-action table-page__resource-action--primary"
                :disabled="!canCompleteCleaning(resource) || isCompletingCleaning(resource)"
                type="button"
                @click="completeResourceCleaning(resource)"
              >
                {{ isCompletingCleaning(resource) ? '完成中' : '完成清台' }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </section>

    <section
      v-if="displayedGroupResources.length"
      class="table-page__group-section"
      aria-label="桌台分组"
    >
      <header class="table-page__section-heading">
        <h2>桌台分组</h2>
        <span>{{ displayedGroupResources.length }} 个</span>
      </header>

      <div class="table-page__resource-grid table-page__resource-grid--groups">
        <article
          v-for="resource in displayedGroupResources"
          :key="resource.resourceId"
          class="table-page__resource-card table-page__resource-card--group"
          :class="statusClass(resource.status)"
        >
          <div class="table-page__resource-title">
            <strong>{{ resource.displayName || resource.code }}</strong>
            <span class="table-page__resource-badge">{{ statusLabel(resource.status) }}</span>
          </div>
          <p class="table-page__resource-meta">{{ capacityText(resource) }}</p>
          <p class="table-page__resource-members">{{ membersText(resource) }}</p>
          <div class="table-page__resource-actions" aria-label="桌台分组操作">
            <RouterLink
              v-if="resource.selectable"
              class="table-page__resource-action table-page__resource-action--primary"
              :to="walkInDirectSeatingRoute(resource)"
            >
              入桌
            </RouterLink>
            <button
              v-if="resource.status === 'occupied'"
              class="table-page__resource-action"
              disabled
              title="换桌需后端换桌契约"
              type="button"
            >
              换桌
            </button>
            <button
              v-if="resource.status === 'occupied'"
              class="table-page__resource-action table-page__resource-action--danger"
              :disabled="!canStartCleaning(resource) || isStartingCleaning(resource)"
              type="button"
              @click="startResourceCleaning(resource)"
            >
              {{ isStartingCleaning(resource) ? '清桌中' : '清桌' }}
            </button>
            <button
              v-if="resource.status === 'cleaning'"
              class="table-page__resource-action table-page__resource-action--primary"
              :disabled="!canCompleteCleaning(resource) || isCompletingCleaning(resource)"
              type="button"
              @click="completeResourceCleaning(resource)"
            >
              {{ isCompletingCleaning(resource) ? '完成中' : '完成清台' }}
            </button>
          </div>
        </article>
      </div>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="table" />
  </main>
</template>

<style scoped>
.top-bar {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.top-bar p,
.party-size-field span,
.filter-panel__total,
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

.top-bar button,
.status-options button,
.table-page__area-filter button {
  border: 1px solid #fed7aa;
  border-radius: 999px;
  font-weight: 900;
  min-height: 38px;
  padding: 0 13px;
}

.top-bar button {
  background: #f97316;
  color: #ffffff;
}

.top-bar button:disabled {
  background: #cbd5e1;
  border-color: #cbd5e1;
}

.flow-strip {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fdba74;
  border-radius: 8px;
  color: #c2410c;
  display: flex;
  font-size: 0.78rem;
  font-weight: 900;
  gap: 8px;
  min-height: 42px;
  overflow-x: auto;
  padding: 0 10px;
}

.flow-strip span {
  flex: 0 0 auto;
}

.flow-strip span + span::before {
  color: #94a3b8;
  content: '>';
  margin-right: 8px;
}

.summary-row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.summary-row__item {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  min-height: 64px;
  padding: 10px 6px;
  place-items: center;
  text-align: center;
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

.summary-row__item--available strong {
  color: #059669;
}

.summary-row__item--occupied strong {
  color: #2563eb;
}

.summary-row__item--cleaning strong {
  color: #94a3b8;
}

.summary-row__item--active strong {
  color: #7c3aed;
}

.filter-panel,
.table-page__area-list,
.table-page__group-section {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.filter-panel {
  grid-template-columns: minmax(0, 1fr) auto;
}

.status-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-options button,
.table-page__area-filter button {
  align-items: center;
  background: #ffffff;
  color: #315f91;
  display: inline-flex;
  gap: 6px;
}

.status-options button strong,
.table-page__area-filter button strong {
  font-size: 0.78rem;
}

.status-options button.selected,
.table-page__area-filter button.selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.party-size-field {
  align-items: center;
  display: flex;
  gap: 8px;
  grid-column: 1 / -1;
}

.party-size-field select {
  appearance: none;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  flex: 0 1 140px;
  font-weight: 850;
  min-height: 38px;
  padding: 0 30px 0 12px;
}

.filter-panel__total {
  align-self: center;
  justify-self: end;
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

.table-page__area-section {
  display: grid;
  gap: 10px;
}

.table-page__area-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-page__area-filter button {
  min-height: 34px;
  padding: 0 12px;
}

.table-page__resource-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
.table-page__resource-note,
.table-page__resource-members {
  color: #315f91;
  font-size: 0.78rem;
  font-weight: 850;
  margin: 0;
}

.table-page__resource-note {
  color: #64748b;
}

.table-page__resource-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
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
  }
}
</style>
