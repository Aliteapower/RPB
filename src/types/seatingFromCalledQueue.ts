export type SeatingFromCalledQueueIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface SeatCalledQueueTicketRequest {
  tableId?: string | null
  tableGroupId?: string | null
  temporaryTableIds?: string[] | null
  overrideReasonCode?: string | null
  overrideNote?: string | null
  note?: string | null
}

export interface SeatingFromCalledQueueIdempotency {
  status: SeatingFromCalledQueueIdempotencyStatus
  replayed?: boolean
}

export interface SeatCalledQueueTicketResponse {
  success: true
  queueTicketId: string
  queueTicketNumber: number
  queueTicketStatus: string
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  seatingId: string
  seatingStatus: string
  resourceType: string
  resourceId: string
  alreadySeated: boolean
  events: string[]
  idempotency: SeatingFromCalledQueueIdempotency
}

export interface SeatingFromCalledQueueApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface SeatingFromCalledQueueApiErrorResponse {
  success: false
  error: SeatingFromCalledQueueApiErrorBody
  idempotency?: SeatingFromCalledQueueIdempotency
}

export type SeatingFromCalledQueueApiResponse =
  | SeatCalledQueueTicketResponse
  | SeatingFromCalledQueueApiErrorResponse
