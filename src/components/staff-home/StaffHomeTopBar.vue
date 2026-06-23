<script setup lang="ts">
defineProps<{
  appStatusLabel: string
  currentTimeText: string
  storeLabel: string
}>()
</script>

<template>
  <header class="staff-topbar" aria-label="员工工作台顶部栏">
    <div class="brand-block">
      <span class="brand-mark" aria-hidden="true">食</span>
      <div>
        <p class="brand-kicker">门店员工</p>
        <h1>食刻 · 管理</h1>
      </div>
    </div>

    <div class="topbar-meta" aria-label="当前门店和应用状态">
      <span class="time-pill">{{ currentTimeText }}</span>
      <span class="store-pill">{{ storeLabel }}</span>
      <span class="app-pill">{{ appStatusLabel }}</span>
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
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  min-width: 0;
}

.time-pill,
.store-pill,
.app-pill {
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 800;
  line-height: 1;
  max-width: 120px;
  overflow: hidden;
  padding: 7px 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-pill {
  background: #e2e8f0;
  color: #64748b;
}

.store-pill {
  background: #eef2ff;
  color: #4338ca;
}

.app-pill {
  background: #ffedd5;
  color: #c2410c;
}

@media (max-width: 420px) {
  .store-pill {
    max-width: 86px;
  }

  .app-pill {
    max-width: 72px;
  }
}
</style>
