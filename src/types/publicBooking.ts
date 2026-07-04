export interface PublicBookingErrorResponse {
  success: false
  error: string
}

export interface PublicBookingStore {
  storeId: string
  storeName: string
  timezone: string
  shareAddress: string | null
  googleMapUrl: string | null
  shareContactPhone: string | null
  shareEmail: string | null
  whatsappBusinessPhoneE164: string | null
}

export interface PublicBookingSettings {
  enabled: boolean
  requireCustomerLogin: boolean
  defaultQuotaMode: 'percentage' | 'table_count' | 'guest_count'
  defaultQuotaPercent: number | null
  defaultTableCount: number | null
  defaultGuestCount: number | null
  minLeadMinutes: number
  maxAdvanceDays: number
}

export interface PublicBookingTimeSlot {
  periodKey: string
  displayName: string
  businessDate: string
  localTime: string
  startAt: string
  nextDay: boolean
  selectable: boolean
}

export interface PublicBookingAuthProvider {
  provider: 'google' | 'facebook'
  clientId: string
}

export interface PublicBookingContextResponse {
  success: true
  store: PublicBookingStore
  settings: PublicBookingSettings
  businessDate: string
  timeSlots: PublicBookingTimeSlot[]
  emailAuthEnabled: boolean
  authProviders: PublicBookingAuthProvider[]
}

export interface CustomerAuthEmailCodeResponse {
  success: true
  email: string
  expiresAt: string
  devCode: string | null
}

export interface CustomerAuthPrincipal {
  tenantId: string
  customerId: string
  email: string
  displayName: string | null
}

export interface CustomerAuthLoginResponse {
  success: true
  principal: CustomerAuthPrincipal
  expiresAt: string
}

export interface CustomerAuthMeResponse {
  success: true
  principal: CustomerAuthPrincipal | null
}

export interface PublicBookingCreateMutation {
  partySize: number
  reservedStartAt: string
  businessDate: string
  phoneE164: string | null
  note: string | null
}

export interface PublicBookingCreateResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  partySize: number
  businessDate: string
  reservedStartAt: string
  reservedEndAt: string
  holdUntilAt: string
}

export interface TenantAdminPublicBookingSettingsResponse extends PublicBookingSettings {
  success: true
}

export interface TenantAdminPublicBookingSettingsMutation {
  enabled: boolean
  requireCustomerLogin: boolean
  defaultQuotaMode: 'percentage' | 'table_count' | 'guest_count'
  defaultQuotaPercent: number | null
  defaultTableCount: number | null
  defaultGuestCount: number | null
  minLeadMinutes: number
  maxAdvanceDays: number
}

export interface TenantAdminCustomerEmailSettingsResponse {
  success: true
  enabled: boolean
  provider: 'smtp'
  fromEmail: string | null
  fromName: string | null
  smtpHost: string | null
  smtpPort: number
  smtpUsername: string | null
  smtpStartTls: boolean
  secretConfigured: boolean
}

export interface TenantAdminCustomerEmailSettingsMutation {
  enabled: boolean
  fromEmail: string | null
  fromName: string | null
  smtpHost: string | null
  smtpPort: number
  smtpUsername: string | null
  smtpPassword: string | null
  smtpStartTls: boolean
}

export interface TenantAdminCustomerOAuthProviderSettingsResponse {
  success: true
  enabled: boolean
  provider: 'google' | 'facebook'
  clientId: string | null
  secretConfigured: boolean
}

export interface TenantAdminCustomerOAuthProviderSettingsMutation {
  enabled: boolean
  clientId: string | null
  clientSecret: string | null
}

export type TenantAdminPublicBookingRuleType = 'weekly' | 'date_exception'

export interface TenantAdminPublicBookingAvailabilityRule {
  id: string | null
  ruleType: TenantAdminPublicBookingRuleType
  businessDate: string | null
  dayOfWeek: number | null
  periodKey: string | null
  quotaMode: 'percentage' | 'table_count' | 'guest_count' | 'closed'
  quotaPercent: number | null
  tableCount: number | null
  guestCount: number | null
}

export interface TenantAdminPublicBookingAvailabilityRulesResponse {
  success: true
  rules: TenantAdminPublicBookingAvailabilityRule[]
}

export interface TenantAdminPublicBookingAvailabilityRuleMutation {
  ruleType: TenantAdminPublicBookingRuleType
  businessDate: string | null
  dayOfWeek: number | null
  periodKey: string | null
  quotaMode: 'percentage' | 'table_count' | 'guest_count' | 'closed'
  quotaPercent: number | null
  tableCount: number | null
  guestCount: number | null
}

export interface TenantAdminPublicBookingAvailabilityRuleResponse
  extends TenantAdminPublicBookingAvailabilityRule {
  success: true
}

export interface TenantAdminPublicBookingQuotaOverrideMutation {
  businessDate: string
  periodKey: string | null
  quotaMode: 'percentage' | 'table_count' | 'guest_count' | 'closed'
  quotaPercent: number | null
  tableCount: number | null
  guestCount: number | null
}

export interface TenantAdminPublicBookingQuotaOverrideResponse {
  success: true
  periodKey: string | null
  quotaMode: 'percentage' | 'table_count' | 'guest_count' | 'closed'
  quotaPercent: number | null
  tableCount: number | null
  guestCount: number | null
}
