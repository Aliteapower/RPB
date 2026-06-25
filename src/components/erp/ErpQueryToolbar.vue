<script setup lang="ts">
defineProps<{
  keyword: string
  placeholder: string
  loading: boolean
  hasDirtyQuery: boolean
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  search: []
  reset: []
  refresh: []
}>()
</script>

<template>
  <div class="erp-query-toolbar">
    <slot name="filters" />

    <div class="query-box">
      <input
        type="search"
        :placeholder="placeholder"
        :value="keyword"
        @input="emit('update:keyword', ($event.target as HTMLInputElement).value)"
        @keydown.enter.prevent="emit('search')"
      />
      <button type="button" class="secondary-button" :disabled="loading" @click="emit('search')">
        查询
      </button>
      <button
        type="button"
        class="secondary-button"
        :disabled="loading || !hasDirtyQuery"
        @click="emit('reset')"
      >
        重置
      </button>
    </div>

    <div class="toolbar-actions">
      <button type="button" class="secondary-button" :disabled="loading" @click="emit('refresh')">
        刷新
      </button>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.erp-query-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.query-box,
.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

input {
  width: 220px;
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.secondary-button {
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 12px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 700px) {
  .erp-query-toolbar,
  .query-box,
  .toolbar-actions,
  input,
  .secondary-button {
    width: 100%;
  }
}
</style>
