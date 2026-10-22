import { cents, money } from './model'
import type { LedgerDay } from './types'
export function historyReport(days: LedgerDay[]) {
  const totals = { noncash: 0, expenses: 0, revenue: 0, handover: 0, cashIncome: 0 }
  const channels = new Map<string, { id: string; name: string; amount: number }>()
  const expenseCategories = new Map<string, { id: string; name: string; amount: number }>()
  const rows = days.map(day => {
    const record = day.last_period ? day.submitted_records[day.last_period] : undefined
    if (record) {
      totals.noncash += cents(record.totals.noncash); totals.expenses += cents(record.totals.expenses)
      totals.revenue += cents(record.totals.revenue); totals.handover += cents(record.totals.handover)
      totals.cashIncome += cents(record.totals.revenue) - cents(record.totals.noncash)
      for (const [id, field] of Object.entries(record.payments)) {
        const entry = channels.get(id) ?? { id, name: day.config.payments.find(p => p.id === id)?.name ?? id, amount: 0 }
        entry.amount += cents(field.value); channels.set(id, entry)
      }
      for (const row of record.expenses) {
        const id = row.category ?? 'other'
        const entry = expenseCategories.get(id) ?? { id, name: day.config.expenses.find(p => p.id === id)?.name ?? id, amount: 0 }
        entry.amount += cents(row.value); expenseCategories.set(id, entry)
      }
    }
    return { day, record }
  })
  return { rows, totals, channels: [...channels.values()], expenseCategories: [...expenseCategories.values()] }
}
export function csv(days: LedgerDay[]) {
  const report = historyReport(days)
  const rows: unknown[][] = [['日期', '指定统计班次', '盘点现金', '已抽大钞', '非现金收入', '已付现金开支', '保留底钱', '营业额', '现金总交款', '记录员工', '保存时间(UTC)', '记账口径', '现金收入', ...report.channels.map(c => c.name)]]
  for (const { day, record: r } of report.rows) if (r) rows.push([day.date, day.config.periods.find(p => p.id === day.last_period)?.name, r.totals.cash, r.totals.withdrawn, r.totals.noncash, r.totals.expenses, r.base_amount ?? day.config.base_amount, r.totals.revenue, r.totals.handover, r.saved_by, r.submitted_at ?? r.saved_at, r.accounting_version >= 5 ? '盘点含底钱' : '旧口径盘点不含底钱', money(cents(r.totals.revenue) - cents(r.totals.noncash)), ...report.channels.map(c => r.payments[c.id]?.value ?? '0.00')])
  const t = report.totals
  rows.push(['查询结果合计', '', '', '', money(t.noncash), money(t.expenses), '', money(t.revenue), money(t.handover), '', '', '', money(t.cashIncome), ...report.channels.map(c => money(c.amount))])
  const cell = (value: unknown) => { let text = String(value ?? ''); if (/^[=+\-@\t\r\n]/.test(text)) text = "'" + text; return '"' + text.replaceAll('"', '""') + '"' }
  return '\ufeff' + rows.map(row => row.map(cell).join(',')).join('\r\n') + '\r\n'
}
