<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  limit: number
  offset: number
  total: number
  loading: boolean
}>()

const emit = defineEmits<{
  change: [offset: number]
}>()

const currentPage = computed(() => Math.floor(props.offset / props.limit) + 1)
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.limit)))
const start = computed(() => (props.total === 0 ? 0 : props.offset + 1))
const end = computed(() => Math.min(props.offset + props.limit, props.total))
const canPrev = computed(() => props.offset > 0 && !props.loading)
const canNext = computed(() => props.offset + props.limit < props.total && !props.loading)

function prevPage(): void {
  if (canPrev.value) {
    emit('change', Math.max(0, props.offset - props.limit))
  }
}

function nextPage(): void {
  if (canNext.value) {
    emit('change', props.offset + props.limit)
  }
}
</script>

<template>
  <footer class="erp-pagination">
    <span>{{ $t('common.pagination.summary', { start, end, total }) }}</span>
    <div class="pager-actions">
      <button type="button" :disabled="!canPrev" @click="prevPage">
        {{ $t('common.actions.previousPage') }}
      </button>
      <strong>{{ currentPage }} / {{ totalPages }}</strong>
      <button type="button" :disabled="!canNext" @click="nextPage">
        {{ $t('common.actions.nextPage') }}
      </button>
    </div>
  </footer>
</template>

<style scoped>
.erp-pagination {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  color: #475569;
  font-size: 13px;
}

.pager-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

button {
  min-height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

button:disabled {
  opacity: 0.55;
  cursor: default;
}

strong {
  min-width: 58px;
  color: #0f172a;
  text-align: center;
}

@media (max-width: 640px) {
  .erp-pagination {
    display: grid;
  }

  .pager-actions {
    justify-content: space-between;
  }
}
</style>
