<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthSessionStore } from '../../stores/authSession'

const router = useRouter()
const auth = useAuthSessionStore()
const loggingOut = ref(false)

async function logoutFromPlatform(): Promise<void> {
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
  <aside class="platform-nav" aria-label="平台后台导航">
    <div class="nav-main">
      <div class="brand-block">
        <span class="brand-mark">RPB</span>
        <strong>平台后台</strong>
      </div>
      <nav class="nav-list">
        <RouterLink class="nav-item" to="/platform/tenants">租户管理</RouterLink>
        <RouterLink class="nav-item" to="/platform/settings/product-lines">产品线</RouterLink>
        <RouterLink class="nav-item" to="/platform/call-screen/text-seed">叫号模板</RouterLink>
      </nav>
    </div>

    <button class="logout-button" type="button" :disabled="loggingOut" @click="logoutFromPlatform">
      {{ loggingOut ? '退出中' : '退出登录' }}
    </button>
  </aside>
</template>

<style scoped>
.platform-nav {
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
  gap: 6px;
}

.brand-mark {
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
}

.brand-block strong {
  font-size: 20px;
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

.nav-item--disabled {
  color: #94a3b8;
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
  .platform-nav {
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
  .platform-nav {
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
