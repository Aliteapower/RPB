import type {
  ReservationPublicShareApiErrorResponse,
  ReservationPublicShareResponse
} from '../types/reservationPublicShare'
import { translate } from '../i18n'

export class ReservationPublicShareApiError extends Error {
  readonly status: number
  readonly response: ReservationPublicShareApiErrorResponse

  constructor(status: number, response: ReservationPublicShareApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationPublicShareApiError'
    this.status = status
    this.response = response
  }
}

export async function getReservationPublicShare(
  token: string,
  locale?: string,
  fetcher: typeof fetch = fetch
): Promise<ReservationPublicShareResponse> {
  const searchParams = new URLSearchParams()
  if (locale?.trim()) {
    searchParams.set('locale', locale.trim())
  }
  const query = searchParams.toString()
  const endpoint = `/api/v1/public/reservation-shares/${encodeURIComponent(token)}${query ? `?${query}` : ''}`
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new ReservationPublicShareApiError(
      0,
      localError('NETWORK_FAILURE', 'reservation.public_share.network_failure')
    )
  }

  const payload = await readJson(response)
  if (!response.ok || isReservationPublicShareApiErrorResponse(payload)) {
    throw new ReservationPublicShareApiError(
      response.status,
      isReservationPublicShareApiErrorResponse(payload)
        ? payload
        : localError('REQUEST_FAILED', 'reservation.public_share.request_failed')
    )
  }

  if (!isReservationPublicShareResponse(payload)) {
    throw new ReservationPublicShareApiError(
      response.status,
      localError('INVALID_API_RESPONSE', 'reservation.public_share.invalid_api_response')
    )
  }

  return payload
}

export function reservationPublicShareErrorMessage(code: string): string {
  switch (code) {
    case 'TOKEN_EXPIRED':
    case 'TOKEN_REVOKED':
      return translate('reservationPublicShare.errors.expired')
    case 'TOKEN_NOT_FOUND':
    case 'RESERVATION_NOT_FOUND':
      return translate('reservationPublicShare.errors.notFound')
    default:
      return translate('reservationPublicShare.errors.loadFailed')
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

function isReservationPublicShareApiErrorResponse(
  payload: unknown
): payload is ReservationPublicShareApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationPublicShareApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isReservationPublicShareResponse(
  payload: unknown
): payload is ReservationPublicShareResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationPublicShareResponse>
  return (
    candidate.success === true &&
    !!candidate.share &&
    typeof candidate.share.reservationNo === 'string' &&
    typeof candidate.share.storeName === 'string' &&
    typeof candidate.share.reservationDate === 'string' &&
    typeof candidate.share.reservationTime === 'string' &&
    typeof candidate.share.partySize === 'number' &&
    typeof candidate.share.tableCode === 'string' &&
    typeof candidate.share.tablePending === 'boolean' &&
    typeof candidate.share.shareTitle === 'string' &&
    typeof candidate.share.shareSummary === 'string'
  )
}

function localError(code: string, messageKey: string): ReservationPublicShareApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    }
  }
}
