import type { AuditEntry, LedgerDay, Period, SaveRequest, Settings } from './types'
export class LedgerApiError extends Error { constructor(public status: number, public code: string, message: string) { super(message) } }
export function ledgerApi(storeId: string) {
  const base = `/api/v1/stores/${encodeURIComponent(storeId)}/daily-ledger`
  async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const response = await fetch(base + path, { method, credentials: 'same-origin', cache: 'no-store', headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined })
    const data = await response.json()
    if (!response.ok) throw new LedgerApiError(response.status, data.error?.code ?? 'REQUEST_FAILED', data.error?.message ?? data.error?.messageKey ?? '读取或保存失败，请重试')
    return data as T
  }
  return {
    settings: () => request<Settings>('/settings'),
    saveSettings: (value: Settings) => request<Settings>('/settings', 'PUT', value),
    day: (date: string) => request<LedgerDay>(`/days/${date}`),
    save: (date: string, period: Period, value: SaveRequest) => request<LedgerDay>(`/days/${date}/records/${period}`, 'PUT', value),
    history: (start: string, end: string) => request<LedgerDay[]>(`/history?start=${start}&end=${end}`),
    years: () => request<number[]>('/years'),
    audit: (date: string, period: Period) => request<AuditEntry[]>(`/audit?date=${date}&period=${period}`)
  }
}
