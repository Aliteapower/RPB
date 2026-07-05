<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useAuthSessionStore } from '../../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const { t } = useI18n()
const loggingOut = ref(false)

const storeId = computed(() => String(route.params.storeId || auth.user?.defaultStoreId || auth.user?.storeIds[0] || ''))
const navItems = computed(() => [
  { to: `/stores/${storeId.value}/admin/profile`, labelKey: 'nav.tenant.profile' },
  { to: `/stores/${storeId.value}/admin/staff`, labelKey: 'nav.tenant.staff' },
  { to: `/stores/${storeId.value}/admin/tables`, labelKey: 'nav.tenant.tables' },
  { to: `/stores/${storeId.value}/admin/settings`, labelKey: 'nav.tenant.settings' },
  { to: `/stores/${storeId.value}/admin/share-template`, labelKey: 'nav.tenant.shareTemplate' },
  { to: `/stores/${storeId.value}/admin/public-booking`, labelKey: 'nav.tenant.publicBooking' },
  { to: `/stores/${storeId.value}/admin/call-screen`, labelKey: 'nav.tenant.callScreen' }
])

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
  <aside class="tenant-nav" :aria-label="t('nav.tenant.aria')">
    <div class="nav-main">
      <div class="brand-block">
        <span class="brand-mark">RPB</span>
        <strong>{{ t('nav.tenant.title') }}</strong>
        <small>{{ t('nav.tenant.storePrefix') }} {{ auth.user?.username || '20000000' }}</small>
      </div>
      <nav class="nav-list">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="nav-item"
          :to="item.to"
        >
          {{ t(item.labelKey) }}
        </RouterLink>
      </nav>
    </div>

    <button class="logout-button" type="button" :disabled="loggingOut" @click="logoutFromTenantAdmin">
      {{ loggingOut ? t('common.actions.loggingOut') : t('common.actions.logout') }}
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-list {
  gap: 6px;
  min-width: 0;
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
  white-space: nowrap;
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
    grid-template-columns: auto minmax(0, 1fr) auto;
    min-height: auto;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    border-right: 0;
    border-bottom: 1px solid #dbe3ea;
  }

  .nav-main {
    display: contents;
  }

  .nav-list {
    grid-auto-flow: column;
    grid-auto-columns: max-content;
    justify-content: start;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .nav-list::-webkit-scrollbar {
    display: none;
  }

  .logout-button {
    width: auto;
    padding: 0 12px;
  }
}

@media (max-width: 700px) {
  .tenant-nav {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px 10px;
    padding: 10px 12px;
  }

  .brand-block {
    min-width: 0;
    gap: 3px;
  }

  .brand-block strong {
    font-size: 18px;
  }

  .nav-list {
    grid-column: 1 / -1;
    width: 100%;
  }

  .nav-item {
    min-height: 36px;
    padding: 0 11px;
  }

  .logout-button {
    min-height: 34px;
    width: auto;
  }
}
</style>
