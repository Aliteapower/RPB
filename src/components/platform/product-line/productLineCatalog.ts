export const defaultEntryRouteOptions = [
  {
    value: '',
    label: '暂不配置入口',
    description: '先登记产品线，后续开发完成后再配置默认入口'
  },
  {
    value: '/stores/:storeId/staff',
    label: '门店员工首页',
    description: '预约排队叫号系统默认入口'
  }
] as const

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
