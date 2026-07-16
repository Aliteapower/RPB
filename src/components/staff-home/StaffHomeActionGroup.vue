<script setup lang="ts">
import type { StaffHomeActionItem } from './staffHomeActions'

defineProps<{
  actions: StaffHomeActionItem[]
  groupId: string
  heading: string
  layout?: 'two' | 'three'
}>()
</script>

<template>
  <section v-if="actions.length" class="action-section" :aria-labelledby="groupId">
    <h2 :id="groupId" class="section-title">{{ heading }}</h2>

    <div
      class="action-grid"
      :class="{
        'single-action-grid': actions.length === 1,
        'three-action-grid': layout === 'three'
      }"
    >
      <RouterLink
        v-for="action in actions"
        :key="action.id"
        class="operation-link"
        :class="[
          `tone-${action.tone}`,
          { 'high-frequency-action': action.emphasis }
        ]"
        :to="action.to"
      >
        <span class="action-symbol" aria-hidden="true">{{ action.symbol }}</span>
        <span class="action-label">{{ action.label }}</span>
        <span v-if="action.description" class="action-description">{{ action.description }}</span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.action-section {
  display: grid;
  gap: 10px;
}

.section-title {
  align-items: center;
  color: #0f172a;
  display: flex;
  font-size: 1rem;
  font-weight: 900;
  gap: 8px;
  letter-spacing: 0;
  margin: 0;
}

.section-title::before {
  background: #f97316;
  border-radius: 999px;
  content: "";
  display: inline-block;
  height: 8px;
  width: 8px;
}

.action-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.single-action-grid {
  grid-template-columns: 1fr;
}

.three-action-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.operation-link {
  align-items: center;
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
  color: #0f172a;
  display: grid;
  gap: 7px;
  justify-items: center;
  min-height: 86px;
  padding: 12px 8px;
  text-align: center;
  text-decoration: none;
}

.action-symbol {
  align-items: center;
  background: #f8fafc;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  font-size: 0.9rem;
  font-weight: 900;
  height: 34px;
  justify-content: center;
  width: 34px;
}

.action-label {
  font-size: 0.88rem;
  font-weight: 900;
  line-height: 1.2;
}

.action-description {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 800;
  line-height: 1.25;
}

.high-frequency-action {
  background: #fff7ed;
  border-color: #fdba74;
}

.tone-primary .action-symbol {
  background: #fed7aa;
  color: #c2410c;
}

.tone-reservation .action-symbol {
  background: #dbeafe;
  color: #2563eb;
}

.tone-queue .action-symbol {
  background: #ffedd5;
  color: #c2410c;
}

.tone-success .action-symbol {
  background: #d1fae5;
  color: #047857;
}

.tone-support .action-symbol {
  background: #f1f5f9;
  color: #475569;
}

.operation-link:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
