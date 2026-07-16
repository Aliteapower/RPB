<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section class="workflow-strip" :aria-label="t('staffControls.workflow.aria')">
    <span>{{ t('staffControls.workflow.walkInSeating') }}</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>{{ t('staffControls.workflow.occupied') }}</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>{{ t('staffControls.workflow.cleaning') }}</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>{{ t('staffControls.workflow.available') }}</span>
  </section>
</template>

<style scoped>
.workflow-strip {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  color: #9a3412;
  display: flex;
  flex-wrap: wrap;
  font-size: 0.82rem;
  font-weight: 900;
  gap: 7px;
  justify-content: center;
  padding: 10px 12px;
}

.workflow-arrow {
  color: #94a3b8;
  font-weight: 800;
}
</style>
