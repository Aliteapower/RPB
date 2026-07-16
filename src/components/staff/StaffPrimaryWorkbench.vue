<script setup lang="ts">
import StaffBottomNav from './StaffBottomNav.vue'
import type { StaffBottomNavTab } from './staffBottomNavItems'

defineProps<{
  storeId: string
  activeTab: StaffBottomNavTab
}>()
</script>

<template>
  <main class="staff-workbench-shell staff-primary-workbench">
    <div class="staff-primary-workbench__surface">
      <slot />
    </div>
    <StaffBottomNav
      :store-id="storeId"
      :active-tab="activeTab"
      display-mode="adaptive-primary"
    />
  </main>
</template>

<style scoped>
.staff-primary-workbench__surface {
  min-width: 0;
  overflow-x: clip;
}

@media (min-width: 768px) {
  .staff-workbench-shell.staff-primary-workbench {
    display: grid;
    grid-template-columns: 88px minmax(0, 1fr);
    max-width: 1200px;
  }

  .staff-primary-workbench__surface {
    grid-column: 2;
    grid-row: 1;
    min-height: 100dvh;
  }

  .staff-primary-workbench__surface :deep(.staff-topbar) {
    margin-left: 0;
    margin-right: 0;
    padding-right: 116px;
  }
}
</style>
