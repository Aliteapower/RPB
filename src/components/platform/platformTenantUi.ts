import type { TenantStatus } from '../../api/platformApi'

export type TenantFilter = 'all' | 'active' | 'deleted'

export interface TenantStatusOption {
  value: TenantStatus
  labelKey: string
}

export interface PlatformTenantFormModel {
  id: string
  tenantCode: string
  displayName: string
  status: TenantStatus
  defaultLocale: string
  contactPhone: string
  address: string
  principalName: string
  logoMediaUrl: string
  logoFile: File | null
  initialPassword: string
  password: string
  adminStoreIds: string[]
  defaultAdminStoreId: string
}
