import { i18n, type FrontendLocale, type TranslationParams } from './index'
import { generatedEnSG } from './locales/generated-en-SG'
import { generatedZhCN } from './locales/generated-zh-CN'

const generatedMessages: Record<FrontendLocale, Record<string, string>> = {
  'zh-CN': generatedZhCN,
  'en-SG': generatedEnSG
}

export function generatedText(
  key: string,
  params: TranslationParams = {},
  locale = i18n.global.locale.value as FrontendLocale
): string {
  const template = generatedMessages[locale]?.[key] ?? generatedMessages['zh-CN'][key] ?? key
  return Object.entries(params).reduce(
    (text, [paramKey, value]) => text.replaceAll(`{${paramKey}}`, String(value ?? '')),
    template
  )
}

export function useGeneratedText() {
  return {
    gt: generatedText
  }
}
