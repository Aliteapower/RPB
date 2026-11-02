import { computed, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

import { fetchMeApps } from '../api/meAppsApi'
import type { MeAppEntry, ProductRouteAccess } from '../types/meApps'

export function useStoreVisibleApps(storeId: Ref<string | undefined>, options: {includeAccess?:boolean; identity?:Ref<unknown>} = {}) {
  const apps = ref<MeAppEntry[]>([])
  const historyApps = ref<MeAppEntry[]>([])
  const productAccess = ref<ProductRouteAccess[]>([])
  const failed = ref(false)
  const loaded = ref(false)
  const loading = ref(false)
  let loadSequence = 0
  let pendingScope: string | undefined
  let disposed = false
  let timer: ReturnType<typeof setInterval> | undefined
  let browserWindow: Window | undefined
  let browserDocument: Document | undefined

  async function refresh() {
    const nextStoreId = storeId.value
    const identity = options.identity?.value
    const scope = JSON.stringify([nextStoreId,identity])
    if (pendingScope === scope) return
    pendingScope = scope
    const sequence = ++loadSequence
    const current = () => !disposed && sequence === loadSequence && storeId.value === nextStoreId && options.identity?.value === identity

    if (!nextStoreId) {
      pendingScope = undefined
      loading.value = false
      loaded.value = true
      return
    }

    loading.value = true

    try {
      const result = await fetchMeApps(nextStoreId, fetch, options.includeAccess)
      if (options.includeAccess && !result.productAccess) throw new Error('Product access summary missing')

      if (current()) {
        failed.value = false; productAccess.value = result.productAccess ?? []; apps.value = result.apps; historyApps.value = result.historyApps ?? []
      }
    } catch {
      if (current()) {
        failed.value = true
        apps.value = []; historyApps.value = []; productAccess.value = []
      }
    } finally {
      if (current()) {
        pendingScope = undefined
        loading.value = false
        loaded.value = true
      }
    }
  }

  watch([storeId, () => options.identity?.value], () => {
    pendingScope = undefined
    failed.value = false
    productAccess.value = []
    apps.value = []; historyApps.value = []
    loaded.value = false
    void refresh()
  }, { immediate: true, flush: 'sync' })

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
    productAccess,
    failed,
    refresh,
    visibleApps,
    visibleAppKeys,
    loaded,
    loading,
    findVisibleApp,
    hasVisibleApp
  }
}
