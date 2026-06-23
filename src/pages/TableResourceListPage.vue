<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

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

type StatusFilter = 'all' | 'available' | 'occupied' | 'cleaning' | 'active'

const route = useRoute()
const storeContext = useStoreContextStore()

const statusOptions: Array<{ value: StatusFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'available', label: '可用' },
  { value: 'occupied', label: '占用' },
  { value: 'cleaning', label: '清台中' },
  { value: 'active', label: '分组' }
]

const statusLabels: Record<string, string> = {
  available: '可用',
  occupied: '占用',
  cleaning: '清台中',
  locked: '锁定',
  reserved: '预留',
  inactive: '停用',
  active: '可选分组',
  created: '已创建',
  released: '已释放',
  ended: '已结束'
}

const selectedStatus = ref<StatusFilter>('all')
const partySize = ref<number | null>(null)
const isLoading = ref(false)
const response = ref<TableResourceListResponse | null>(null)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
let loadSequence = 0

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const resources = computed(() => response.value?.resources ?? [])
const tableResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'dining_table')
)
const groupResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'table_group')
)
const availableCount = computed(() =>
  tableResources.value.filter(resource => resource.status === 'available').length
)
const occupiedCount = computed(() =>
  tableResources.value.filter(resource => resource.status === 'occupied').length
)
const cleaningCount = computed(() =>
  tableResources.value.filter(resource => resource.status === 'cleaning').length
)
const showEmpty = computed(
  () => !isLoading.value && !apiError.value && !!response.value && resources.value.length === 0
)

watch(
  [storeId, selectedStatus, partySize],
  () => {
    void loadResources()
  },
  { immediate: true }
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
      status: selectedStatus.value === 'all' ? undefined : selectedStatus.value,
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
      <div>
        <span>可用</span>
        <strong>{{ availableCount }}</strong>
      </div>
      <div>
        <span>占用</span>
        <strong>{{ occupiedCount }}</strong>
      </div>
      <div>
        <span>清台</span>
        <strong>{{ cleaningCount }}</strong>
      </div>
      <div>
        <span>分组</span>
        <strong>{{ groupResources.length }}</strong>
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
          {{ option.label }}
        </button>
      </div>

      <label class="party-size-field">
        <span>人数</span>
        <input
          v-model.number="partySize"
          min="1"
          inputmode="numeric"
          name="partySize"
          placeholder="不限"
          type="number"
        />
      </label>
    </section>

    <section v-if="isLoading" class="state-panel" aria-live="polite">
      正在读取后台已配置桌台
    </section>

    <section v-if="apiError" class="state-panel error-panel" aria-live="assertive">
      <strong>{{ apiError.error.code }}</strong>
      <span>{{ apiError.error.messageKey }}</span>
    </section>

    <section v-if="showEmpty" class="state-panel" aria-live="polite">
      暂无桌台，请先在后台配置桌台。
    </section>

    <section v-if="tableResources.length" class="resource-section" aria-label="桌号列表">
      <header>
        <h2>桌号</h2>
        <span>{{ tableResources.length }} 个</span>
      </header>

      <div class="resource-grid">
        <article
          v-for="resource in tableResources"
          :key="resource.resourceId"
          class="resource-tile"
          :class="statusClass(resource.status)"
        >
          <strong>{{ resource.displayName || resource.code }}</strong>
          <span>{{ capacityText(resource) }}</span>
          <small>{{ statusLabel(resource.status) }}</small>
        </article>
      </div>
    </section>

    <section v-if="groupResources.length" class="resource-section" aria-label="桌组列表">
      <header>
        <h2>分组</h2>
        <span>{{ groupResources.length }} 个</span>
      </header>

      <div class="resource-grid">
        <article
          v-for="resource in groupResources"
          :key="resource.resourceId"
          class="resource-tile group-tile"
          :class="statusClass(resource.status)"
        >
          <strong>{{ resource.displayName || resource.code }}</strong>
          <span>{{ capacityText(resource) }}</span>
          <small>{{ membersText(resource) }}</small>
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
.resource-section header span,
.party-size-field span {
  color: #64748b;
  font-size: 0.82rem;
  font-weight: 800;
  margin: 0;
}

h1,
h2 {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.35rem;
  line-height: 1.15;
}

h2 {
  font-size: 1rem;
}

.top-bar button,
.status-options button {
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-row div {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  min-height: 62px;
  padding: 10px;
}

.summary-row span {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 800;
}

.summary-row strong {
  color: #f97316;
  font-size: 1.3rem;
  line-height: 1;
}

.filter-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.status-options {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.status-options button {
  background: #ffffff;
  color: #315f91;
  flex: 0 0 auto;
}

.status-options button.selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.party-size-field {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: auto minmax(0, 1fr);
}

.party-size-field input {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  min-height: 42px;
  padding: 0 12px;
  width: 100%;
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

.resource-section {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.resource-section header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.resource-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.resource-tile {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  min-height: 96px;
  min-width: 0;
  padding: 10px;
}

.resource-tile strong {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.resource-tile span,
.resource-tile small {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 800;
}

.status-available,
.status-active {
  background: #ecfdf5;
  border-color: #10b981;
}

.status-occupied {
  background: #eff6ff;
  border-color: #60a5fa;
}

.status-cleaning {
  background: #fff7ed;
  border-color: #fb923c;
}

.status-locked,
.status-reserved {
  background: #fef3c7;
  border-color: #f59e0b;
}

.status-inactive,
.status-deleted,
.status-released,
.status-ended {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.group-tile {
  grid-column: span 2;
}

button:focus-visible,
a:focus-visible,
input:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

</style>
