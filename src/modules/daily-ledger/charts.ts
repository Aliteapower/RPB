import { cents } from './model'
import { historyReport } from './report'
import type { LedgerDay } from './types'
export interface ChartPoint { date: string; amount: number; submitted: boolean }
export function chartData(days: LedgerDay[], start: string, end: string, mode: 'income' | 'expense', text: (source: string) => string = source => source) {
  const report = historyReport(days), spending = mode === 'expense', key = spending ? 'expenses' : 'revenue'
  const categories = (spending ? report.expenseCategories : [{ id: 'cash', name: text('现金收入'), amount: report.totals.cashIncome }, ...report.channels]).filter(c => c.amount !== 0).sort((a, b) => b.amount - a.amount)
  const byDate = new Map(report.rows.filter(r => r.record).map(r => [r.day.date, cents(r.record!.totals[key])]))
  const daily: ChartPoint[] = [], monthly = new Map<string, ChartPoint>()
  for (let date = start; date <= end && daily.length < 366;) {
    const point = { date, amount: byDate.get(date) ?? 0, submitted: byDate.has(date) }; daily.push(point)
    const month = date.slice(0, 7), entry = monthly.get(month) ?? { date: month, amount: 0, submitted: false }
    entry.amount += point.amount; entry.submitted ||= point.submitted; monthly.set(month, entry)
    const next = new Date(date + 'T00:00:00Z'); next.setUTCDate(next.getUTCDate() + 1); date = next.toISOString().slice(0, 10)
  }
  return { categories, daily, monthly: [...monthly.values()], total: report.totals[key], submittedDays: report.rows.filter(r => r.record).length }
}
