export type WalkInQueueIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface QueueWalkInRequest {
  partySize: number
  customerId?: string | null
  customerName?: string | null
  customerNickname?: string | null
  phoneE164?: string | null
  note?: string | null
}

export interface WalkInQueueIdempotency {
  status: WalkInQueueIdempotencyStatus
  replayed?: boolean
}

export interface QueueWalkInResponse {
  success: true
  walkInId: string
  queueTicketId: string
  queueTicketNumber: number
  queueTicketStatus: string
  partySize: number
  partySizeGroup: string
  businessDate: string
  queuePosition?: number | null
  alreadyQueued: boolean
  events: string[]
  idempotency: WalkInQueueIdempotency
}

export interface WalkInQueueApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface WalkInQueueApiErrorResponse {
  success: false
  error: WalkInQueueApiErrorBody
  idempotency?: WalkInQueueIdempotency
}

export type WalkInQueueApiResponse = QueueWalkInResponse | WalkInQueueApiErrorResponse
