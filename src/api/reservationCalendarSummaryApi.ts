import type {
  ReservationCalendarSummaryApiErrorResponse,
  ReservationCalendarSummaryResponse
} from '../types/reservationCalendarSummary'

export class ReservationCalendarSummaryApiError extends Error {
  readonly status: number
  readonly response: ReservationCalendarSummaryApiErrorResponse

  constructor(status: number, response: ReservationCalendarSummaryApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationCalendarSummaryApiError'
    this.status = status
    this.response = response
  }
}

export async function getReservationCalendarSummary(
  storeId: string,
  month: string,
  fetcher: typeof fetch = fetch
): Promise<ReservationCalendarSummaryResponse> {
  const endpoint = `/api/v1/stores/${storeId}/reservations/calendar-summary?month=${encodeURIComponent(month)}`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new ReservationCalendarSummaryApiError(
      0,
      errorResponse('NETWORK_FAILURE', 'reservation.calendar_summary.network_failure')
    )
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationCalendarSummaryApiErrorResponse(payload)) {
    const apiError: ReservationCalendarSummaryApiErrorResponse =
      isReservationCalendarSummaryApiErrorResponse(payload)
        ? payload
        : errorResponse(
            response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            'reservation.calendar_summary.request_failed',
            response.status
          )

    throw new ReservationCalendarSummaryApiError(response.status, apiError)
  }

  if (!isReservationCalendarSummaryResponse(payload)) {
    throw new ReservationCalendarSummaryApiError(
      response.status,
      errorResponse('INVALID_API_RESPONSE', 'reservation.calendar_summary.invalid_api_response')
    )
  }

  return payload
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

function isReservationCalendarSummaryApiErrorResponse(
  payload: unknown
): payload is ReservationCalendarSummaryApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationCalendarSummaryApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isReservationCalendarSummaryResponse(
  payload: unknown
): payload is ReservationCalendarSummaryResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationCalendarSummaryResponse>
  return (
    candidate.success === true &&
    typeof candidate.storeId === 'string' &&
    typeof candidate.month === 'string' &&
    typeof candidate.storeTimezone === 'string' &&
    Array.isArray(candidate.days) &&
    candidate.days.every(isReservationCalendarSummaryDay)
  )
}

function isReservationCalendarSummaryDay(payload: unknown): boolean {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationCalendarSummaryResponse['days'][number]>
  return typeof candidate.businessDate === 'string' && typeof candidate.reservationCount === 'number'
}

function errorResponse(
  code: string,
  messageKey: string,
  httpStatus?: number
): ReservationCalendarSummaryApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: httpStatus ? { httpStatus } : {}
    }
  }
}
