import type {
  QueueTicketListApiErrorResponse,
  QueueTicketListItem,
  QueueTicketListPage,
  QueueTicketListQuery,
  QueueTicketListResponse
} from '../types/queueTicketList'

export class QueueTicketListApiError extends Error {
  readonly status: number
  readonly response: QueueTicketListApiErrorResponse

  constructor(status: number, response: QueueTicketListApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueTicketListApiError'
    this.status = status
    this.response = response
  }
}

export async function listQueueTickets(
  storeId: string,
  query: QueueTicketListQuery = {},
  fetcher: typeof fetch = fetch
): Promise<QueueTicketListResponse> {
  const endpoint = buildEndpoint(storeId, query)

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new QueueTicketListApiError(0, createUnknownErrorResponse())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueTicketListApiErrorResponse(payload)) {
    const apiError: QueueTicketListApiErrorResponse = isQueueTicketListApiErrorResponse(payload)
      ? payload
      : createUnknownErrorResponse(response.status)

    throw new QueueTicketListApiError(response.status, apiError)
  }

  if (!isQueueTicketListResponse(payload)) {
    throw new QueueTicketListApiError(response.status, createUnknownErrorResponse(response.status))
  }

  return payload
}

function buildEndpoint(storeId: string, query: QueueTicketListQuery): string {
  const params = new URLSearchParams()
  const status = query.status?.trim()

  if (status) {
    params.set('status', status)
  }

  const tableArea = query.tableArea?.trim()
  if (tableArea) {
    params.set('tableArea', tableArea)
  }

  const phone = query.phone?.trim()
  if (phone) {
    params.set('phone', phone)
  }

  setNumberParam(params, 'partySize', query.partySize)
  setNumberParam(params, 'limit', query.limit)
  setNumberParam(params, 'offset', query.offset)

  const queryString = params.toString()
  const path = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets`
  return queryString ? `${path}?${queryString}` : path
}

function setNumberParam(params: URLSearchParams, key: string, value: number | undefined): void {
  if (typeof value === 'number' && Number.isFinite(value)) {
    params.set(key, String(value))
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

function createUnknownErrorResponse(httpStatus?: number): QueueTicketListApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.list.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isQueueTicketListApiErrorResponse(
  payload: unknown
): payload is QueueTicketListApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueTicketListApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isQueueTicketListResponse(payload: unknown): payload is QueueTicketListResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueTicketListResponse>
  return (
    candidate.success === true &&
    Array.isArray(candidate.items) &&
    candidate.items.every(isQueueTicketListItem) &&
    isQueueTicketListPage(candidate.page)
  )
}

function isQueueTicketListPage(payload: unknown): payload is QueueTicketListPage {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueTicketListPage>
  return (
    typeof candidate.limit === 'number' &&
    typeof candidate.offset === 'number' &&
    typeof candidate.total === 'number'
  )
}

function isQueueTicketListItem(payload: unknown): payload is QueueTicketListItem {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueTicketListItem>
  return (
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.queueTicketNumber === 'number' &&
    typeof candidate.queueTicketDisplayNumber === 'string' &&
    typeof candidate.queueTicketStatus === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.partySizeGroup === 'string' &&
    typeof candidate.createdAt === 'string' &&
    isOptionalString(candidate.reservationId) &&
    isOptionalString(candidate.reservationCode) &&
    isOptionalString(candidate.reservationStatus) &&
    isOptionalString(candidate.customerName) &&
    isOptionalString(candidate.customerPhoneMasked) &&
    isOptionalString(candidate.assignedResourceType) &&
    isOptionalString(candidate.assignedResourceId) &&
    isOptionalString(candidate.assignedResourceCode) &&
    isOptionalString(candidate.assignedResourceGroupType) &&
    isOptionalString(candidate.assignedResourceLabel) &&
    isOptionalString(candidate.assignedResourceAreaName) &&
    isOptionalString(candidate.calledAt) &&
    isOptionalString(candidate.holdUntilAt) &&
    isOptionalString(candidate.expiresAt)
  )
}

function isOptionalString(value: unknown): value is string | null | undefined {
  return value === undefined || value === null || typeof value === 'string'
}
