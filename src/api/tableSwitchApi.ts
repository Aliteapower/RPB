import type {
  SwitchTableRequest,
  SwitchTableResponse,
  TableSwitchApiErrorResponse
} from '../types/tableSwitch'

export class TableSwitchApiError extends Error {
  readonly status: number
  readonly response: TableSwitchApiErrorResponse

  constructor(status: number, response: TableSwitchApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'TableSwitchApiError'
    this.status = status
    this.response = response
  }
}

export async function switchTable(
  storeId: string,
  seatingId: string,
  request: SwitchTableRequest,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<SwitchTableResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/seatings/${encodeURIComponent(seatingId)}/table-switch`

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
    throw new TableSwitchApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'table_switch.network_failure',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isTableSwitchApiErrorResponse(payload)) {
    const apiError: TableSwitchApiErrorResponse =
      isTableSwitchApiErrorResponse(payload)
        ? payload
        : {
            success: false,
            error: {
              code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
              messageKey: 'table_switch.request_failed',
              details: {
                httpStatus: response.status
              }
            },
            idempotency: {
              status: 'failed'
            }
          }

    throw new TableSwitchApiError(response.status, apiError)
  }

  if (!isSwitchTableResponse(payload)) {
    throw new TableSwitchApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'table_switch.invalid_api_response',
        details: {}
      },
      idempotency: {
        status: 'failed'
      }
    })
  }

  return payload
}

function toApiBody(request: SwitchTableRequest): SwitchTableRequest {
  return {
    tableId: request.tableId ?? null,
    tableGroupId: request.tableGroupId ?? null,
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

function isTableSwitchApiErrorResponse(payload: unknown): payload is TableSwitchApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TableSwitchApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isSwitchTableResponse(payload: unknown): payload is SwitchTableResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<SwitchTableResponse>
  return (
    candidate.success === true &&
    typeof candidate.seatingId === 'string' &&
    typeof candidate.seatingStatus === 'string' &&
    Array.isArray(candidate.events) &&
    !!candidate.fromResource &&
    !!candidate.toResource &&
    !!candidate.idempotency
  )
}
