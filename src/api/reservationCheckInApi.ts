import type {
  CheckInReservationRequest,
  CheckInReservationResponse,
  ReservationCheckInApiErrorResponse
} from '../types/reservationCheckIn'

export class ReservationCheckInApiError extends Error {
  readonly status: number
  readonly response: ReservationCheckInApiErrorResponse

  constructor(status: number, response: ReservationCheckInApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationCheckInApiError'
    this.status = status
    this.response = response
  }
}

export async function checkInReservation(
  storeId: string,
  reservationId: string,
  request: CheckInReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CheckInReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/check-in`

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
    throw new ReservationCheckInApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'reservation.check_in.network_failure',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationCheckInApiErrorResponse(payload)) {
    const apiError: ReservationCheckInApiErrorResponse = isReservationCheckInApiErrorResponse(payload)
      ? payload
      : {
          success: false,
          error: {
            code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            messageKey: 'reservation.check_in.request_failed',
            details: {
              httpStatus: response.status
            }
          },
          idempotency: {
            status: 'failed'
          }
        }

    throw new ReservationCheckInApiError(response.status, apiError)
  }

  if (!isCheckInReservationResponse(payload)) {
    throw new ReservationCheckInApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'reservation.check_in.invalid_api_response',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toApiBody(request: CheckInReservationRequest): CheckInReservationRequest {
  return {
    arrivedAt: request.arrivedAt ?? null,
    reasonCode: request.reasonCode ?? null,
    note: request.note ?? null
  }
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

function isReservationCheckInApiErrorResponse(
  payload: unknown
): payload is ReservationCheckInApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationCheckInApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isCheckInReservationResponse(payload: unknown): payload is CheckInReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CheckInReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.arrivedAt === 'string' &&
    typeof candidate.alreadyArrived === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}
