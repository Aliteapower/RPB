import type {
  CleaningApiErrorResponse,
  CompleteCleaningRequest,
  CompleteCleaningResponse,
  StartCleaningRequest,
  StartCleaningResponse
} from '../types/cleaning'

export class CleaningApiError extends Error {
  readonly status: number
  readonly response: CleaningApiErrorResponse

  constructor(status: number, response: CleaningApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'CleaningApiError'
    this.status = status
    this.response = response
  }
}

export async function startCleaning(
  storeId: string,
  seatingId: string,
  request: StartCleaningRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<StartCleaningResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/seatings/${encodeURIComponent(
    seatingId
  )}/cleaning/start`

  return postCleaning<StartCleaningResponse>(endpoint, toBody(request), idempotencyKey, isStartCleaningResponse, fetcher)
}

export async function completeCleaning(
  storeId: string,
  cleaningId: string,
  request: CompleteCleaningRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CompleteCleaningResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/cleanings/${encodeURIComponent(
    cleaningId
  )}/complete`

  return postCleaning<CompleteCleaningResponse>(
    endpoint,
    toBody(request),
    idempotencyKey,
    isCompleteCleaningResponse,
    fetcher
  )
}

async function postCleaning<T>(
  endpoint: string,
  body: StartCleaningRequest | CompleteCleaningRequest,
  idempotencyKey: string,
  validator: (payload: unknown) => payload is T,
  fetcher: typeof fetch
): Promise<T> {
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify(body)
    })
  } catch {
    throw new CleaningApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'cleaning.network_failure',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isCleaningApiErrorResponse(payload)) {
    const apiError: CleaningApiErrorResponse = isCleaningApiErrorResponse(payload)
      ? payload
      : {
          success: false,
          error: {
            code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            messageKey: 'cleaning.request_failed',
            details: {
              httpStatus: response.status
            }
          },
          idempotency: {
            status: 'failed'
          }
        }

    throw new CleaningApiError(response.status, apiError)
  }

  if (!validator(payload)) {
    throw new CleaningApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'cleaning.invalid_api_response',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toBody(request: StartCleaningRequest | CompleteCleaningRequest): StartCleaningRequest {
  return {
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

function isCleaningApiErrorResponse(payload: unknown): payload is CleaningApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CleaningApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isStartCleaningResponse(payload: unknown): payload is StartCleaningResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<StartCleaningResponse>
  return (
    candidate.success === true &&
    typeof candidate.cleaningId === 'string' &&
    typeof candidate.seatingId === 'string' &&
    typeof candidate.cleaningStatus === 'string' &&
    typeof candidate.tableStatus === 'string' &&
    !!candidate.resource &&
    !!candidate.idempotency
  )
}

function isCompleteCleaningResponse(payload: unknown): payload is CompleteCleaningResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CompleteCleaningResponse>
  return (
    candidate.success === true &&
    typeof candidate.cleaningId === 'string' &&
    typeof candidate.cleaningStatus === 'string' &&
    typeof candidate.tableStatus === 'string' &&
    !!candidate.resource &&
    !!candidate.idempotency
  )
}
