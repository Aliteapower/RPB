import type {
  ReservationArrivedDirectSeatingApiErrorResponse,
  SeatArrivedReservationRequest,
  SeatArrivedReservationResponse
} from '../types/reservationArrivedDirectSeating'

export class ReservationArrivedDirectSeatingApiError extends Error {
  readonly status: number
  readonly response: ReservationArrivedDirectSeatingApiErrorResponse

  constructor(status: number, response: ReservationArrivedDirectSeatingApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationArrivedDirectSeatingApiError'
    this.status = status
    this.response = response
  }
}

export async function seatArrivedReservation(
  storeId: string,
  reservationId: string,
  request: SeatArrivedReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SeatArrivedReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/seating/direct`

  return submitReservationSeating(endpoint, request, idempotencyKey, fetcher)
}

export async function checkInAndSeatConfirmedReservation(
  storeId: string,
  reservationId: string,
  request: SeatArrivedReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SeatArrivedReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/seating/check-in-direct`

  return submitReservationSeating(endpoint, request, idempotencyKey, fetcher)
}

async function submitReservationSeating(
  endpoint: string,
  request: SeatArrivedReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch
): Promise<SeatArrivedReservationResponse> {
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
    throw new ReservationArrivedDirectSeatingApiError(0, {
      success: false,
      error: {
        code: 'UNKNOWN_ERROR',
        messageKey: 'reservation.direct_seating.unknown_error',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationArrivedDirectSeatingApiErrorResponse(payload)) {
    const apiError: ReservationArrivedDirectSeatingApiErrorResponse =
      isReservationArrivedDirectSeatingApiErrorResponse(payload)
        ? payload
        : {
            success: false,
            error: {
              code: 'UNKNOWN_ERROR',
              messageKey: 'reservation.direct_seating.unknown_error',
              details: {
                httpStatus: response.status
              }
            },
            idempotency: {
              status: 'failed'
            }
          }

    throw new ReservationArrivedDirectSeatingApiError(response.status, apiError)
  }

  if (!isSeatArrivedReservationResponse(payload)) {
    throw new ReservationArrivedDirectSeatingApiError(response.status, {
      success: false,
      error: {
        code: 'UNKNOWN_ERROR',
        messageKey: 'reservation.direct_seating.unknown_error',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toApiBody(request: SeatArrivedReservationRequest): SeatArrivedReservationRequest {
  return {
    tableId: request.tableId ?? null,
    tableGroupId: request.tableGroupId ?? null,
    temporaryTableIds: temporaryTableIdsOrNull(request.temporaryTableIds),
    overrideReasonCode: request.overrideReasonCode ?? null,
    overrideNote: request.overrideNote ?? null,
    note: request.note ?? null
  }
}

function temporaryTableIdsOrNull(value: string[] | null | undefined): string[] | null {
  const ids = value?.map(item => item.trim()).filter(Boolean) ?? []
  return ids.length ? ids : null
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

function isReservationArrivedDirectSeatingApiErrorResponse(
  payload: unknown
): payload is ReservationArrivedDirectSeatingApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationArrivedDirectSeatingApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isSeatArrivedReservationResponse(
  payload: unknown
): payload is SeatArrivedReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SeatArrivedReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.reservationStatus === 'string' &&
    typeof candidate.seatingId === 'string' &&
    typeof candidate.seatingStatus === 'string' &&
    typeof candidate.resourceType === 'string' &&
    typeof candidate.resourceId === 'string' &&
    typeof candidate.alreadySeated === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}
