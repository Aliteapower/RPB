import { inject, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { StoreDisplay } from './types'
export const displayKey = Symbol('daily-ledger-store-display')
export const defaultDisplay: StoreDisplay = { timezone: 'Asia/Singapore', locale: 'zh-CN', dateFormat: 'DD-MM-YYYY', timeFormat: 'HH:mm', currency: 'SGD' }
export function useLedgerFormat() {
  const { locale } = useI18n({ useScope: 'global' })
  const context = inject<ComputedRef<StoreDisplay>>(displayKey)
  return {
    clock(value: string) { const d = context?.value ?? defaultDisplay; return new Intl.DateTimeFormat(locale.value, { timeZone: d.timezone, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: d.timeFormat.includes('a') }).format(new Date(value)) },
    time(value?: string | null) { if (!value) return ''; const d = context?.value ?? defaultDisplay; return new Intl.DateTimeFormat(locale.value, { timeZone: d.timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: d.timeFormat.includes('a') }).format(new Date(value)) },
    date(value: string) { const d = context?.value ?? defaultDisplay; const [year, month, day] = value.split('-'); return d.dateFormat.replace('YYYY', year!).replace('MM', month!).replace('DD', day!) }
  }
}
