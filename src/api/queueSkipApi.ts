import type {
  QueueSkipApiErrorResponse,
  SkipQueueTicketResponse
} from '../types/queueSkip'

export class QueueSkipApiError extends Error {
  readonly status: number
  readonly response: QueueSkipApiErrorResponse

  constructor(status: number, response: QueueSkipApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueSkipApiError'
    this.status = status
    this.response = response
  }
}

export async function skipQueueTicket(
  storeId: string,
  queueTicketId: string,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SkipQueueTicketResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/skip`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({})
    })
  } catch {
    throw new QueueSkipApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueSkipApiErrorResponse(payload)) {
    const apiError: QueueSkipApiErrorResponse = isQueueSkipApiErrorResponse(payload)
      ? payload
      : unknownError(response.status)

    throw new QueueSkipApiError(response.status, apiError)
  }

  if (!isSkipQueueTicketResponse(payload)) {
    throw new QueueSkipApiError(response.status, unknownError(response.status))
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

function isQueueSkipApiErrorResponse(payload: unknown): payload is QueueSkipApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueSkipApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isSkipQueueTicketResponse(payload: unknown): payload is SkipQueueTicketResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SkipQueueTicketResponse>
  return (
    candidate.success === true &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.skippedAt === 'string' &&
    typeof candidate.alreadySkipped === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): QueueSkipApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.skip.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    },
    idempotency: {
      status: 'failed'
    }
  }
}
