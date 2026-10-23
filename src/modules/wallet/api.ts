export class WalletApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly details: Record<string, unknown> = {}) { super(message); this.name = 'WalletApiError' }
}
export function createWalletBill(store: string, accountId: string, amount: string, customerCode: string) {
  return walletApi(store).command<import('./types').Bill>('/bills', { accountId, amount, ...(customerCode ? { customerCode } : {}) })
}

export function walletApi(store: string, customer = false) {
  const base = `/api/v1/${customer ? 'public/' : ''}stores/${encodeURIComponent(store)}/wallet`
  async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const response = await fetch(base + path, { method, credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-RPB-Wallet': '1', 'X-RPB-Wallet-Model': 'components-v2' }, body: body === undefined ? undefined : JSON.stringify(body) })
    const result = await response.json().catch(() => ({})) as { code?: string; message?: string } & Record<string, unknown>
    if (!response.ok) throw new WalletApiError(response.status, result.code ?? 'WALLET_ERROR', result.message ?? `HTTP ${response.status}`,result)
    return result as T
  }
  async function command<T>(path: string, body: Record<string, unknown> = {}, method = 'POST'): Promise<T> {
    // Keep a retry key across refresh/network failure without storing receipts, codes or customer data.
    const payload = JSON.stringify([store, customer, path, method, body])
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(payload))
    const slot = 'rpb.wallet.retry.' + Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, '0')).join('')
    let key: string | null = null
    try { key = sessionStorage.getItem(slot) } catch { /* private browser */ }
    key ??= crypto.randomUUID()
    try { sessionStorage.setItem(slot, key) } catch { /* transaction also has server-side uniqueness */ }
    const result = await request<T>(path, method, { ...body, idempotencyKey: key })
    try { sessionStorage.removeItem(slot) } catch { /* no sensitive payload stored */ }
    return result
  }
  return { get: <T>(path: string) => request<T>(path), post: <T>(path: string, body?: unknown) => request<T>(path, 'POST', body), command }
}
