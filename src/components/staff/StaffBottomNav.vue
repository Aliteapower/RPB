<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import {
  staffBottomNavItems,
  type StaffBottomNavTab
} from './staffBottomNavItems'

const props = defineProps<{
  storeId: string
  activeTab: StaffBottomNavTab
}>()

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
  <nav class="staff-bottom-nav" aria-label="员工工作台导航">
    <RouterLink
      v-for="item in items"
      :key="item.tab"
      class="staff-bottom-nav__item"
      :class="{ active: activeTab === item.tab }"
      :to="item.to"
    >
      <span class="staff-bottom-nav__symbol" aria-hidden="true">{{ item.symbol }}</span>
      <span class="staff-bottom-nav__label">{{ item.label }}</span>
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
</style>
