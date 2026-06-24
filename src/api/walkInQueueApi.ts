import type {
  QueueWalkInRequest,
  QueueWalkInResponse,
  WalkInQueueApiErrorResponse
} from '../types/walkInQueue'

export class WalkInQueueApiError extends Error {
  readonly status: number
  readonly response: WalkInQueueApiErrorResponse

  constructor(status: number, response: WalkInQueueApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'WalkInQueueApiError'
    this.status = status
    this.response = response
  }
}

export async function queueWalkIn(
  storeId: string,
  request: QueueWalkInRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<QueueWalkInResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/walk-ins/queue`

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
    throw new WalkInQueueApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isWalkInQueueApiErrorResponse(payload)) {
    const apiError: WalkInQueueApiErrorResponse = isWalkInQueueApiErrorResponse(payload)
      ? payload
      : unknownError(response.status)

    throw new WalkInQueueApiError(response.status, apiError)
  }

  if (!isQueueWalkInResponse(payload)) {
    throw new WalkInQueueApiError(response.status, unknownError(response.status))
  }

  return payload
}

function toApiBody(request: QueueWalkInRequest): QueueWalkInRequest {
  return {
    partySize: request.partySize,
    customerId: trimToNull(request.customerId),
    customerName: trimToNull(request.customerName),
    customerNickname: trimToNull(request.customerNickname),
    phoneE164: trimToNull(request.phoneE164),
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

function isWalkInQueueApiErrorResponse(
  payload: unknown
): payload is WalkInQueueApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<WalkInQueueApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isQueueWalkInResponse(payload: unknown): payload is QueueWalkInResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueWalkInResponse>
  return (
    candidate.success === true &&
    typeof candidate.walkInId === 'string' &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.partySizeGroup === 'string' &&
    typeof candidate.businessDate === 'string' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): WalkInQueueApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'walkin.queue.unknown_error',
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
