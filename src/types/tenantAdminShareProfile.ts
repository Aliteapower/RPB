export interface TenantAdminShareProfile {
  storeDisplayName: string
  shareDisplayName: string
  shareAddress: string
  googleMapUrl: string
  shareContactPhone: string
  reservationShareNote: string
  reservationShareTemplate: string
  defaultReservationShareTemplate: string
  availableVariables: string[]
  usesDefaultReservationShareTemplate: boolean
}

export interface TenantAdminShareProfileMutation {
  shareDisplayName?: string | null
  shareAddress?: string | null
  googleMapUrl?: string | null
  shareContactPhone?: string | null
  reservationShareNote?: string | null
  reservationShareTemplate?: string | null
}

export interface TenantAdminShareProfileResponse {
  success: true
  shareProfile: TenantAdminShareProfile
}

export interface TenantAdminSharePreviewResponse {
  success: true
  preview: {
    shareText: string
  }
}
