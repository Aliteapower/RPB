import type {
  ReservationTodayViewApiErrorResponse,
  ReservationTodayViewQuery,
  ReservationTodayViewResponse
} from '../types/reservationTodayView'

export class ReservationTodayViewApiError extends Error {
  readonly status: number
  readonly response: ReservationTodayViewApiErrorResponse

  constructor(status: number, response: ReservationTodayViewApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationTodayViewApiError'
    this.status = status
    this.response = response
  }
}

export async function getReservationTodayView(
  storeId: string,
  query: ReservationTodayViewQuery = {},
  fetcher: typeof fetch = fetch
): Promise<ReservationTodayViewResponse> {
  const endpoint = buildEndpoint(storeId, query)

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new ReservationTodayViewApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'reservation.today_view.network_failure',
        details: {}
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationTodayViewApiErrorResponse(payload)) {
    const apiError: ReservationTodayViewApiErrorResponse =
      isReservationTodayViewApiErrorResponse(payload)
        ? payload
        : {
            success: false,
            error: {
              code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
              messageKey: 'reservation.today_view.request_failed',
              details: {
                httpStatus: response.status
              }
            }
          }

    throw new ReservationTodayViewApiError(response.status, apiError)
  }

  if (!isReservationTodayViewResponse(payload)) {
    throw new ReservationTodayViewApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'reservation.today_view.invalid_api_response',
        details: {}
      }
    })
  }

  return payload
}

function buildEndpoint(storeId: string, query: ReservationTodayViewQuery): string {
  const params = new URLSearchParams()
  const businessDate = query.businessDate?.trim()
  const status = query.status?.trim() || 'operational'

  if (businessDate) {
    params.set('businessDate', businessDate)
  }

  if (status) {
    params.set('status', status)
  }

  const queryString = params.toString()
  const path = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/today`
  return queryString ? `${path}?${queryString}` : path
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

function isReservationTodayViewApiErrorResponse(
  payload: unknown
): payload is ReservationTodayViewApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationTodayViewApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isReservationTodayViewResponse(payload: unknown): payload is ReservationTodayViewResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationTodayViewResponse>
  return (
    candidate.success === true &&
    typeof candidate.storeId === 'string' &&
    typeof candidate.businessDate === 'string' &&
    typeof candidate.storeTimezone === 'string' &&
    typeof candidate.statusFilter === 'string' &&
    Array.isArray(candidate.items) &&
    candidate.items.every(isReservationTodayViewItem)
  )
}

function isReservationTodayViewItem(payload: unknown): boolean {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationTodayViewResponse['items'][number]>
  return (
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.reservedStartAt === 'string' &&
    typeof candidate.reservedEndAt === 'string' &&
    typeof candidate.holdUntilAt === 'string' &&
    typeof candidate.businessDate === 'string'
  )
}
