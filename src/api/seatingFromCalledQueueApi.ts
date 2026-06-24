import type {
  SeatCalledQueueTicketRequest,
  SeatCalledQueueTicketResponse,
  SeatingFromCalledQueueApiErrorResponse
} from '../types/seatingFromCalledQueue'

type OptionalStringRequestKey = Exclude<keyof SeatCalledQueueTicketRequest, 'temporaryTableIds'>

export class SeatingFromCalledQueueApiError extends Error {
  readonly status: number
  readonly response: SeatingFromCalledQueueApiErrorResponse

  constructor(status: number, response: SeatingFromCalledQueueApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'SeatingFromCalledQueueApiError'
    this.status = status
    this.response = response
  }
}

export async function seatCalledQueueTicket(
  storeId: string,
  queueTicketId: string,
  request: SeatCalledQueueTicketRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SeatCalledQueueTicketResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/seating/direct`

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
    throw new SeatingFromCalledQueueApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isSeatingFromCalledQueueApiErrorResponse(payload)) {
    const apiError: SeatingFromCalledQueueApiErrorResponse =
      isSeatingFromCalledQueueApiErrorResponse(payload)
        ? payload
        : unknownError(response.status)

    throw new SeatingFromCalledQueueApiError(response.status, apiError)
  }

  if (!isSeatCalledQueueTicketResponse(payload)) {
    throw new SeatingFromCalledQueueApiError(response.status, unknownError())
  }

  return payload
}

function toApiBody(request: SeatCalledQueueTicketRequest): SeatCalledQueueTicketRequest {
  const body: SeatCalledQueueTicketRequest = {}

  addOptionalField(body, 'tableId', request.tableId)
  addOptionalField(body, 'tableGroupId', request.tableGroupId)
  addTemporaryTableIds(body, request.temporaryTableIds)
  addOptionalField(body, 'overrideReasonCode', request.overrideReasonCode)
  addOptionalField(body, 'overrideNote', request.overrideNote)
  addOptionalField(body, 'note', request.note)

  return body
}

function addTemporaryTableIds(
  body: SeatCalledQueueTicketRequest,
  value: string[] | null | undefined
): void {
  const ids = value?.map(item => item.trim()).filter(Boolean) ?? []

  if (ids.length) {
    body.temporaryTableIds = ids
  }
}

function addOptionalField(
  body: SeatCalledQueueTicketRequest,
  key: OptionalStringRequestKey,
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

function isSeatingFromCalledQueueApiErrorResponse(
  payload: unknown
): payload is SeatingFromCalledQueueApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SeatingFromCalledQueueApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isSeatCalledQueueTicketResponse(
  payload: unknown
): payload is SeatCalledQueueTicketResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SeatCalledQueueTicketResponse>
  return (
    candidate.success === true &&
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.reservationCode === 'string' &&
    typeof candidate.reservationStatus === 'string' &&
    typeof candidate.seatingId === 'string' &&
    typeof candidate.seatingStatus === 'string' &&
    typeof candidate.resourceType === 'string' &&
    typeof candidate.resourceId === 'string' &&
    typeof candidate.alreadySeated === 'boolean' &&
    Array.isArray(candidate.events) &&
    !!candidate.idempotency
  )
}

function unknownError(httpStatus?: number): SeatingFromCalledQueueApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.seat.unknown_error',
      details: httpStatus ? { httpStatus } : {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}
