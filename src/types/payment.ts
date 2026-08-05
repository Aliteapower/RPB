export type PaymentMethod = 'paynow'
export type PaymentProfileStatus = 'active' | 'disabled'
export type PayNowType = 'mobile' | 'uen'
export type PaymentIntentSourceType = 'quick_pay' | 'reservation' | 'queue' | 'pos'

export interface PaymentProfile {
  id: string
  tenantId: string
  storeId: string
  method: PaymentMethod
  status: PaymentProfileStatus
  paynowType: PayNowType
  paynowMobile: string | null
  paynowUen: string | null
  merchantName: string
  currency: string
  configJson: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface PaymentProfileMutation {
  method: PaymentMethod
  status: PaymentProfileStatus
  paynowType: PayNowType
  paynowMobile?: string | null
  paynowUen?: string | null
  merchantName: string
  currency: string
  configJson: string
  version?: number | null
}

export interface PaymentIntent {
  id: string
  tenantId: string
  storeId: string
  intentNo: string
  sourceType: PaymentIntentSourceType
  sourceId: string | null
  method: PaymentMethod
  amount: string
  currency: string
  status: string
  terminalCode: string | null
  cashierName: string | null
  metadataJson: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface PaymentSession {
  id: string
  tenantId: string
  storeId: string
  intentId: string
  sessionNo: string
  displayNumber: number
  businessDate: string
  status: string
  qrPayloadsJson: string
  expiresAt: string
  version: number
}

export interface PaymentProfileResponse {
  success: true
  profile: PaymentProfile
}

export interface PaymentIntentCreateRequest {
  idempotencyKey: string
  sourceType: PaymentIntentSourceType
  sourceId?: string | null
  method: PaymentMethod
  amount: string
  currency: string
  terminalCode?: string | null
  cashierName?: string | null
  requestedDisplayNumber?: number | null
  metadataJson?: string | null
}

export interface PaymentIntentCreateResponse {
  success: true
  replayed: boolean
  intent: PaymentIntent
  session: PaymentSession
  nextDisplayNumber: number
}

export interface PaymentApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
