export type ReservationIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export interface CreateReservationRequest {
  partySize: number
  reservedStartAt: string
  reservedEndAt?: string | null
  customerId?: string | null
  customerName?: string | null
  customerNickname?: string | null
  phoneE164?: string | null
  note?: string | null
}

export interface ReservationCustomerProjection {
  id?: string | null
  displayName?: string | null
  phoneE164?: string | null
}

export interface ReservationIdempotencyResponse {
  status: ReservationIdempotencyStatus
  replayed?: boolean
}

export interface CreateReservationResponse {
  success: true
  reservationId: string
  reservationCode: string
  status: string
  partySize: number
  reservedStartAt: string
  reservedEndAt: string
  holdUntilAt: string
  businessDate: string
  customer?: ReservationCustomerProjection | null
  events: string[]
  idempotency: ReservationIdempotencyResponse
}

export interface ReservationApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface ReservationApiErrorResponse {
  success: false
  error: ReservationApiErrorBody
  idempotency?: {
    status: ReservationIdempotencyStatus
  }
}

export type ReservationCreateApiResponse =
  | CreateReservationResponse
  | ReservationApiErrorResponse
