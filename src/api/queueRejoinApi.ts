import type {
  QueueRejoinApiErrorResponse,
  RejoinQueueTicketResponse
} from '../types/queueRejoin'

export class QueueRejoinApiError extends Error {
  readonly status: number
  readonly response: QueueRejoinApiErrorResponse

  constructor(status: number, response: QueueRejoinApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueRejoinApiError'
    this.status = status
    this.response = response
  }
}

export async function rejoinQueueTicket(
  storeId: string,
  queueTicketId: string,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<RejoinQueueTicketResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/rejoin`

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
    throw new QueueRejoinApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueRejoinApiErrorResponse(payload)) {
    const apiError: QueueRejoinApiErrorResponse = isQueueRejoinApiErrorResponse(payload)
      ? payload
      : unknownError(response.status)

    throw new QueueRejoinApiError(response.status, apiError)
  }

  if (!isRejoinQueueTicketResponse(payload)) {
    throw new QueueRejoinApiError(response.status, unknownError(response.status))
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

function isQueueRejoinApiErrorResponse(payload: unknown): payload is QueueRejoinApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueRejoinApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isRejoinQueueTicketResponse(payload: unknown): payload is RejoinQueueTicketResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<RejoinQueueTicketResponse>
  return (
    candidate.success === true &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    isOptionalNumber(candidate.queuePosition) &&
    typeof candidate.rejoinedAt === 'string' &&
    typeof candidate.alreadyRejoined === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function isOptionalNumber(value: unknown): value is number | null | undefined {
  return value === undefined || value === null || typeof value === 'number'
}

function unknownError(httpStatus?: number): QueueRejoinApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.rejoin.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    },
    idempotency: {
      status: 'failed'
    }
  }
}
