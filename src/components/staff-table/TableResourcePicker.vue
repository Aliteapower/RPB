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
}>()

const emit = defineEmits<{
  'select-table': [tableId: string]
  'select-table-group': [tableGroupId: string]
}>()

const resources = ref<TableResourceItem[]>([])
const isLoading = ref(false)
const apiError = ref<TableResourceApiErrorResponse | null>(null)
let loadSequence = 0

const tableResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'dining_table')
)
const groupResources = computed(() =>
  resources.value.filter(resource => resource.resourceType === 'table_group')
)
const showEmpty = computed(
  () => !isLoading.value && !apiError.value && resources.value.length === 0
)

watch(
  () => [props.storeId, props.partySize] as const,
  () => {
    void loadResources()
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
      includeGroups: true
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
  if (!resource.selectable) {
    return
  }

  if (resource.resourceType === 'dining_table') {
    emit('select-table', resource.resourceId)
    return
  }

  emit('select-table-group', resource.resourceId)
}

function selected(resource: TableResourceItem): boolean {
  return resource.resourceType === 'dining_table'
    ? props.selectedTableId === resource.resourceId
    : props.selectedTableGroupId === resource.resourceId
}

function capacityText(resource: TableResourceItem): string {
  return `${resource.capacityMin}-${resource.capacityMax}人`
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
        <strong>选择后台已配置的可用资源</strong>
      </div>
      <button type="button" :disabled="isLoading" @click="loadResources">刷新</button>
    </header>

    <section v-if="isLoading" class="table-picker__state" aria-live="polite">
      正在读取桌台资源
    </section>

    <section v-if="apiError" class="table-picker__state table-picker__state--error" aria-live="assertive">
      {{ apiError.error.messageKey }}
    </section>

    <section v-if="showEmpty" class="table-picker__state" aria-live="polite">
      暂无桌台，请先在后台配置桌台。
    </section>

    <section v-if="tableResources.length" class="table-picker__section" aria-label="桌号">
      <p class="table-picker__section-title">桌号</p>
      <div class="table-picker__grid">
        <button
          v-for="resource in tableResources"
          :key="resource.resourceId"
          class="table-picker__resource"
          :class="{ selected: selected(resource), unavailable: !resource.selectable }"
          type="button"
          :disabled="!resource.selectable"
          @click="chooseResource(resource)"
        >
          <span>{{ resource.displayName || resource.code }}</span>
          <strong>{{ capacityText(resource) }}</strong>
          <small>{{ resource.status }}</small>
        </button>
      </div>
    </section>

    <section v-if="groupResources.length" class="table-picker__section" aria-label="桌组">
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
          <small>{{ resource.memberTableCodes.join(' + ') || resource.status }}</small>
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

.table-picker__header button {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #f97316;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 12px;
}

.table-picker__header button:disabled {
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
