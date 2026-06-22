export type SeatingResourceType = 'TABLE' | 'TABLE_GROUP'

export type ApiIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface SeatWalkInDirectlyRequest {
  partySize: number
  customerId?: string | null
  customerName?: string | null
  customerNickname?: string | null
  phoneE164?: string | null
  tableId?: string | null
  tableGroupId?: string | null
  overrideReasonCode?: string | null
  overrideNote?: string | null
}

export interface SeatingResourceResponse {
  type: SeatingResourceType
  id: string
  label?: string | null
}

export interface ApiIdempotencyResponse {
  status: ApiIdempotencyStatus
  replayed?: boolean
}

export interface SeatWalkInDirectlyResponse {
  success: true
  walkInId: string
  seatingId: string
  resource: SeatingResourceResponse
  partySize: number
  status: string
  events: string[]
  idempotency: ApiIdempotencyResponse
}

export interface ApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ApiErrorResponse {
  success: false
  error: ApiErrorBody
  idempotency?: {
    status: ApiIdempotencyStatus
  }
}

export type WalkInDirectSeatingApiResponse = SeatWalkInDirectlyResponse | ApiErrorResponse
