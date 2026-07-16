import type {
  AssignableReservationTable,
  AssignableReservationTablesResponse,
  ReservationTableAssignmentApiErrorResponse,
  ReservationTableAssignmentResponse
} from '../types/reservationTableAssignment'

export class ReservationTableAssignmentApiError extends Error {
  readonly status: number
  readonly response: ReservationTableAssignmentApiErrorResponse

  constructor(status: number, response: ReservationTableAssignmentApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationTableAssignmentApiError'
    this.status = status
    this.response = response
  }
}

export async function getAssignableReservationTables(
  storeId: string,
  reservationId: string,
  fetcher: typeof fetch = fetch
): Promise<AssignableReservationTablesResponse> {
  return requestAssignableTables(
    `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/assignable-tables`,
    fetcher
  )
}

export async function assignReservationTable(
  storeId: string,
  reservationId: string,
  tableId: string,
  idempotencyKey: string,
  fetcher: typeof fetch = fetch
): Promise<ReservationTableAssignmentResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/table-assignment`
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'PUT',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({ tableId })
    })
  } catch {
    throw new ReservationTableAssignmentApiError(
      0,
      localError('NETWORK_FAILURE', 'reservation.table_assignment.network_failure')
    )
  }

  const payload = await readJson(response)
  if (!response.ok || isApiError(payload)) {
    throw new ReservationTableAssignmentApiError(
      response.status,
      isApiError(payload)
        ? payload
        : localError('REQUEST_FAILED', 'reservation.table_assignment.request_failed')
    )
  }
  if (!isAssignmentResponse(payload)) {
    throw new ReservationTableAssignmentApiError(
      response.status,
      localError('INVALID_API_RESPONSE', 'reservation.table_assignment.invalid_api_response')
    )
  }
  return payload
}

async function requestAssignableTables(
  endpoint: string,
  fetcher: typeof fetch
): Promise<AssignableReservationTablesResponse> {
  let response: Response
  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    })
  } catch {
    throw new ReservationTableAssignmentApiError(
      0,
      localError('NETWORK_FAILURE', 'reservation.table_assignment.network_failure')
    )
  }

  const payload = await readJson(response)
  if (!response.ok || isApiError(payload)) {
    throw new ReservationTableAssignmentApiError(
      response.status,
      isApiError(payload)
        ? payload
        : localError('REQUEST_FAILED', 'reservation.table_assignment.request_failed')
    )
  }
  if (!isAssignableResponse(payload)) {
    throw new ReservationTableAssignmentApiError(
      response.status,
      localError('INVALID_API_RESPONSE', 'reservation.table_assignment.invalid_api_response')
    )
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

function isAssignableResponse(payload: unknown): payload is AssignableReservationTablesResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<AssignableReservationTablesResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.partySize === 'number' &&
    Array.isArray(candidate.tables) &&
    candidate.tables.every(isAssignableTable)
  )
}

function isAssignableTable(payload: unknown): payload is AssignableReservationTable {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<AssignableReservationTable>
  return (
    typeof candidate.tableId === 'string' &&
    typeof candidate.tableCode === 'string' &&
    typeof candidate.displayName === 'string' &&
    typeof candidate.capacityMin === 'number' &&
    typeof candidate.capacityMax === 'number'
  )
}

function isAssignmentResponse(payload: unknown): payload is ReservationTableAssignmentResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<ReservationTableAssignmentResponse>
  return (
    candidate.success === true &&
    typeof candidate.reservationId === 'string' &&
    typeof candidate.tableId === 'string' &&
    typeof candidate.tableCode === 'string' &&
    typeof candidate.assignmentStatus === 'string' &&
    typeof candidate.idempotency?.status === 'string' &&
    typeof candidate.idempotency?.replayed === 'boolean'
  )
}

function isApiError(payload: unknown): payload is ReservationTableAssignmentApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<ReservationTableAssignmentApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function localError(code: string, messageKey: string): ReservationTableAssignmentApiErrorResponse {
  return {
    success: false,
    error: { code, messageKey, details: {} }
  }
}
