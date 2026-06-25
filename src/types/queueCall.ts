export type QueueCallIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface CallQueueTicketRequest {
  calledAt?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface QueueCallIdempotency {
  status: QueueCallIdempotencyStatus
  replayed?: boolean
}

export interface CallQueueTicketResponse {
  success: true
  queueTicketId: string
  queueTicketNumber: number
  queueTicketDisplayNumber?: string | null
  queueTicketStatus: string
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  calledAt: string
  holdUntilAt: string
  alreadyCalled: boolean
  events: string[]
  idempotency: QueueCallIdempotency
}

export interface QueueCallApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueCallApiErrorResponse {
  success: false
  error: QueueCallApiErrorBody
  idempotency?: QueueCallIdempotency
}

export type QueueCallApiResponse =
  | CallQueueTicketResponse
  | QueueCallApiErrorResponse
