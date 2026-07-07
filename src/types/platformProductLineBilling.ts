export type ProductLineStatus = 'active' | 'disabled'
export type ProductBillingCycle = 'monthly' | 'yearly' | 'legacy_grant' | 'manual'
export type ProductSubscriptionStatus = 'active' | 'suspended' | 'cancelled' | 'expired'
export type ProductLinePriceStatus = 'active' | 'disabled'

export interface PlatformProductLinePrice {
  billingCycle: 'monthly' | 'yearly'
  amount: number
  currency: string
  status: ProductLinePriceStatus
  version: number
}

export interface PlatformProductLine {
  appKey: string
  displayName: string
  status: ProductLineStatus
  defaultEntryRoute: string
  description: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
  prices: PlatformProductLinePrice[]
}

export interface PlatformProductLineMutation {
  displayName: string
  status: ProductLineStatus
  defaultEntryRoute?: string
  description?: string | null
  sortOrder?: number
}

export interface PlatformProductLineCreateMutation {
  appKey: string
  displayName: string
  status: ProductLineStatus
  defaultEntryRoute: string
  description?: string | null
  sortOrder?: number
}

export interface PlatformProductLineListQuery {
  keyword?: string
  status?: ProductLineStatus | ''
  page?: number
  size?: number
}

export interface TenantProductSubscription {
  id: string
  tenantId: string
  appKey: string
  productLineName: string
  billingCycle: ProductBillingCycle
  status: ProductSubscriptionStatus
  effectiveStatus: ProductSubscriptionStatus
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  amount: number
  currency: string
  paymentNote: string | null
  entitlementStatus: string
  entitlementValidUntil: string | null
  createdAt: string
  updatedAt: string
  version: number
  items: TenantProductSubscriptionItem[]
}

export interface TenantProductSubscriptionItem {
  id: string
  scopeType: 'tenant' | 'store'
  storeId: string | null
  storeCode: string | null
  storeName: string | null
  operatingEntityId: string | null
  operatingEntityName: string | null
  quantity: number
  unitAmount: number
  amount: number
  currency: string
  status: ProductSubscriptionStatus
  createdAt: string
  updatedAt: string
  version: number
}

export interface ProductSubscriptionMutation {
  idempotencyKey: string
  appKey: string
  billingCycle: ProductBillingCycle
  currentPeriodStart?: string | null
  currentPeriodEnd?: string | null
  durationCount?: number | null
  amount?: number
  currency?: string
  paymentNote?: string | null
  version?: number
}

export interface ProductSubscriptionStatusMutation {
  idempotencyKey: string
  paymentNote?: string | null
  version?: number
}

export interface PlatformProductLineListResponse {
  success: true
  productLines: PlatformProductLine[]
  items?: PlatformProductLine[]
  total?: number
  page?: number
  size?: number
}

export interface PlatformProductLineResponse {
  success: true
  productLine: PlatformProductLine
}

export interface PlatformProductLinePriceMutation {
  prices: Array<{
    billingCycle: 'monthly' | 'yearly'
    amount: number
    currency: string
    status: ProductLinePriceStatus
    version?: number
  }>
}

export interface TenantProductSubscriptionListResponse {
  success: true
  subscriptions: TenantProductSubscription[]
}

export interface TenantProductSubscriptionResponse {
  success: true
  replayed: boolean
  subscription: TenantProductSubscription
  quote: ProductSubscriptionQuote | null
}

export interface ProductSubscriptionQuote {
  durationCount: number
  durationUnit: string
  storeCount: number
  unitAmount: number
  storeUnitAmount: number
  defaultAmount: number
  finalAmount: number
  currency: string
}

export interface PlatformBillingApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
