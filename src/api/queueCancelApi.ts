import type {
  CancelQueueTicketRequest,
  CancelQueueTicketResponse,
  QueueCancelApiErrorResponse
} from '../types/queueCancel'

export class QueueCancelApiError extends Error {
  readonly status: number
  readonly response: QueueCancelApiErrorResponse

  constructor(status: number, response: QueueCancelApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueCancelApiError'
    this.status = status
    this.response = response
  }
}

export async function cancelQueueTicket(
  storeId: string,
  queueTicketId: string,
  request: CancelQueueTicketRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<CancelQueueTicketResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/cancel`

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
    throw new QueueCancelApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueCancelApiErrorResponse(payload)) {
    const apiError: QueueCancelApiErrorResponse = isQueueCancelApiErrorResponse(payload)
      ? payload
      : unknownError(response.status)

    throw new QueueCancelApiError(response.status, apiError)
  }

  if (!isCancelQueueTicketResponse(payload)) {
    throw new QueueCancelApiError(response.status, unknownError(response.status))
  }

  return payload
}

function toApiBody(request: CancelQueueTicketRequest): CancelQueueTicketRequest {
  return {
    cancelledAt: trimToNull(request.cancelledAt),
    reasonCode: trimToNull(request.reasonCode),
    note: trimToNull(request.note)
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

function isQueueCancelApiErrorResponse(payload: unknown): payload is QueueCancelApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueCancelApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isCancelQueueTicketResponse(payload: unknown): payload is CancelQueueTicketResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CancelQueueTicketResponse>
  return (
    candidate.success === true &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.cancelledAt === 'string' &&
    typeof candidate.alreadyCancelled === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): QueueCancelApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.cancel.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    },
    idempotency: {
      status: 'failed'
    }
  }
}

function trimToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed ? trimmed : null
}
