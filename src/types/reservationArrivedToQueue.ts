export type ReservationArrivedToQueueIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface QueueArrivedReservationRequest {
  partySizeGroup?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface ReservationArrivedToQueueIdempotency {
  status: ReservationArrivedToQueueIdempotencyStatus
  replayed?: boolean
}

export interface QueueArrivedReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  reservationStatus: string
  queueTicketId: string
  queueTicketNumber: number
  queueTicketStatus: string
  queueGroupId: string
  queueGroupCode?: string | null
  partySize: number
  partySizeGroup: string
  businessDate: string
  queuePosition: number
  alreadyQueued: boolean
  events: string[]
  idempotency: ReservationArrivedToQueueIdempotency
}

export interface ReservationArrivedToQueueApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationArrivedToQueueApiErrorResponse {
  success: false
  error: ReservationArrivedToQueueApiErrorBody
  idempotency?: ReservationArrivedToQueueIdempotency
}

export type ReservationArrivedToQueueApiResponse =
  | QueueArrivedReservationResponse
  | ReservationArrivedToQueueApiErrorResponse
