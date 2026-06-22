import type {
  CallQueueTicketRequest,
  CallQueueTicketResponse,
  QueueCallApiErrorResponse
} from '../types/queueCall'

export class QueueCallApiError extends Error {
  readonly status: number
  readonly response: QueueCallApiErrorResponse

  constructor(status: number, response: QueueCallApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueCallApiError'
    this.status = status
    this.response = response
  }
}

export async function callQueueTicket(
  storeId: string,
  queueTicketId: string,
  request: CallQueueTicketRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CallQueueTicketResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/call`

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
    throw new QueueCallApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueCallApiErrorResponse(payload)) {
    const apiError: QueueCallApiErrorResponse = isQueueCallApiErrorResponse(payload)
      ? payload
      : unknownError(response.status)

    throw new QueueCallApiError(response.status, apiError)
  }

  if (!isCallQueueTicketResponse(payload)) {
    throw new QueueCallApiError(response.status, unknownError())
  }

  return payload
}

function toApiBody(request: CallQueueTicketRequest): CallQueueTicketRequest {
  const body: CallQueueTicketRequest = {}

  addOptionalField(body, 'calledAt', request.calledAt)
  addOptionalField(body, 'reasonCode', request.reasonCode)
  addOptionalField(body, 'note', request.note)

  return body
}

function addOptionalField(
  body: CallQueueTicketRequest,
  key: keyof CallQueueTicketRequest,
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

function isQueueCallApiErrorResponse(
  payload: unknown
): payload is QueueCallApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueCallApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isCallQueueTicketResponse(
  payload: unknown
): payload is CallQueueTicketResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CallQueueTicketResponse>
  return (
    candidate.success === true &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.calledAt === 'string' &&
    typeof candidate.holdUntilAt === 'string' &&
    typeof candidate.alreadyCalled === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): QueueCallApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.call.unknown_error',
      details: httpStatus ? { httpStatus } : {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}
