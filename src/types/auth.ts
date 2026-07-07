export interface SliderCaptchaChallenge {
  challengeId: string
  type: 'image_slider'
  imageWidth: number
  imageHeight: number
  pieceSize: number
  pieceY: number
  expiresAt: string
  backgroundImage: string
  pieceImage: string
  hint: string
}

export interface SliderCaptchaResponse {
  success: true
  challenge: SliderCaptchaChallenge
}

export interface AuthUser {
  accountId: string
  tenantId: string | null
  username: string
  displayName: string
  actorType: string
  defaultStoreId: string | null
  storeIds: string[]
  roles: string[]
  permissions: string[]
}

export interface AuthStoreAccess {
  tenantId: string
  tenantCode: string
  operatingEntityId: string | null
  operatingEntityName: string | null
  storeId: string
  storeCode: string
  storeName: string
  status: string
  locale: string
  defaultStore: boolean
}

export interface AuthStoreAccessResponse {
  success: true
  stores: AuthStoreAccess[]
}

export type AuthLoginEntry = 'platform_admin' | 'tenant_admin' | 'staff'

export interface LoginRequest {
  username: string
  password: string
  captchaId: string
  captchaX: number
  loginEntry?: AuthLoginEntry
  tenantCode?: string | null
}

export interface AuthLoginResponse {
  success: true
  user: AuthUser
  expiresAt: string
}

export interface AuthMeResponse {
  success: true
  user: AuthUser
}

export interface AuthLogoutResponse {
  success: true
}

export interface AuthApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
