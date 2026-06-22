export type ReservationArrivedDirectSeatingIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface SeatArrivedReservationRequest {
  tableId?: string | null
  tableGroupId?: string | null
  overrideReasonCode?: string | null
  overrideNote?: string | null
  note?: string | null
}

export interface ReservationArrivedDirectSeatingIdempotency {
  status: ReservationArrivedDirectSeatingIdempotencyStatus
  replayed?: boolean
}

export interface SeatArrivedReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  reservationStatus: string
  seatingId: string
  seatingStatus: string
  resourceType: string
  resourceId: string
  alreadySeated: boolean
  events: string[]
  idempotency: ReservationArrivedDirectSeatingIdempotency
}

export interface ReservationArrivedDirectSeatingApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationArrivedDirectSeatingApiErrorResponse {
  success: false
  error: ReservationArrivedDirectSeatingApiErrorBody
  idempotency?: ReservationArrivedDirectSeatingIdempotency
}

export type ReservationArrivedDirectSeatingApiResponse =
  | SeatArrivedReservationResponse
  | ReservationArrivedDirectSeatingApiErrorResponse
