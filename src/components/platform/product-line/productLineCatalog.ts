import productLineEntries from '../../../main/resources/product-line-entries.json'

export const defaultEntryRouteOptions = productLineEntries.map(({ value, labelKey, descriptionKey }) => ({
  value, labelKey, descriptionKey
}))

export type ProductLineEntryRouteOption = (typeof defaultEntryRouteOptions)[number]

export function normalizeProductLineAppKey(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
}

export function isProductLineAppKeyValid(appKey: string): boolean {
  return /^[a-z][a-z0-9_]{1,63}$/.test(appKey)
}
