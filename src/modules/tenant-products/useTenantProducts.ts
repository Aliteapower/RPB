import { onUnmounted, ref, watch, type Ref } from 'vue'
import { requestProducts } from './api'
import type { StoreProductSetting } from './types'

export function useTenantProducts(store: Ref<string>) {
  const products = ref<StoreProductSetting[]>([])
  const loading = ref(false), saving = ref(''), error = ref(''), saved = ref(false), changeCount = ref(0)
  let generation = 0
  async function refresh() {
    if (saving.value) return
    const current = ++generation, id = store.value
    products.value = []; error.value = ''; saved.value = false; loading.value = true
    try {
      const result = await requestProducts(id)
      if (current === generation) products.value = result
    } catch (e) {
      if (current === generation) error.value = errorKey(e, 'loadFailed')
    } finally {
      if (current === generation) loading.value = false
    }
  }
  async function toggle(product: StoreProductSetting) {
    if (saving.value || loading.value || (!product.enabled && !product.canEnable) || !products.value.includes(product)) return
    const current = generation, id = store.value
    saving.value = product.appKey; error.value = ''; saved.value = false
    try {
      const result = await requestProducts(id, product.appKey, !product.enabled)
      const updated = result.find(row => row.appKey === product.appKey)
      if (!updated) throw new Error('INVALID_RESPONSE')
      if (current === generation) {
        products.value = products.value.map(row => row.appKey === updated.appKey ? updated : row)
        saved.value = true; changeCount.value++
      }
    } catch (e) {
      if (current === generation) error.value = errorKey(e, 'saveFailed')
    } finally {
      if (current === generation) saving.value = ''
    }
  }
  watch(store, () => { saving.value = ''; void refresh() }, { immediate: true })
  onUnmounted(() => generation++)
  return { products, loading, saving, error, saved, changeCount, refresh, toggle }
}

function errorKey(error: unknown, fallback: string): string {
  return error instanceof Error && ['UNAUTHENTICATED', 'FORBIDDEN', 'STORE_SCOPE_MISMATCH'].includes(error.message)
    ? 'tenantProducts.accessDenied' : `tenantProducts.${fallback}`
}
