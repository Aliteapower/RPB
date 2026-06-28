export interface ReservationShareInfo {
  reservationId: string
  reservationNo: string
  channel: 'manual_copy' | (string & {})
  shareText: string
  customerMaskedPhone: string
  customerPhoneAvailable: boolean
  canOpenWhatsAppLink: boolean
  whatsappLink?: string | null
  shareToken: string
  sharePath: string
  shareTitle: string
  shareSummary: string
}

export interface ReservationShareInfoResponse {
  success: true
  shareInfo: ReservationShareInfo
}

export interface ReservationShareInfoApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
