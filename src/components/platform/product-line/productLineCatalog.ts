export const defaultEntryRouteOptions = [
  {
    value: '',
    labelKey: 'platform.productLines.entryRoutes.none.label',
    descriptionKey: 'platform.productLines.entryRoutes.none.description'
  },
  {
    value: '/stores/:storeId/staff',
    labelKey: 'platform.productLines.entryRoutes.staffHome.label',
    descriptionKey: 'platform.productLines.entryRoutes.staffHome.description'
  }
] as const

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
