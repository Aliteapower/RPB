export type I18nCatalogScopeLevel = 'tenant' | 'store'
export type I18nCatalogMessageStatus = 'active' | 'inactive'

export interface I18nCatalogMessage {
  message: string
  status: I18nCatalogMessageStatus
  version: number
}

export interface I18nCatalogKey {
  i18nKey: string
  namespace: string
  category: string
  displayName: string
  description: string
  textKind: 'label' | 'template' | 'status' | 'prompt'
  tenantEditable: boolean
  placeholderNames: string[]
  status: string
  sortOrder: number
}

export interface I18nCatalogLocaleEntry {
  locale: string
  platformMessage: I18nCatalogMessage | null
  tenantOverride: I18nCatalogMessage | null
  storeOverride: I18nCatalogMessage | null
  effectiveMessage: string
  effectiveSource: 'store' | 'tenant' | 'platform' | 'frontend_fallback'
}

export interface I18nCatalogEntry {
  key: I18nCatalogKey
  locales: I18nCatalogLocaleEntry[]
}

export interface I18nCatalogResponse {
  success: true
  supportedLocales: string[]
  entries: I18nCatalogEntry[]
}

export interface I18nCatalogMessageMutation {
  i18nKey: string
  locale: string
  message?: string | null
  status?: I18nCatalogMessageStatus
  version?: number | null
  clear?: boolean
}

export interface PlatformI18nCatalogMutation {
  messages: I18nCatalogMessageMutation[]
}

export interface TenantAdminI18nCatalogMutation {
  scopeLevel: I18nCatalogScopeLevel
  messages: I18nCatalogMessageMutation[]
}

export interface I18nCatalogApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
