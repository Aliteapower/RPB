export type ReservationCheckInIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface CheckInReservationRequest {
  arrivedAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface ReservationCheckInIdempotency {
  status: ReservationCheckInIdempotencyStatus
  replayed?: boolean
}

export interface CheckInReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  arrivedAt: string
  alreadyArrived: boolean
  events: string[]
  idempotency: ReservationCheckInIdempotency
}

export interface ReservationCheckInApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationCheckInApiErrorResponse {
  success: false
  error: ReservationCheckInApiErrorBody
  idempotency?: ReservationCheckInIdempotency
}

export type ReservationCheckInApiResponse =
  | CheckInReservationResponse
  | ReservationCheckInApiErrorResponse
