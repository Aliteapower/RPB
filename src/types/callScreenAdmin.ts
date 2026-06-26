export type CallScreenAdMode = 'text'

export type CallScreenStatus = 'active' | 'disabled'

export interface CallScreenSettings {
  activeAdSetId: string | null
  adMode: CallScreenAdMode
  status: CallScreenStatus
  slideDurationSeconds: number
  statePollSeconds: number
  showWaitingPreview: boolean
  version: number
}

export interface CallScreenTextSlide {
  id: string
  title: string
  subtitle: string
  tagline: string
  sortOrder: number
  status: CallScreenStatus
  version: number
}

export interface CallScreenAdSet {
  id: string
  name: string
  adType: CallScreenAdMode
  status: CallScreenStatus
  slides: CallScreenTextSlide[]
  version: number
}

export interface CallScreenSettingsResponse {
  success: true
  settings: CallScreenSettings
}

export interface CallScreenAdSetResponse {
  success: true
  adSet: CallScreenAdSet
}

export interface CallScreenAdSetListResponse {
  success: true
  adSets: CallScreenAdSet[]
}

export interface CallScreenSettingsMutation {
  activeAdSetId: string | null
  adMode: CallScreenAdMode
  status?: CallScreenStatus
  slideDurationSeconds: number
  statePollSeconds: number
  showWaitingPreview: boolean
  version?: number
}

export interface CallScreenTextSlideMutation {
  id?: string
  title: string
  subtitle: string
  tagline: string
  sortOrder: number
  status: CallScreenStatus
  version?: number
}

export interface CallScreenAdSetMutation {
  name: string
  adType: CallScreenAdMode
  status: CallScreenStatus
  slides: CallScreenTextSlideMutation[]
  version?: number
}

export interface CallScreenAdminApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
