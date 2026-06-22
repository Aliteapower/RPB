export type ReservationTodayViewStatusFilter =
  | 'operational'
  | 'all'
  | 'confirmed'
  | 'arrived'
  | 'seated'
  | 'cancelled'
  | 'no_show'
  | 'completed'
  | (string & {})

export interface ReservationTodayViewQuery {
  businessDate?: string
  status?: ReservationTodayViewStatusFilter
}

export interface ReservationTodayViewItem {
  reservationId: string
  reservationCode: string
  status: string
  partySize: number
  reservedStartAt: string
  reservedEndAt: string
  holdUntilAt: string
  businessDate: string
  customerName?: string | null
  customerNickname?: string | null
  phoneMasked?: string | null
  note?: string | null
}

export interface ReservationTodayViewResponse {
  success: true
  storeId: string
  businessDate: string
  storeTimezone: string
  statusFilter: ReservationTodayViewStatusFilter
  items: ReservationTodayViewItem[]
}

export interface ReservationTodayViewApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationTodayViewApiErrorResponse {
  success: false
  error: ReservationTodayViewApiErrorBody
}

export type ReservationTodayViewApiResponse =
  | ReservationTodayViewResponse
  | ReservationTodayViewApiErrorResponse
