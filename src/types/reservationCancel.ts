export type ReservationCancelIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface CancelReservationRequest {
  cancelledAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface ReservationCancelIdempotency {
  status: ReservationCancelIdempotencyStatus
  replayed?: boolean
}

export interface CancelReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  cancelledAt: string
  cancellationReasonCode?: string | null
  alreadyCancelled: boolean
  events: string[]
  idempotency: ReservationCancelIdempotency
}

export interface ReservationCancelApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationCancelApiErrorResponse {
  success: false
  error: ReservationCancelApiErrorBody
  idempotency?: ReservationCancelIdempotency
}

export type ReservationCancelApiResponse =
  | CancelReservationResponse
  | ReservationCancelApiErrorResponse
