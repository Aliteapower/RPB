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
      <div class="query-buttons">
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
.query-buttons,
.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.query-box {
  flex: 1 1 360px;
}

.query-buttons {
  flex: 0 0 auto;
}

.toolbar-actions {
  justify-content: flex-end;
}

input {
  width: 220px;
  max-width: 100%;
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
  .erp-query-toolbar {
    align-items: stretch;
    gap: 8px;
  }

  .query-box,
  .toolbar-actions,
  input {
    width: 100%;
  }

  .query-box {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .query-buttons {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }

  .secondary-button {
    min-width: 0;
    width: auto;
  }

  .toolbar-actions {
    justify-content: stretch;
  }

  .toolbar-actions > .secondary-button {
    flex: 1 1 108px;
  }
}
</style>
