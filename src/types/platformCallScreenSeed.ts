export type PlatformCallScreenSeedStatus = 'active' | 'disabled'

export type PlatformCallScreenMediaKind = 'image' | 'video'

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

export interface PlatformCallScreenMediaSeedSlide {
  id: string | null
  mediaAssetId: string
  mediaKind: PlatformCallScreenMediaKind
  mediaUrl: string
  title: string | null
  altText: string | null
  sortOrder: number
  status: PlatformCallScreenSeedStatus
  version: number
}

export interface PlatformCallScreenMediaSeedSet {
  id: string
  seedKey: string
  displayName: string
  adType: 'media'
  status: PlatformCallScreenSeedStatus
  mediaSlides: PlatformCallScreenMediaSeedSlide[]
  version: number
}

export interface PlatformCallScreenMediaAsset {
  id: string
  mediaKind: PlatformCallScreenMediaKind
  contentType: string
  byteSize: number
  originalFilename: string
  mediaUrl: string
  version: number
}

export interface PlatformCallScreenSeedResponse {
  success: true
  seedSet: PlatformCallScreenSeedSet
}

export interface PlatformCallScreenMediaSeedResponse {
  success: true
  seedSet: PlatformCallScreenMediaSeedSet
}

export interface PlatformCallScreenMediaAssetResponse {
  success: true
  media: PlatformCallScreenMediaAsset
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

export interface PlatformCallScreenMediaSeedSlideMutation {
  id?: string | null
  mediaAssetId: string
  mediaKind: PlatformCallScreenMediaKind
  title?: string | null
  altText?: string | null
  sortOrder: number
  status: PlatformCallScreenSeedStatus
  version?: number
}

export interface PlatformCallScreenMediaSeedMutation {
  displayName: string
  status: PlatformCallScreenSeedStatus
  mediaSlides: PlatformCallScreenMediaSeedSlideMutation[]
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
