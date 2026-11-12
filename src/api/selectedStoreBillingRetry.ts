import type { ProductSubscriptionMutation } from '../types/platformProductLineBilling'

/** Preserve an uncertain selected-store billing command across retries, without persisting its note or store IDs. */
export async function selectedStoreBillingRetry<T>(path: string, input: ProductSubscriptionMutation, send: (body: ProductSubscriptionMutation) => Promise<T>): Promise<T> {
  const { idempotencyKey: initialKey, ...content } = input
  const normalized = { ...content, storeIds: content.storeIds ? [...content.storeIds].sort() : undefined }
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify([path, normalized])))
  const slot = 'rpb.wallet.billing.retry.' + Array.from(new Uint8Array(hash), n => n.toString(16).padStart(2, '0')).join('')
  let key = initialKey
  try { key = sessionStorage.getItem(slot) || key; sessionStorage.setItem(slot, key) } catch { /* server uniqueness still applies */ }
  const result = await send({ ...normalized, idempotencyKey: key })
  try { sessionStorage.removeItem(slot) } catch { /* no raw payload stored */ }
  return result
}
