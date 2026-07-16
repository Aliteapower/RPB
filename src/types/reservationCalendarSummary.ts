export interface ReservationCalendarSummaryDay {
  businessDate: string
  reservationCount: number
}

export interface ReservationCalendarSummaryResponse {
  success: true
  storeId: string
  month: string
  storeTimezone: string
  days: ReservationCalendarSummaryDay[]
}

export interface ReservationCalendarSummaryApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationCalendarSummaryApiErrorResponse {
  success: false
  error: ReservationCalendarSummaryApiErrorBody
}

export type ReservationCalendarSummaryApiResponse =
  | ReservationCalendarSummaryResponse
  | ReservationCalendarSummaryApiErrorResponse
