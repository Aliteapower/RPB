import { useI18n } from 'vue-i18n'
import { ledgerMessageKeys } from './locales/messages'

/** UI translation only. Frozen configuration and operator-entered text remain untouched. */
export function useLedgerI18n() {
  const { t, locale } = useI18n({ useScope: 'global' })
  function text(source: string, params: Record<string, string | number> = {}) {
    const key = ledgerMessageKeys[source]
    return key ? t(`dailyLedger.messages.${key}`, params) : source
  }
  function errorText(source: string) {
    const raw = source.replace(/^(?:Error|LedgerApiError): /, '')
    if (ledgerMessageKeys[raw]) return text(raw)
    // Gate/network errors may originate outside this module. Never show untranslated server prose.
    return locale.value.startsWith('en') ? text('读取或保存失败，请重试') : raw
  }
  function itemName(item?: { id: string; name: string }, fallback = '') {
    if (!item) return fallback
    // Only canonical built-in period names are UI labels. Custom configuration is business data.
    const defaults: Record<string, string[]> = { breakfast: ['早餐', '早上'], lunch: ['午餐', '中午'], dinner: ['晚餐', '晚上'] }
    return defaults[item.id]?.includes(item.name) ? text(item.name) : item.name
  }
  return { text, locale, errorText, itemName }
}
