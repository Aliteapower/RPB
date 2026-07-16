<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { PlatformProductLine, ProductLineStatus } from '../../../types/platformProductLineBilling'

const props = defineProps<{
  productLines: PlatformProductLine[]
  loading: boolean
  selectedAppKey: string
  keyword: string
  status: ProductLineStatus | ''
  total: number
  page: number
  size: number
}>()

const emit = defineEmits<{
  create: []
  edit: [productLine: PlatformProductLine]
  page: [page: number]
  reset: []
  search: [query: { keyword: string; status: ProductLineStatus | '' }]
}>()

const localKeyword = ref(props.keyword)
const localStatus = ref<ProductLineStatus | ''>(props.status)

watch(() => props.keyword, value => {
  localKeyword.value = value
})

watch(() => props.status, value => {
  localStatus.value = value
})

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const rangeStart = computed(() => (props.total === 0 ? 0 : props.page * props.size + 1))
const rangeEnd = computed(() => Math.min(props.total, (props.page + 1) * props.size))

function submitSearch(): void {
  emit('search', {
    keyword: localKeyword.value.trim(),
    status: localStatus.value
  })
}
</script>

<template>
  <section class="product-line-list-module">
    <header class="list-toolbar">
      <div class="filter-group">
        <input
          v-model.trim="localKeyword"
          :aria-label="$t('platform.productLines.list.keywordAria')"
          :placeholder="$t('platform.productLines.list.keywordPlaceholder')"
          @keyup.enter="submitSearch"
        >
        <select v-model="localStatus" :aria-label="$t('platform.productLines.list.statusAria')">
          <option value="">{{ $t('platform.productLines.list.allStatuses') }}</option>
          <option value="active">{{ $t('platform.productLines.status.active') }}</option>
          <option value="disabled">{{ $t('platform.productLines.status.disabled') }}</option>
        </select>
        <button class="secondary-button" type="button" :disabled="loading" @click="submitSearch">
          {{ $t('common.actions.query') }}
        </button>
        <button class="secondary-button" type="button" :disabled="loading" @click="emit('reset')">
          {{ $t('common.actions.reset') }}
        </button>
      </div>
      <button class="primary-button" type="button" @click="emit('create')">
        {{ $t('platform.productLines.list.create') }}
      </button>
    </header>

    <div class="table-panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>{{ $t('platform.productLines.list.columns.productLine') }}</th>
            <th>App Key</th>
            <th>{{ $t('platform.productLines.list.columns.status') }}</th>
            <th>{{ $t('platform.productLines.list.columns.defaultEntry') }}</th>
            <th>{{ $t('platform.productLines.list.columns.sortOrder') }}</th>
            <th>{{ $t('platform.productLines.list.columns.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="table-empty">{{ $t('common.actions.loading') }}</td>
          </tr>
          <tr v-else-if="productLines.length === 0">
            <td colspan="6" class="table-empty">{{ $t('platform.productLines.list.empty') }}</td>
          </tr>
          <tr
            v-for="productLine in productLines"
            v-else
            :key="productLine.appKey"
            :class="{ selected: productLine.appKey === selectedAppKey }"
          >
            <td>{{ productLine.displayName }}</td>
            <td>{{ productLine.appKey }}</td>
            <td>{{ $t(`platform.productLines.status.${productLine.status}`) }}</td>
            <td>{{ productLine.defaultEntryRoute || $t('platform.productLines.list.unconfigured') }}</td>
            <td>{{ productLine.sortOrder }}</td>
            <td>
              <button class="text-action" type="button" @click="emit('edit', productLine)">
                {{ $t('platform.productLines.list.edit') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="pagination-bar">
      <span>{{ $t('common.pagination.summary', { start: rangeStart, end: rangeEnd, total }) }}</span>
      <div class="pager-actions">
        <button class="secondary-button" type="button" :disabled="loading || page <= 0" @click="emit('page', page - 1)">
          {{ $t('common.actions.previousPage') }}
        </button>
        <strong>{{ page + 1 }} / {{ pageCount }}</strong>
        <button
          class="secondary-button"
          type="button"
          :disabled="loading || page + 1 >= pageCount"
          @click="emit('page', page + 1)"
        >
          {{ $t('common.actions.nextPage') }}
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.product-line-list-module {
  display: grid;
  gap: 12px;
}

.list-toolbar {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.filter-group {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-group input,
.filter-group select {
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 7px 10px;
  font: inherit;
}

.filter-group input {
  width: min(320px, 64vw);
}

.table-panel {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  overflow-x: auto;
}

.data-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}

.data-table th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

.data-table tr.selected {
  background: #ecfdf5;
}

.table-empty {
  color: #64748b;
  text-align: center;
}

.pagination-bar {
  align-items: center;
  color: #64748b;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.pager-actions {
  align-items: center;
  display: flex;
  gap: 10px;
}

.primary-button,
.secondary-button,
.text-action {
  min-height: 36px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  background: #ffffff;
}

.text-action {
  border: 0;
  color: #0f766e;
  background: transparent;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 720px) {
  .list-toolbar,
  .pagination-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
