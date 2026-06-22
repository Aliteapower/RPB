import type {
  QueueArrivedReservationRequest,
  QueueArrivedReservationResponse,
  ReservationArrivedToQueueApiErrorResponse
} from '../types/reservationArrivedToQueue'

export class ReservationArrivedToQueueApiError extends Error {
  readonly status: number
  readonly response: ReservationArrivedToQueueApiErrorResponse

  constructor(status: number, response: ReservationArrivedToQueueApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationArrivedToQueueApiError'
    this.status = status
    this.response = response
  }
}

export async function queueArrivedReservation(
  storeId: string,
  reservationId: string,
  request: QueueArrivedReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<QueueArrivedReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/queue`

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
    throw new ReservationArrivedToQueueApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationArrivedToQueueApiErrorResponse(payload)) {
    const apiError: ReservationArrivedToQueueApiErrorResponse =
      isReservationArrivedToQueueApiErrorResponse(payload)
        ? payload
        : unknownError(response.status)

    throw new ReservationArrivedToQueueApiError(response.status, apiError)
  }

  if (!isQueueArrivedReservationResponse(payload)) {
    throw new ReservationArrivedToQueueApiError(response.status, unknownError())
  }

  return payload
}

function toApiBody(request: QueueArrivedReservationRequest): QueueArrivedReservationRequest {
  const body: QueueArrivedReservationRequest = {}

  addOptionalField(body, 'partySizeGroup', request.partySizeGroup)
  addOptionalField(body, 'reasonCode', request.reasonCode)
  addOptionalField(body, 'note', request.note)

  return body
}

function addOptionalField(
  body: QueueArrivedReservationRequest,
  key: keyof QueueArrivedReservationRequest,
  value: string | null | undefined
): void {
  const trimmed = trimToOptional(value)

  if (trimmed) {
    body[key] = trimmed
  }
}

function trimToOptional(value: string | null | undefined): string | null {
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

function isReservationArrivedToQueueApiErrorResponse(
  payload: unknown
): payload is ReservationArrivedToQueueApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationArrivedToQueueApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isQueueArrivedReservationResponse(
  payload: unknown
): payload is QueueArrivedReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueArrivedReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.reservationStatus === 'string' &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.queueGroupId === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.partySizeGroup === 'string' &&
    typeof candidate.alreadyQueued === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): ReservationArrivedToQueueApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'reservation.queue.unknown_error',
      details: httpStatus ? { httpStatus } : {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}
