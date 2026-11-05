import type { Bill } from './types'
export interface PaymentResult { kind: 'paid' | 'partial' | 'unfunded'; walletCents: number; receivedCents: number; dueCents: number }
type Receipt = Pick<Bill, 'id' | 'storeId' | 'amountCents' | 'walletPaidCents' | 'externalPaidCents' | 'walletApplied' | 'closed' | 'refundStatus'>

/** Show only persisted money state, never a scan event, authorization or optimistic click. */
export function paymentResult(bill: Receipt): PaymentResult | null {
  if (bill.closed || bill.refundStatus !== 'none') return null
  const receivedCents = bill.walletPaidCents + bill.externalPaidCents
  const dueCents = Math.max(0, bill.amountCents - receivedCents)
  if (dueCents > 0 && !bill.walletApplied) return null
  return { kind: dueCents === 0 ? 'paid' : bill.walletPaidCents > 0 ? 'partial' : 'unfunded', walletCents: bill.walletPaidCents, receivedCents, dueCents }
}
export function paymentTransition(previous: Receipt | null, next: Receipt): PaymentResult | null {
  if (!previous || previous.id !== next.id || previous.storeId !== next.storeId) return null
  const result = paymentResult(next), old = paymentResult(previous)
  if (!result || (old?.kind === result.kind && old.walletCents === result.walletCents && old.receivedCents === result.receivedCents)) return null
  return result
}
