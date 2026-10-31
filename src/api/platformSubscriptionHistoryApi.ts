import { PlatformBillingApiError } from './platformProductLineBillingApi'
import type { SubscriptionHistoryResponse } from '../types/platformSubscriptionHistory'

export async function listSubscriptionHistory(
  tenantId: string,
  subscriptionId: string,
  signal?: AbortSignal,
  fetcher: typeof fetch = globalThis.fetch.bind(globalThis)
): Promise<SubscriptionHistoryResponse> {
  let response: Response
  try {
    response = await fetcher(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/${encodeURIComponent(subscriptionId)}/events`, {
      method: 'GET', credentials: 'include', cache: 'no-store', signal, headers: { Accept: 'application/json' }
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw historyError(0)
  }
  let payload: unknown
  try { payload = await response.json() } catch { throw historyError(response.status) }
  const data = payload as { success?: boolean; error?: { code?: string; messageKey?: string }; events?: unknown[] } | null
  if (!response.ok || data?.success === false) {
    if (typeof data?.error?.code === 'string' && typeof data.error.messageKey === 'string') {
      throw new PlatformBillingApiError(response.status, {success: false, error: {code: data.error.code, messageKey: data.error.messageKey, details: {}}})
    }
    throw historyError(response.status)
  }
  if (!Array.isArray(data?.events)) throw historyError(response.status)
  return data as SubscriptionHistoryResponse
}

function historyError(status: number): PlatformBillingApiError {
  return new PlatformBillingApiError(status, {success: false, error: {code: 'UNKNOWN_ERROR', messageKey: 'platform.billing.unknown_error', details: {}}})
}
