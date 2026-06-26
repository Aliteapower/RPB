export type QueueDisplayAdMode = 'text'

export interface QueueDisplayStoreTime {
  timezone: string
  timeText: string
  businessDate: string
}

export interface QueueDisplayCurrentCall {
  queueTicketId: string
  displayNumber: string
  customerDisplayName: string
  partySize: number
  partySizeGroup: string
  calledAt: string
  holdUntilAt: string
}

export interface QueueDisplayWaitingPreviewItem {
  displayNumber: string
  customerDisplayName: string
  partySize: number
  partySizeGroup: string
}

export interface QueueDisplayWaiting {
  count: number
  preview: QueueDisplayWaitingPreviewItem[]
}

export interface QueueDisplayTextAdSlide {
  slideId: string
  title: string
  subtitle: string
  tagline: string
}

export type QueueDisplayAdSlide = QueueDisplayTextAdSlide

export interface QueueDisplayAds {
  mode: QueueDisplayAdMode
  slideDurationSeconds: number
  statePollSeconds?: number
  slides: QueueDisplayAdSlide[]
}

export interface QueueDisplayStateResponse {
  success: true
  serverNow: string
  storeDisplayName?: string | null
  storeName?: string | null
  storeTime: QueueDisplayStoreTime
  currentCall: QueueDisplayCurrentCall | null
  waiting: QueueDisplayWaiting
  ads: QueueDisplayAds
}

export interface QueueDisplayApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueDisplayApiErrorResponse {
  success: false
  error: QueueDisplayApiErrorBody
}

export type QueueDisplayApiResponse =
  | QueueDisplayStateResponse
  | QueueDisplayApiErrorResponse
