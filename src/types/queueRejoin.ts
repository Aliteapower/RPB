export type QueueRejoinIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface QueueRejoinIdempotency {
  status: QueueRejoinIdempotencyStatus
  replayed?: boolean
}

export interface RejoinQueueTicketResponse {
  success: true
  queueTicketId: string
  queueTicketNumber: number
  queueTicketDisplayNumber?: string | null
  queueTicketStatus: string
  queuePosition?: number | null
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  rejoinedAt: string
  alreadyRejoined: boolean
  events: string[]
  idempotency: QueueRejoinIdempotency
}

export interface QueueRejoinApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueRejoinApiErrorResponse {
  success: false
  error: QueueRejoinApiErrorBody
  idempotency?: QueueRejoinIdempotency
}

export type QueueRejoinApiResponse =
  | RejoinQueueTicketResponse
  | QueueRejoinApiErrorResponse
