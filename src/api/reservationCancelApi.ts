import type {
  CancelReservationRequest,
  CancelReservationResponse,
  ReservationCancelApiErrorResponse
} from '../types/reservationCancel'

export class ReservationCancelApiError extends Error {
  readonly status: number
  readonly response: ReservationCancelApiErrorResponse

  constructor(status: number, response: ReservationCancelApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationCancelApiError'
    this.status = status
    this.response = response
  }
}

export async function cancelReservation(
  storeId: string,
  reservationId: string,
  request: CancelReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CancelReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/cancel`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify(toApiBody(request))
    })
  } catch {
    throw new ReservationCancelApiError(0, unknownError('NETWORK_FAILURE', 'reservation.cancel.network_failure'))
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationCancelApiErrorResponse(payload)) {
    const apiError: ReservationCancelApiErrorResponse = isReservationCancelApiErrorResponse(payload)
      ? payload
      : fallbackError(response.status)

    throw new ReservationCancelApiError(response.status, apiError)
  }

  if (!isCancelReservationResponse(payload)) {
    throw new ReservationCancelApiError(
      response.status,
      unknownError('INVALID_API_RESPONSE', 'reservation.cancel.invalid_api_response')
    )
  }

  return payload
}

function toApiBody(request: CancelReservationRequest): CancelReservationRequest {
  return {
    cancelledAt: request.cancelledAt ?? null,
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

function isReservationCancelApiErrorResponse(
  payload: unknown
): payload is ReservationCancelApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationCancelApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isCancelReservationResponse(payload: unknown): payload is CancelReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CancelReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.cancelledAt === 'string' &&
    typeof candidate.alreadyCancelled === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(
  code: string,
  messageKey: string,
  httpStatus?: number
): ReservationCancelApiErrorResponse {
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

function fallbackError(httpStatus: number): ReservationCancelApiErrorResponse {
  if (httpStatus === 403) {
    return unknownError('FORBIDDEN', 'reservation.forbidden', httpStatus)
  }

  return unknownError(
    httpStatus >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
    'reservation.cancel.request_failed',
    httpStatus
  )
}
