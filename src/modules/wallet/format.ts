import type { Bill } from './types'
export const amountInput = (cents: number) => (cents / 100).toFixed(2)
export const money = (cents: number) => new Intl.NumberFormat('en-SG', { style: 'currency', currency: 'SGD' }).format(cents / 100)
export const remaining = (bill: Bill) => bill.amountCents - bill.walletPaidCents - bill.externalPaidCents
export function dateTime(value: string, timezone = 'Asia/Singapore'): string {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: timezone, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(value))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(p => p.type === type)?.value ?? ''
  return `${part('day')}-${part('month')}-${part('year')} ${part('hour')}:${part('minute')}`
}
export function billCode(value: string): { store?: string; code: string } {
  if (/^WB\.[A-Za-z0-9_-]{43}$/.test(value.trim())) return { code: value.trim() }
  const url = new URL(value)
  const match = /^\/wallet\/([0-9a-f-]{36})$/i.exec(url.pathname)
  const code = new URLSearchParams(url.hash.slice(1)).get('bill') ?? ''
  if (url.origin !== window.location.origin || !match || !/^WB\.[A-Za-z0-9_-]{43}$/.test(code)) throw new Error('Invalid wallet bill code')
  return { store: match[1], code }
}
