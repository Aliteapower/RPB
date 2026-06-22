export type CleaningResourceType = 'TABLE' | 'TABLE_GROUP'

export type CleaningIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface StartCleaningRequest {
  reasonCode?: string | null
  note?: string | null
}

export interface CompleteCleaningRequest {
  reasonCode?: string | null
  note?: string | null
}

export interface CleaningResourceResponse {
  type: CleaningResourceType
  id: string
  label?: string | null
}

export interface CleaningIdempotencyResponse {
  status: CleaningIdempotencyStatus
  replayed?: boolean
}

export interface StartCleaningResponse {
  success: true
  cleaningId: string
  seatingId: string
  resource: CleaningResourceResponse
  cleaningStatus: string
  tableStatus: string
  events: string[]
  idempotency: CleaningIdempotencyResponse
}

export interface CompleteCleaningResponse {
  success: true
  cleaningId: string
  resource: CleaningResourceResponse
  cleaningStatus: string
  tableStatus: string
  events: string[]
  idempotency: CleaningIdempotencyResponse
}

export interface CleaningApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface CleaningApiErrorResponse {
  success: false
  error: CleaningApiErrorBody
  idempotency?: {
    status: CleaningIdempotencyStatus
  }
}

export type CleaningApiResponse =
  | StartCleaningResponse
  | CompleteCleaningResponse
  | CleaningApiErrorResponse
