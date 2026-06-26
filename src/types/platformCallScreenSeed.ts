export type PlatformCallScreenSeedStatus = 'active' | 'disabled'

export interface PlatformCallScreenSeedSlide {
  id: string | null
  title: string
  subtitle: string
  tagline: string
  sortOrder: number
  status: PlatformCallScreenSeedStatus
  version: number
}

export interface PlatformCallScreenSeedSet {
  id: string
  seedKey: string
  displayName: string
  adType: 'text'
  status: PlatformCallScreenSeedStatus
  slides: PlatformCallScreenSeedSlide[]
  version: number
}

export interface PlatformCallScreenSeedResponse {
  success: true
  seedSet: PlatformCallScreenSeedSet
}

export interface PlatformCallScreenSeedSlideMutation {
  id?: string | null
  title: string
  subtitle: string
  tagline: string
  sortOrder: number
  status: PlatformCallScreenSeedStatus
  version?: number
}

export interface PlatformCallScreenSeedMutation {
  displayName: string
  status: PlatformCallScreenSeedStatus
  slides: PlatformCallScreenSeedSlideMutation[]
  version?: number
}

export interface PlatformCallScreenSeedApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
