export interface ReservationShareInfo {
  reservationId: string
  reservationNo: string
  channel: 'manual_copy' | (string & {})
  shareText: string
  customerMaskedPhone: string
  customerPhoneAvailable: boolean
  senderLabel: string
  canOpenWhatsAppLink: boolean
  whatsappLink?: string | null
  canOpenWechatLink: boolean
  wechatLink?: string | null
  wechatShareText?: string | null
  shareToken: string
  sharePath: string
  shareTitle: string
  shareSummary: string
}

export interface ReservationShareInfoResponse {
  success: true
  shareInfo: ReservationShareInfo
}

export type ReservationShareIntentChannel = 'whatsapp' | 'wechat' | 'system_share' | 'copy_link'

export interface ReservationShareIntentResponse {
  success: true
  channel: ReservationShareIntentChannel
}

export interface ReservationShareInfoApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
