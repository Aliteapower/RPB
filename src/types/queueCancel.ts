export type QueueCancelIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface CancelQueueTicketRequest {
  cancelledAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface QueueCancelIdempotency {
  status: QueueCancelIdempotencyStatus
  replayed?: boolean
}

export interface CancelQueueTicketResponse {
  success: true
  queueTicketId: string
  queueTicketNumber: number
  queueTicketDisplayNumber?: string | null
  queueTicketStatus: string
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  walkInId?: string | null
  cancelledAt: string
  cancellationReasonCode?: string | null
  alreadyCancelled: boolean
  events: string[]
  idempotency: QueueCancelIdempotency
}

export interface QueueCancelApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueCancelApiErrorResponse {
  success: false
  error: QueueCancelApiErrorBody
  idempotency?: QueueCancelIdempotency
}

export type QueueCancelApiResponse =
  | CancelQueueTicketResponse
  | QueueCancelApiErrorResponse
