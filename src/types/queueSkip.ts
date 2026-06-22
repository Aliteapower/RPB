export type QueueSkipIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface QueueSkipIdempotency {
  status: QueueSkipIdempotencyStatus
  replayed?: boolean
}

export interface SkipQueueTicketResponse {
  success: true
  queueTicketId: string
  queueTicketNumber: number
  queueTicketStatus: string
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  skippedAt: string
  alreadySkipped: boolean
  events: string[]
  idempotency: QueueSkipIdempotency
}

export interface QueueSkipApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueSkipApiErrorResponse {
  success: false
  error: QueueSkipApiErrorBody
  idempotency?: QueueSkipIdempotency
}

export type QueueSkipApiResponse =
  | SkipQueueTicketResponse
  | QueueSkipApiErrorResponse
