<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import StoreSwitcher from '../store/StoreSwitcher.vue'

const props = defineProps<{
  appStatusLabel: string
  businessDate?: string | null
  currentTimeText: string
  storeLabel: string
}>()

const { t } = useI18n()
const displayAppStatus = computed(() => {
  const status = props.appStatusLabel.trim()
  return status === t('staffHome.appStatus.available') ? '' : status
})
</script>

<template>
  <header class="staff-topbar" :aria-label="t('staffHome.topbar.aria')">
    <div class="brand-block">
      <span class="brand-mark" aria-hidden="true">{{ t('staffHome.topbar.brandMark') }}</span>
      <div>
        <p class="brand-kicker">{{ t('staffHome.topbar.kicker') }}</p>
        <h1>{{ t('staffHome.topbar.title') }}</h1>
      </div>
    </div>

    <div class="topbar-meta" :aria-label="t('staffHome.topbar.metaAria')">
      <div class="topbar-row topbar-row--time">
        <span class="time-pill">{{ currentTimeText }}</span>
        <slot name="utility" />
      </div>
      <div class="topbar-row topbar-row--store">
        <StoreSwitcher :fallback-label="storeLabel" surface="staff" />
        <span v-if="displayAppStatus" class="app-pill">{{ displayAppStatus }}</span>
        <slot name="action" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.staff-topbar {
  align-items: center;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid #dbe3ee;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin: 0 -14px;
  min-height: 58px;
  padding: max(10px, env(safe-area-inset-top)) 14px 10px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand-block {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  align-items: center;
  background: #fff7ed;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 0.92rem;
  font-weight: 900;
  height: 30px;
  justify-content: center;
  width: 30px;
}

.brand-kicker,
h1 {
  margin: 0;
}

.brand-kicker {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 800;
}

h1 {
  color: #0f172a;
  font-size: 1.08rem;
  letter-spacing: 0;
  line-height: 1.18;
  white-space: nowrap;
}

.topbar-meta {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.topbar-row {
  align-items: center;
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  min-width: 0;
  width: 100%;
}

.time-pill,
.app-pill {
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 800;
  line-height: 1;
  max-width: 120px;
  overflow: hidden;
  padding: 6px 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-pill {
  background: #e2e8f0;
  color: #64748b;
}

.app-pill {
  background: #ffedd5;
  color: #c2410c;
}

:slotted(button) {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  font-size: 0.74rem;
  font-weight: 900;
  min-height: 28px;
  padding: 0 10px;
}

:slotted(button:disabled) {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #94a3b8;
}

@media (max-width: 420px) {
  .staff-topbar {
    gap: 8px;
  }

  .topbar-row {
    gap: 5px;
  }

  .app-pill {
    max-width: 72px;
  }
}
</style>
