import type {
  ApiErrorResponse,
  SeatWalkInDirectlyRequest,
  SeatWalkInDirectlyResponse
} from '../types/walkInDirectSeating'

export class WalkInDirectSeatingApiError extends Error {
  readonly status: number
  readonly response: ApiErrorResponse

  constructor(status: number, response: ApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'WalkInDirectSeatingApiError'
    this.status = status
    this.response = response
  }
}

export async function seatWalkInDirectly(
  storeId: string,
  request: SeatWalkInDirectlyRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SeatWalkInDirectlyResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/walk-ins/direct-seating`

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
    throw new WalkInDirectSeatingApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'walkin.direct_seating.network_failure',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isApiErrorResponse(payload)) {
    const apiError: ApiErrorResponse = isApiErrorResponse(payload)
      ? payload
      : {
          success: false,
          error: {
            code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            messageKey: 'walkin.direct_seating.request_failed',
            details: {
              httpStatus: response.status
            }
          },
          idempotency: {
            status: 'failed'
          }
        }

    throw new WalkInDirectSeatingApiError(response.status, apiError)
  }

  if (!isSeatWalkInDirectlyResponse(payload)) {
    throw new WalkInDirectSeatingApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'walkin.direct_seating.invalid_api_response',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toApiBody(request: SeatWalkInDirectlyRequest): SeatWalkInDirectlyRequest {
  return {
    partySize: request.partySize,
    customerId: request.customerId ?? null,
    customerName: request.customerName ?? null,
    customerNickname: request.customerNickname ?? null,
    phoneE164: request.phoneE164 ?? null,
    tableId: request.tableId ?? null,
    tableGroupId: request.tableGroupId ?? null,
    overrideReasonCode: request.overrideReasonCode ?? null,
    overrideNote: request.overrideNote ?? null
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

function isApiErrorResponse(payload: unknown): payload is ApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isSeatWalkInDirectlyResponse(payload: unknown): payload is SeatWalkInDirectlyResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SeatWalkInDirectlyResponse>
  return (
    candidate.success === true &&
    typeof candidate.walkInId === 'string' &&
    typeof candidate.seatingId === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.status === 'string' &&
    !!candidate.idempotency
  )
}
