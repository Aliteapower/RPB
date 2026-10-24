import { computed, ref, watch, type Ref } from 'vue'

import { fetchMeApps } from '../api/meAppsApi'
import type { MeAppEntry } from '../types/meApps'

export function useStoreVisibleApps(storeId: Ref<string | undefined>) {
  const apps = ref<MeAppEntry[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  let loadSequence = 0

  watch(
    storeId,
    async nextStoreId => {
      const sequence = ++loadSequence
      apps.value = []
      loaded.value = false

      if (!nextStoreId) {
        loading.value = false
        loaded.value = true
        return
      }

      loading.value = true

      try {
        const result = await fetchMeApps(nextStoreId)

        if (sequence === loadSequence) {
          apps.value = result.apps
        }
      } catch {
        if (sequence === loadSequence) {
          apps.value = []
        }
      } finally {
        if (sequence === loadSequence) {
          loading.value = false
          loaded.value = true
        }
      }
    },
    { immediate: true }
  )

  const visibleApps = computed(() => apps.value.filter(app => app.entryVisible))
  const visibleAppKeys = computed(() => new Set(visibleApps.value.map(app => app.appKey)))

  function findVisibleApp(appKey: string): MeAppEntry | undefined {
    return visibleApps.value.find(app => app.appKey === appKey)
  }

  function hasVisibleApp(appKey: string): boolean {
    return visibleAppKeys.value.has(appKey)
  }

  return {
    apps,
    visibleApps,
    visibleAppKeys,
    loaded,
    loading,
    findVisibleApp,
    hasVisibleApp
  }
}
