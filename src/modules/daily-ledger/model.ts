import type { Field, LedgerConfig, LedgerDay, LedgerRecord, Period, Totals } from './types'
export const denominations = ['100', '50', '10', '5', '2', '1', '0.5', '0.2', '0.1']
export const field = (): Field => ({ value: '', note: '', entered_at: null })
export const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
export function restoreInputs(current: LedgerRecord, backup: LedgerRecord, preserveWithdrawals: boolean): LedgerRecord {
  return { ...clone(current), cash: clone(backup.cash), coin: clone(backup.coin), payments: clone(backup.payments), expenses: clone(backup.expenses), withdrawals: clone(preserveWithdrawals ? current.withdrawals : backup.withdrawals) }
}
export const cents = (value: string | undefined) => Math.round(Number(value || 0) * 100)
export const money = (value: number) => (value / 100).toFixed(2)
export function blankRecord(config: LedgerConfig): LedgerRecord {
  return { version: 0, status: 'draft', cash: Object.fromEntries(denominations.map(d => [d, field()])), coin: field(), payments: Object.fromEntries(config.payments.filter(p => p.enabled).map(p => [p.id, field()])), expenses: [], withdrawals: [], withdrawal_basis: [], withdrawal_snapshot: [], accounting_version: 5, base_amount: config.base_amount, totals: { cash: '0.00', noncash: '0.00', expenses: '0.00', withdrawn: '0.00', revenue: '0.00', handover: '0.00' } }
}
export function basis(day: LedgerDay, period: Period) {
  return period === day.config.final_period ? day.config.periods.filter(p => p.id !== period).map(p => ({ period: p.id, version: day.records[p.id as Period]?.version ?? 0 })) : []
}
export function withdrawalSnapshot(day: LedgerDay, period: Period) {
  return basis(day, period).flatMap(source => (day.records[source.period as Period]?.withdrawals ?? []).map(row => ({ ...clone(row), source_period: source.period, source_version: source.version })))
}
export function totals(record: LedgerRecord): Totals {
  const cash = denominations.reduce((sum, d) => sum + cents(d) * Number(record.cash[d]?.value || 0), 0) + cents(record.coin.value)
  const noncash = Object.values(record.payments).reduce((sum, row) => sum + cents(row.value), 0)
  const expenses = record.expenses.reduce((sum, row) => sum + cents(row.value), 0)
  const withdrawn = [...record.withdrawals, ...(record.withdrawal_snapshot ?? [])].reduce((sum, row) => sum + cents(row.value), 0)
  const base = record.accounting_version >= 5 ? cents(record.base_amount) : 0
  return { cash: money(cash), noncash: money(noncash), expenses: money(expenses), withdrawn: money(withdrawn), revenue: money(cash + withdrawn + noncash + expenses - base), handover: money(cash + withdrawn - base) }
}
export function businessDate(timezone = 'Asia/Singapore', now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}
