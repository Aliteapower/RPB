import { computed, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

import { fetchMeApps } from '../api/meAppsApi'
import type { MeAppEntry } from '../types/meApps'

export function useStoreVisibleApps(storeId: Ref<string | undefined>) {
  const apps = ref<MeAppEntry[]>([])
  const historyApps = ref<MeAppEntry[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  let loadSequence = 0
  let disposed = false
  let timer: ReturnType<typeof setInterval> | undefined
  let browserWindow: Window | undefined
  let browserDocument: Document | undefined

  async function refresh() {
    const nextStoreId = storeId.value
    const sequence = ++loadSequence
    const current = () => !disposed && sequence === loadSequence && storeId.value === nextStoreId

    if (!nextStoreId) {
      loading.value = false
      loaded.value = true
      return
    }

    loading.value = true

    try {
      const result = await fetchMeApps(nextStoreId)

      if (current()) {
        apps.value = result.apps; historyApps.value = result.historyApps ?? []
      }
    } catch {
      if (current()) {
        apps.value = []; historyApps.value = []
      }
    } finally {
      if (current()) {
        loading.value = false
        loaded.value = true
      }
    }
  }

  watch(storeId, () => {
    apps.value = []; historyApps.value = []
    loaded.value = false
    void refresh()
  }, { immediate: true })

  function refreshVisiblePage() {
    if (!disposed && !loading.value && browserDocument?.visibilityState !== 'hidden') void refresh()
  }
  onMounted(() => {
    if (typeof window === 'undefined') return
    browserWindow = window
    browserDocument = typeof document === 'undefined' ? undefined : document
    browserWindow.addEventListener('focus', refreshVisiblePage)
    browserWindow.addEventListener('pageshow', refreshVisiblePage)
    browserDocument?.addEventListener('visibilitychange', refreshVisiblePage)
    timer = setInterval(refreshVisiblePage, 30_000)
  })
  onUnmounted(() => {
    disposed = true
    loadSequence++
    if (timer !== undefined) clearInterval(timer)
    browserWindow?.removeEventListener('focus', refreshVisiblePage)
    browserWindow?.removeEventListener('pageshow', refreshVisiblePage)
    browserDocument?.removeEventListener('visibilitychange', refreshVisiblePage)
  })

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
    historyApps,
    visibleApps,
    visibleAppKeys,
    loaded,
    loading,
    findVisibleApp,
    hasVisibleApp
  }
}
