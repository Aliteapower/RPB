import type {
  PlatformOperatingEntityStatus,
  PlatformStoreStatus,
  PlatformTenantOnboardingMode,
  TenantStatus
} from '../../api/platformApi'

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
  onboardingMode: PlatformTenantOnboardingMode
  initialPassword: string
  password: string
  adminStoreIds: string[]
  defaultAdminStoreId: string
}

export interface PlatformTenantAdminStoreAccessFormModel {
  adminStoreIds: string[]
  defaultAdminStoreId: string
}

export interface PlatformOperatingEntityFormModel {
  id: string
  entityCode: string
  displayName: string
  status: PlatformOperatingEntityStatus
  defaultLocale: string
  contactPhone: string
  address: string
  principalName: string
}

export interface PlatformStoreFormModel {
  id: string
  operatingEntityId: string
  storeCode: string
  storeName: string
  status: PlatformStoreStatus
  timezone: string
  locale: string
  dateFormat: string
  timeFormat: string
  currency: string
  adminUsername: string
  adminPassword: string
}
