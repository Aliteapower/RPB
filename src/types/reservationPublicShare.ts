export interface ReservationPublicShare {
  reservationNo: string
  storeName: string
  reservationDate: string
  reservationTime: string
  partySize: number
  tableCode: string
  tablePending: boolean
  arrivalNote: string
  storePhone: string
  storeAddress: string
  googleMapUrl: string
  shareTitle: string
  shareSummary: string
  shareText: string
}

export interface ReservationPublicShareResponse {
  success: true
  share: ReservationPublicShare
}

export interface ReservationPublicShareApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
