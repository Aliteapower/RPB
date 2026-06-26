<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthSessionStore } from '../../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const loggingOut = ref(false)

const storeId = computed(() => String(route.params.storeId || auth.user?.defaultStoreId || auth.user?.storeIds[0] || ''))

async function logoutFromTenantAdmin(): Promise<void> {
  if (loggingOut.value) {
    return
  }

  loggingOut.value = true
  try {
    await auth.logoutCurrentUser()
    await router.push({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <aside class="tenant-nav" aria-label="租户后台导航">
    <div class="nav-main">
      <div class="brand-block">
        <span class="brand-mark">RPB</span>
        <strong>租户后台</strong>
        <small>门店 {{ auth.user?.username || '20000000' }}</small>
      </div>
      <nav class="nav-list">
        <RouterLink class="nav-item" :to="`/stores/${storeId}/admin/staff`">员工管理</RouterLink>
        <RouterLink class="nav-item" :to="`/stores/${storeId}/admin/tables`">桌号管理</RouterLink>
        <RouterLink class="nav-item" :to="`/stores/${storeId}/admin/settings`">基础设置</RouterLink>
        <RouterLink class="nav-item" :to="`/stores/${storeId}/admin/call-screen`">叫号屏配置</RouterLink>
      </nav>
    </div>

    <button class="logout-button" type="button" :disabled="loggingOut" @click="logoutFromTenantAdmin">
      {{ loggingOut ? '退出中' : '退出登录' }}
    </button>
  </aside>
</template>

<style scoped>
.tenant-nav {
  display: grid;
  align-content: space-between;
  gap: 24px;
  min-height: 100dvh;
  padding: 24px 18px;
  border-right: 1px solid #dbe3ea;
  background: #ffffff;
}

.nav-main,
.brand-block,
.nav-list {
  display: grid;
}

.nav-main {
  gap: 24px;
  align-content: start;
}

.brand-block {
  gap: 5px;
}

.brand-mark {
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
}

.brand-block strong {
  color: #0f172a;
  font-size: 20px;
}

.brand-block small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.nav-list {
  gap: 6px;
}

.nav-item {
  min-height: 38px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 6px;
  color: #334155;
  font-weight: 700;
  text-decoration: none;
}

.nav-item.router-link-active {
  color: #ffffff;
  background: #0f766e;
}

.logout-button {
  min-height: 38px;
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.logout-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 980px) {
  .tenant-nav {
    position: sticky;
    top: 0;
    z-index: 1;
    grid-template-columns: auto 1fr auto;
    min-height: auto;
    align-items: center;
    padding: 12px 16px;
  }

  .nav-main {
    display: contents;
  }

  .nav-list {
    grid-auto-flow: column;
    justify-content: end;
  }

  .logout-button {
    width: auto;
    padding: 0 12px;
  }
}

@media (max-width: 700px) {
  .tenant-nav {
    grid-template-columns: 1fr;
  }

  .nav-main {
    display: grid;
    gap: 12px;
  }

  .nav-list {
    grid-auto-flow: row;
  }

  .logout-button {
    width: 100%;
  }
}
</style>
