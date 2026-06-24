import type {
  TableResourceApiErrorResponse,
  TableResourceItem,
  TableResourceListResponse,
  TableResourceQuery
} from '../types/tableResource'

export class TableResourceApiError extends Error {
  readonly status: number
  readonly response: TableResourceApiErrorResponse

  constructor(status: number, response: TableResourceApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'TableResourceApiError'
    this.status = status
    this.response = response
  }
}

export async function fetchTableResources(
  storeId: string,
  query: TableResourceQuery = {},
  fetcher: typeof fetch = fetch
): Promise<TableResourceListResponse> {
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
    throw new TableResourceApiError(0, createUnknownErrorResponse())
  }

  const payload = await readJson(response)

  if (!response.ok || isTableResourceApiErrorResponse(payload)) {
    const apiError = isTableResourceApiErrorResponse(payload)
      ? payload
      : createUnknownErrorResponse(response.status)

    throw new TableResourceApiError(response.status, apiError)
  }

  if (!isTableResourceListResponse(payload)) {
    throw new TableResourceApiError(response.status, createUnknownErrorResponse(response.status))
  }

  return payload
}

function buildEndpoint(storeId: string, query: TableResourceQuery): string {
  const params = new URLSearchParams()
  const status = query.status?.trim()

  if (status) {
    params.set('status', status)
  }

  if (typeof query.partySize === 'number' && Number.isFinite(query.partySize)) {
    params.set('partySize', String(query.partySize))
  }

  if (typeof query.includeGroups === 'boolean') {
    params.set('includeGroups', String(query.includeGroups))
  }

  const queryString = params.toString()
  const path = `/api/v1/stores/${storeId}/tables`
  return queryString ? `${path}?${queryString}` : path
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

function createUnknownErrorResponse(httpStatus?: number): TableResourceApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'table.resources.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isTableResourceApiErrorResponse(
  payload: unknown
): payload is TableResourceApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TableResourceApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isTableResourceListResponse(payload: unknown): payload is TableResourceListResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TableResourceListResponse>
  return (
    candidate.success === true &&
    Array.isArray(candidate.resources) &&
    candidate.resources.every(isTableResourceItem)
  )
}

function isTableResourceItem(payload: unknown): payload is TableResourceItem {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TableResourceItem>
  return (
    (candidate.resourceType === 'dining_table' || candidate.resourceType === 'table_group') &&
    typeof candidate.resourceId === 'string' &&
    typeof candidate.code === 'string' &&
    typeof candidate.displayName === 'string' &&
    typeof candidate.capacityMin === 'number' &&
    typeof candidate.capacityMax === 'number' &&
    typeof candidate.status === 'string' &&
    typeof candidate.selectable === 'boolean' &&
    (candidate.currentSeatingId === undefined ||
      candidate.currentSeatingId === null ||
      typeof candidate.currentSeatingId === 'string') &&
    (candidate.currentCleaningId === undefined ||
      candidate.currentCleaningId === null ||
      typeof candidate.currentCleaningId === 'string') &&
    Array.isArray(candidate.memberTableCodes) &&
    candidate.memberTableCodes.every(value => typeof value === 'string')
  )
}
