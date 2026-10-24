import type { ManagedProductKey, StoreProductSetting } from './types'

export async function requestProducts(store: string, key?: ManagedProductKey, enabled?: boolean): Promise<StoreProductSetting[]> {
  const base = `/api/v1/stores/${encodeURIComponent(store)}/tenant-admin/product-lines`
  const response = await fetch(key ? `${base}/${encodeURIComponent(key)}` : base, {
    method: key ? 'PUT' : 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(key ? { 'Content-Type': 'application/json', 'X-RPB-Product-Settings': '1' } : {}) },
    body: key ? JSON.stringify({ enabled }) : undefined
  })
  const result = await response.json()
  if (!response.ok || !result.success || !Array.isArray(result.products)) throw new Error(result.error?.code ?? 'UNKNOWN_ERROR')
  return result.products
}
