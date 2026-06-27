import type {
  ReservationShareInfoApiErrorResponse,
  ReservationShareInfoResponse
} from '../types/reservationShareInfo'

export class ReservationShareInfoApiError extends Error {
  readonly status: number
  readonly response: ReservationShareInfoApiErrorResponse

  constructor(status: number, response: ReservationShareInfoApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationShareInfoApiError'
    this.status = status
    this.response = response
  }
}

export async function getReservationShareInfo(
  storeId: string,
  reservationId: string,
  fetcher: typeof fetch = fetch
): Promise<ReservationShareInfoResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/share-info`
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new ReservationShareInfoApiError(0, localError('NETWORK_FAILURE', 'reservation.share_info.network_failure'))
  }

  const payload = await readJson(response)
  if (!response.ok || isReservationShareInfoApiErrorResponse(payload)) {
    throw new ReservationShareInfoApiError(
      response.status,
      isReservationShareInfoApiErrorResponse(payload)
        ? payload
        : localError('REQUEST_FAILED', 'reservation.share_info.request_failed')
    )
  }

  if (!isReservationShareInfoResponse(payload)) {
    throw new ReservationShareInfoApiError(
      response.status,
      localError('INVALID_API_RESPONSE', 'reservation.share_info.invalid_api_response')
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

function isReservationShareInfoApiErrorResponse(
  payload: unknown
): payload is ReservationShareInfoApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationShareInfoApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isReservationShareInfoResponse(payload: unknown): payload is ReservationShareInfoResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationShareInfoResponse>
  return (
    candidate.success === true &&
    !!candidate.shareInfo &&
    typeof candidate.shareInfo.reservationId === 'string' &&
    typeof candidate.shareInfo.reservationNo === 'string' &&
    typeof candidate.shareInfo.channel === 'string' &&
    typeof candidate.shareInfo.shareText === 'string'
  )
}

function localError(code: string, messageKey: string): ReservationShareInfoApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}
