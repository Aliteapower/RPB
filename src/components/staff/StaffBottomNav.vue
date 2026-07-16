<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import {
  staffBottomNavItems,
  type StaffBottomNavTab
} from './staffBottomNavItems'

const props = withDefaults(defineProps<{
  storeId: string
  activeTab: StaffBottomNavTab
  displayMode?: 'bottom' | 'adaptive-primary'
}>(), {
  displayMode: 'bottom'
})

const { t } = useI18n()
const items = computed(() =>
  staffBottomNavItems.map(item => ({
    ...item,
    to: {
      name: item.routeName,
      params: {
        storeId: props.storeId
      }
    }
  }))
)
</script>

<template>
  <nav
    class="staff-bottom-nav"
    :class="`staff-bottom-nav--${displayMode}`"
    :aria-label="t('nav.staff.aria')"
  >
    <RouterLink
      v-for="item in items"
      :key="item.tab"
      class="staff-bottom-nav__item"
      :class="{ active: activeTab === item.tab }"
      :to="item.to"
    >
      <span class="staff-bottom-nav__symbol" aria-hidden="true">{{ item.symbol }}</span>
      <span class="staff-bottom-nav__label">{{ t(item.labelKey) }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.staff-bottom-nav {
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid #dbe3ee;
  bottom: 0;
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.08);
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  left: 50%;
  max-width: 520px;
  padding: 7px 10px calc(7px + env(safe-area-inset-bottom));
  position: fixed;
  transform: translateX(-50%);
  width: 100%;
  z-index: 30;
}

.staff-bottom-nav__item {
  align-items: center;
  border-radius: 8px;
  color: #8aa0bb;
  display: grid;
  gap: 2px;
  justify-items: center;
  min-height: 50px;
  min-width: 0;
  text-decoration: none;
}

.staff-bottom-nav__item.active {
  color: #f97316;
}

.staff-bottom-nav__symbol {
  font-size: 1.35rem;
  font-weight: 900;
  line-height: 1;
}

.staff-bottom-nav__label {
  font-size: 0.72rem;
  font-weight: 900;
  line-height: 1;
}

.staff-bottom-nav__item:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (min-width: 720px) {
  .staff-bottom-nav {
    border-left: 1px solid #dbe3ee;
    border-right: 1px solid #dbe3ee;
  }
}

@media (min-width: 768px) {
  .staff-bottom-nav--adaptive-primary {
    align-content: center;
    align-self: start;
    border-bottom: 0;
    border-left: 0;
    border-right: 1px solid #dbe3ee;
    border-top: 0;
    bottom: auto;
    box-shadow: 8px 0 24px rgba(15, 23, 42, 0.07);
    grid-column: 1;
    grid-row: 1;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: repeat(4, minmax(72px, auto));
    height: 100dvh;
    left: auto;
    max-width: none;
    padding: 20px 10px;
    position: sticky;
    top: 0;
    transform: none;
    width: 88px;
  }

  .staff-bottom-nav--adaptive-primary .staff-bottom-nav__item {
    gap: 6px;
    min-height: 72px;
    padding: 8px 4px;
  }

  .staff-bottom-nav--adaptive-primary .staff-bottom-nav__label {
    font-size: 0.78rem;
  }
}
</style>
