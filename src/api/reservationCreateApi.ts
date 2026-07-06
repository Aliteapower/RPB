import type {
  CreateReservationRequest,
  CreateReservationResponse,
  ReservationApiErrorResponse
} from '../types/reservation'

export class ReservationCreateApiError extends Error {
  readonly status: number
  readonly response: ReservationApiErrorResponse

  constructor(status: number, response: ReservationApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationCreateApiError'
    this.status = status
    this.response = response
  }
}

export async function createReservation(
  storeId: string,
  request: CreateReservationRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CreateReservationResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify(toApiBody(request))
    })
  } catch {
    throw new ReservationCreateApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'reservation.network_failure',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isReservationApiErrorResponse(payload)) {
    const apiError: ReservationApiErrorResponse = isReservationApiErrorResponse(payload)
      ? payload
      : {
          success: false,
          error: {
            code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            messageKey: 'reservation.request_failed',
            details: {
              httpStatus: response.status
            }
          },
          idempotency: {
            status: 'failed'
          }
        }

    throw new ReservationCreateApiError(response.status, apiError)
  }

  if (!isCreateReservationResponse(payload)) {
    throw new ReservationCreateApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'reservation.invalid_api_response',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toApiBody(request: CreateReservationRequest): CreateReservationRequest {
  return {
    partySize: request.partySize,
    reservedStartAt: request.reservedStartAt,
    reservedEndAt: request.reservedEndAt ?? null,
    businessDate: request.businessDate ?? null,
    customerId: request.customerId ?? null,
    customerName: request.customerName ?? null,
    customerNickname: request.customerNickname ?? null,
    customerEmail: request.customerEmail ?? null,
    phoneE164: request.phoneE164 ?? null,
    note: request.note ?? null,
    tableId: request.tableId ?? null,
    tableGroupId: request.tableGroupId ?? null
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

function isReservationApiErrorResponse(payload: unknown): payload is ReservationApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isCreateReservationResponse(payload: unknown): payload is CreateReservationResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CreateReservationResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.reservedStartAt === 'string' &&
    typeof candidate.reservedEndAt === 'string' &&
    typeof candidate.holdUntilAt === 'string' &&
    typeof candidate.businessDate === 'string' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}
