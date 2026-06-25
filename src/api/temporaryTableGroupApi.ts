import type {
  SaveTemporaryTableGroupRequest,
  TemporaryTableGroupApiErrorResponse,
  TemporaryTableGroupResponse
} from '../types/temporaryTableGroup'

export class TemporaryTableGroupApiError extends Error {
  readonly status: number
  readonly response: TemporaryTableGroupApiErrorResponse

  constructor(status: number, response: TemporaryTableGroupApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'TemporaryTableGroupApiError'
    this.status = status
    this.response = response
  }
}

export async function saveTemporaryTableGroup(
  storeId: string,
  request: SaveTemporaryTableGroupRequest,
  fetcher: typeof fetch = fetch
): Promise<TemporaryTableGroupResponse> {
  return sendTemporaryTableGroupRequest(
    `/api/v1/stores/${encodeURIComponent(storeId)}/tables/temporary-groups`,
    'POST',
    request,
    fetcher
  )
}

export async function dissolveTemporaryTableGroup(
  storeId: string,
  tableGroupId: string,
  fetcher: typeof fetch = fetch
): Promise<TemporaryTableGroupResponse> {
  return sendTemporaryTableGroupRequest(
    `/api/v1/stores/${encodeURIComponent(storeId)}/tables/temporary-groups/${encodeURIComponent(tableGroupId)}`,
    'DELETE',
    null,
    fetcher
  )
}

async function sendTemporaryTableGroupRequest(
  endpoint: string,
  method: 'POST' | 'DELETE',
  request: SaveTemporaryTableGroupRequest | null,
  fetcher: typeof fetch
): Promise<TemporaryTableGroupResponse> {
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method,
      headers: {
        Accept: 'application/json',
        ...(request ? { 'Content-Type': 'application/json' } : {})
      },
      body: request ? JSON.stringify(request) : undefined
    })
  } catch {
    throw new TemporaryTableGroupApiError(0, createUnknownErrorResponse())
  }

  const payload = await readJson(response)

  if (!response.ok || isTemporaryTableGroupApiErrorResponse(payload)) {
    throw new TemporaryTableGroupApiError(
      response.status,
      isTemporaryTableGroupApiErrorResponse(payload)
        ? payload
        : createUnknownErrorResponse(response.status)
    )
  }

  if (!isTemporaryTableGroupResponse(payload)) {
    throw new TemporaryTableGroupApiError(response.status, createUnknownErrorResponse(response.status))
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

function createUnknownErrorResponse(httpStatus?: number): TemporaryTableGroupApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'table.temporary_group.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isTemporaryTableGroupApiErrorResponse(
  payload: unknown
): payload is TemporaryTableGroupApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TemporaryTableGroupApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isTemporaryTableGroupResponse(payload: unknown): payload is TemporaryTableGroupResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TemporaryTableGroupResponse>
  return (
    candidate.success === true &&
    typeof candidate.tableGroupId === 'string' &&
    typeof candidate.groupName === 'string' &&
    typeof candidate.groupType === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.capacityMin === 'number' &&
    typeof candidate.capacityMax === 'number' &&
    Array.isArray(candidate.tableIds)
  )
}
