export type PlatformReservationShareTemplateSeedStatus = 'active' | 'disabled'

export interface PlatformReservationShareTemplateSeed {
  seedKey: string
  displayName: string
  locale: string
  templateText: string
  status: PlatformReservationShareTemplateSeedStatus
  version: number
  allowedVariables: string[]
}

export interface PlatformReservationShareTemplateSeedResponse {
  success: true
  seed: PlatformReservationShareTemplateSeed
}

export interface PlatformReservationShareTemplateSeedMutation {
  displayName: string
  locale: string
  templateText: string
  status: PlatformReservationShareTemplateSeedStatus
  version?: number
}

export interface PlatformReservationShareTemplateSeedApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
