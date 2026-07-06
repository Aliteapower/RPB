import type { I18nCatalogKey } from '../types/i18nCatalog'

type Translate = (key: string) => string

const namespaceLabelKeys: Record<string, string> = {
  reason: 'i18nCatalog.namespaces.reason',
  status: 'i18nCatalog.namespaces.status',
  public_booking: 'i18nCatalog.namespaces.public_booking',
  reservation_share: 'i18nCatalog.namespaces.reservation_share',
  queue: 'i18nCatalog.namespaces.queue',
  call_screen: 'i18nCatalog.namespaces.call_screen',
  reservation_meal_period: 'i18nCatalog.namespaces.reservation_meal_period'
}

const categoryLabelKeys: Record<string, string> = {
  cancellation: 'i18nCatalog.categories.cancellation',
  no_show: 'i18nCatalog.categories.no_show',
  queue: 'i18nCatalog.categories.queue',
  table: 'i18nCatalog.categories.table',
  cleaning: 'i18nCatalog.categories.cleaning',
  reservation: 'i18nCatalog.categories.reservation',
  prompt: 'i18nCatalog.categories.prompt',
  template: 'i18nCatalog.categories.template',
  display: 'i18nCatalog.categories.display',
  display_name: 'i18nCatalog.categories.display_name',
  restaurant_default: 'i18nCatalog.categories.restaurant_default'
}

const textKindLabelKeys: Record<string, string> = {
  label: 'i18nCatalog.textKinds.label',
  template: 'i18nCatalog.textKinds.template',
  status: 'i18nCatalog.textKinds.status',
  prompt: 'i18nCatalog.textKinds.prompt'
}

export function i18nCatalogNamespaceLabel(namespace: string, t: Translate): string {
  return i18nCatalogValueLabel(namespace, namespaceLabelKeys, t)
}

export function i18nCatalogCategoryLabel(category: string, t: Translate): string {
  return i18nCatalogValueLabel(category, categoryLabelKeys, t)
}

export function i18nCatalogTextKindLabel(textKind: string, t: Translate): string {
  return i18nCatalogValueLabel(textKind, textKindLabelKeys, t)
}

export function i18nCatalogEntryMetaLabel(key: I18nCatalogKey, t: Translate): string {
  return [
    i18nCatalogNamespaceLabel(key.namespace, t),
    i18nCatalogCategoryLabel(key.category, t),
    i18nCatalogTextKindLabel(key.textKind, t)
  ].join(' / ')
}

function i18nCatalogValueLabel(value: string, labelKeys: Record<string, string>, t: Translate): string {
  const key = labelKeys[value]
  return key ? t(key) : humanizeCatalogCode(value)
}

function humanizeCatalogCode(value: string): string {
  const normalized = value.trim()
  if (!normalized) {
    return normalized
  }
  return normalized
    .split(/[._-]+/)
    .filter(Boolean)
    .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}
