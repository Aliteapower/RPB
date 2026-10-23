import type { Bill } from './types'
export const staffWalletPath = (store: string, page = '') => `/stores/${encodeURIComponent(store)}/wallet${page ? '/' + page : ''}`
export const customerWalletPath = (store: string, page = '') => `/wallet/${encodeURIComponent(store)}${page ? '/' + page : ''}`
export function billStage(bill: Pick<Bill, 'amountCents'|'walletPaidCents'|'externalPaidCents'|'walletApplied'|'closed'>) {
  if (bill.amountCents <= bill.walletPaidCents + bill.externalPaidCents) return 'paid'
  if (bill.closed) return 'closed'
  return bill.walletApplied ? 'due' : 'waiting'
}
export function legacyWalletTarget(store: string, hash: string) {
  const params = new URLSearchParams(hash.slice(1))
  if (params.get('receipt')) return { type: 'receipt' as const, path: customerWalletPath(store, `bills/${encodeURIComponent(params.get('receipt')!)}`) }
  if (params.get('bill')) return { type: 'scan' as const, store, code: params.get('bill')! }
  if (params.get('redeem')) return { type: 'scan' as const, store, code: params.get('redeem')! }
  return null
}
