<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useAuthSessionStore } from '../../stores/authSession'
import type { AuthStoreAccess } from '../../types/auth'

const props = defineProps<{
  fallbackLabel?: string
  surface?: 'tenant-admin' | 'staff'
}>()

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const { t } = useI18n()

const currentStoreId = computed(() => String(route.params.storeId || auth.user?.defaultStoreId || auth.user?.storeIds[0] || ''))
const stores = computed(() => {
  if (auth.authorizedStores.length > 0) {
    return auth.authorizedStores
  }
  return (auth.user?.storeIds ?? []).map(storeId => ({
    storeId,
    storeCode: '',
    storeName: fallbackStoreLabel(storeId),
    status: 'active',
    locale: '',
    defaultStore: storeId === auth.user?.defaultStoreId
  }))
})
const currentStore = computed(() => stores.value.find(store => store.storeId === currentStoreId.value) ?? stores.value[0])
const displayLabel = computed(() => {
  if (currentStore.value) {
    return storeLabel(currentStore.value)
  }
  return props.fallbackLabel || t('storeSwitcher.unknown')
})
const selectValue = computed(() => currentStore.value?.storeId || currentStoreId.value)
const canSwitch = computed(() => stores.value.length > 1)

onMounted(() => {
  void auth.ensureAuthorizedStores()
})

async function switchStore(event: Event): Promise<void> {
  const nextStoreId = (event.target as HTMLSelectElement).value
  if (!nextStoreId || nextStoreId === currentStoreId.value) {
    return
  }
  await router.push(rewriteStorePath(nextStoreId))
}

function rewriteStorePath(nextStoreId: string): string {
  const encodedStoreId = encodeURIComponent(nextStoreId)
  const path = route.path.includes('/stores/')
    ? route.path.replace(/\/stores\/[^/]+/, `/stores/${encodedStoreId}`)
    : `/stores/${encodedStoreId}/staff`
  const query = new URLSearchParams()
  Object.entries(route.query).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach(item => {
        if (item != null) {
          query.append(key, String(item))
        }
      })
      return
    }
    if (value != null) {
      query.set(key, String(value))
    }
  })
  const queryString = query.toString()
  return `${path}${queryString ? `?${queryString}` : ''}${route.hash || ''}`
}

function storeLabel(store: AuthStoreAccess): string {
  return store.storeName || store.storeCode || fallbackStoreLabel(store.storeId)
}

function fallbackStoreLabel(storeId: string): string {
  return t('staffHome.store.label', { shortId: storeId.slice(0, 8) })
}
</script>

<template>
  <div class="store-switcher" :class="`store-switcher--${surface || 'staff'}`">
    <label v-if="canSwitch" class="switcher-label">
      <span class="sr-only">{{ t('storeSwitcher.label') }}</span>
      <select :value="selectValue" :aria-label="t('storeSwitcher.label')" @change="switchStore">
        <option v-for="store in stores" :key="store.storeId" :value="store.storeId">
          {{ storeLabel(store) }}
        </option>
      </select>
    </label>
    <span v-else class="store-chip">{{ displayLabel }}</span>
  </div>
</template>

<style scoped>
.store-switcher {
  min-width: 0;
}

.switcher-label {
  display: block;
  min-width: 0;
}

select,
.store-chip {
  width: 100%;
  min-height: 34px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 800;
}

select {
  cursor: pointer;
  max-width: 180px;
  padding: 6px 26px 6px 9px;
}

.store-chip {
  display: inline-flex;
  align-items: center;
  max-width: 180px;
  overflow: hidden;
  padding: 0 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.store-switcher--staff select,
.store-switcher--staff .store-chip {
  min-height: 28px;
  max-width: 132px;
  border-color: #c7d2fe;
  border-radius: 999px;
  color: #4338ca;
  background: #eef2ff;
  font-size: 0.74rem;
}

.store-switcher--tenant-admin select,
.store-switcher--tenant-admin .store-chip {
  max-width: 100%;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
