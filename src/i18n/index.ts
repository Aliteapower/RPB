import { createI18n } from 'vue-i18n'

import { enSG } from './locales/en-SG'
import { zhCN } from './locales/zh-CN'

export const DEFAULT_FRONTEND_LOCALE = 'zh-CN'
export const SUPPORTED_FRONTEND_LOCALES = ['zh-CN', 'en-SG'] as const
export const FRONTEND_LOCALE_STORAGE_KEY = 'rpb.frontend.locale'

export type FrontendLocale = (typeof SUPPORTED_FRONTEND_LOCALES)[number]
export type TranslationParams = Record<string, string | number | boolean | null | undefined>

const messages = {
  'zh-CN': zhCN,
  'en-SG': enSG
}

const initialLocale = resolveInitialLocale()

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: DEFAULT_FRONTEND_LOCALE,
  messages,
  missingWarn: false,
  fallbackWarn: false
})

setDocumentLanguage(initialLocale)

export function isSupportedFrontendLocale(value: string | null | undefined): value is FrontendLocale {
  return SUPPORTED_FRONTEND_LOCALES.includes(value as FrontendLocale)
}

export function resolveInitialLocale(candidates: Array<string | null | undefined> = defaultLocaleCandidates()): FrontendLocale {
  for (const candidate of candidates) {
    const normalized = normalizeLocale(candidate)
    if (isSupportedFrontendLocale(normalized)) {
      return normalized
    }
  }

  return DEFAULT_FRONTEND_LOCALE
}

export function setFrontendLocale(locale: FrontendLocale): void {
  i18n.global.locale.value = locale
  setDocumentLanguage(locale)

  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(FRONTEND_LOCALE_STORAGE_KEY, locale)
    } catch {
      // Locale selection still applies for this session when storage is unavailable.
    }
  }
}

export function translate(key: string, fallback?: string, params?: TranslationParams): string {
  const translated = i18n.global.t(key, params ?? {})
  return translated === key && fallback ? fallback : translated
}

function defaultLocaleCandidates(): Array<string | null | undefined> {
  return [
    readStoredLocale(),
    ...(typeof navigator === 'undefined' ? [] : navigator.languages),
    typeof navigator === 'undefined' ? undefined : navigator.language
  ]
}

function readStoredLocale(): string | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    return window.localStorage.getItem(FRONTEND_LOCALE_STORAGE_KEY)
  } catch {
    return null
  }
}

function normalizeLocale(value: string | null | undefined): FrontendLocale | null {
  if (!value) {
    return null
  }

  const normalized = value.trim()
  if (normalized === 'zh' || normalized.toLowerCase() === 'zh-cn' || normalized.toLowerCase() === 'zh-hans-cn') {
    return 'zh-CN'
  }
  if (normalized.toLowerCase() === 'en' || normalized.toLowerCase() === 'en-sg') {
    return 'en-SG'
  }

  return isSupportedFrontendLocale(normalized) ? normalized : null
}

function setDocumentLanguage(locale: FrontendLocale): void {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale
  }
}
