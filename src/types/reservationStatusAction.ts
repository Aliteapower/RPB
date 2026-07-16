export type ReservationStatusActionIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface MarkReservationNoShowRequest {
  noShowAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface CompleteReservationRequest {
  completedAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface ReservationStatusActionIdempotency {
  status: ReservationStatusActionIdempotencyStatus
  replayed?: boolean
}

export interface MarkReservationNoShowResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  noShowAt: string
  noShowReasonCode?: string | null
  alreadyNoShow: boolean
  events: string[]
  idempotency: ReservationStatusActionIdempotency
}

export interface CompleteReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  completedAt: string
  seatingId?: string | null
  seatingStatus?: string | null
  alreadyCompleted: boolean
  events: string[]
  idempotency: ReservationStatusActionIdempotency
}

export interface ReservationStatusActionApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationStatusActionApiErrorResponse {
  success: false
  error: ReservationStatusActionApiErrorBody
  idempotency?: ReservationStatusActionIdempotency
}

export type ReservationStatusActionApiResponse =
  | MarkReservationNoShowResponse
  | CompleteReservationResponse
  | ReservationStatusActionApiErrorResponse
