import type {
  CompleteReservationRequest,
  CompleteReservationResponse,
  MarkReservationNoShowRequest,
  MarkReservationNoShowResponse,
  ReservationStatusActionApiErrorResponse
} from '../types/reservationStatusAction'

export class ReservationStatusActionApiError extends Error {
  readonly status: number
  readonly response: ReservationStatusActionApiErrorResponse

  constructor(status: number, response: ReservationStatusActionApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationStatusActionApiError'
    this.status = status
    this.response = response
  }
}

export async function markReservationNoShow(
  storeId: string,
  reservationId: string,
  request: MarkReservationNoShowRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<MarkReservationNoShowResponse> {
  return postStatusAction(
    storeId,
    reservationId,
    'no-show',
    toNoShowBody(request),
    idempotencyKey,
    isMarkReservationNoShowResponse,
    'reservation.no_show',
    fetcher
  )
}

export async function completeReservation(
  storeId: string,
  reservationId: string,
  request: CompleteReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CompleteReservationResponse> {
  return postStatusAction(
    storeId,
    reservationId,
    'complete',
    toCompleteBody(request),
    idempotencyKey,
    isCompleteReservationResponse,
    'reservation.complete',
    fetcher
  )
}

async function postStatusAction<T>(
  storeId: string,
  reservationId: string,
  action: string,
  body: MarkReservationNoShowRequest | CompleteReservationRequest,
  idempotencyKey: string,
  isSuccessResponse: (payload: unknown) => payload is T,
  messageKeyPrefix: string,
  fetcher: typeof fetch
): Promise<T> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/${action}`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify(body)
    })
  } catch {
    throw new ReservationStatusActionApiError(
      0,
      unknownError('NETWORK_FAILURE', `${messageKeyPrefix}.network_failure`)
    )
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationStatusActionApiErrorResponse(payload)) {
    const apiError: ReservationStatusActionApiErrorResponse =
      isReservationStatusActionApiErrorResponse(payload)
        ? payload
        : fallbackError(response.status, messageKeyPrefix)

    throw new ReservationStatusActionApiError(response.status, apiError)
  }

  if (!isSuccessResponse(payload)) {
    throw new ReservationStatusActionApiError(
      response.status,
      unknownError('INVALID_API_RESPONSE', `${messageKeyPrefix}.invalid_api_response`)
    )
  }

  return payload
}

function toNoShowBody(request: MarkReservationNoShowRequest): MarkReservationNoShowRequest {
  return {
    noShowAt: request.noShowAt ?? null,
    reasonCode: trimToNullable(request.reasonCode),
    note: trimToNullable(request.note)
  }
}

function toCompleteBody(request: CompleteReservationRequest): CompleteReservationRequest {
  return {
    completedAt: request.completedAt ?? null,
    reasonCode: trimToNullable(request.reasonCode),
    note: trimToNullable(request.note)
  }
}

function trimToNullable(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed ? trimmed : null
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text()

  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

function isReservationStatusActionApiErrorResponse(
  payload: unknown
): payload is ReservationStatusActionApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationStatusActionApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isMarkReservationNoShowResponse(
  payload: unknown
): payload is MarkReservationNoShowResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<MarkReservationNoShowResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    candidate.status === 'no_show' &&
    typeof candidate.noShowAt === 'string' &&
    typeof candidate.alreadyNoShow === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function isCompleteReservationResponse(
  payload: unknown
): payload is CompleteReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CompleteReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    candidate.status === 'completed' &&
    typeof candidate.completedAt === 'string' &&
    typeof candidate.alreadyCompleted === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(
  code: string,
  messageKey: string,
  httpStatus?: number
): ReservationStatusActionApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: httpStatus ? { httpStatus } : {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}

function fallbackError(
  httpStatus: number,
  messageKeyPrefix: string
): ReservationStatusActionApiErrorResponse {
  if (httpStatus === 403) {
    return unknownError('FORBIDDEN', 'reservation.forbidden', httpStatus)
  }

  return unknownError(
    httpStatus >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
    `${messageKeyPrefix}.request_failed`,
    httpStatus
  )
}
