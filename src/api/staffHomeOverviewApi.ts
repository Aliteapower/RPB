import type {
  StaffHomeOverviewApiErrorResponse,
  StaffHomeOverviewQuery,
  StaffHomeOverviewResponse
} from '../types/staffHomeOverview'

export class StaffHomeOverviewApiError extends Error {
  readonly status: number
  readonly response: StaffHomeOverviewApiErrorResponse

  constructor(status: number, response: StaffHomeOverviewApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'StaffHomeOverviewApiError'
    this.status = status
    this.response = response
  }
}

export async function getStaffHomeOverview(
  storeId: string,
  query: StaffHomeOverviewQuery = {},
  fetcher: typeof fetch = fetch
): Promise<StaffHomeOverviewResponse> {
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
    throw new StaffHomeOverviewApiError(0, createUnknownErrorResponse())
  }

  const payload = await readJson(response)

  if (!response.ok || isStaffHomeOverviewApiErrorResponse(payload)) {
    const apiError = isStaffHomeOverviewApiErrorResponse(payload)
      ? payload
      : createUnknownErrorResponse(response.status)

    throw new StaffHomeOverviewApiError(response.status, apiError)
  }

  if (!isStaffHomeOverviewResponse(payload)) {
    throw new StaffHomeOverviewApiError(response.status, createUnknownErrorResponse(response.status))
  }

  return payload
}

function buildEndpoint(storeId: string, query: StaffHomeOverviewQuery): string {
  const params = new URLSearchParams()
  const businessDate = query.businessDate?.trim()

  if (businessDate) {
    params.set('businessDate', businessDate)
  }

  const queryString = params.toString()
  const path = `/api/v1/stores/${encodeURIComponent(storeId)}/staff-home/overview`
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

function createUnknownErrorResponse(httpStatus?: number): StaffHomeOverviewApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'staff_home.overview.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isStaffHomeOverviewApiErrorResponse(
  payload: unknown
): payload is StaffHomeOverviewApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<StaffHomeOverviewApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isStaffHomeOverviewResponse(payload: unknown): payload is StaffHomeOverviewResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<StaffHomeOverviewResponse>
  return (
    candidate.success === true &&
    typeof candidate.storeId === 'string' &&
    typeof candidate.businessDate === 'string' &&
    typeof candidate.storeTimezone === 'string' &&
    isReservationMetrics(candidate.reservation) &&
    isQueueMetrics(candidate.queue) &&
    isTableMetrics(candidate.tables) &&
    Array.isArray(candidate.partySizeGroups) &&
    candidate.partySizeGroups.every(isPartySizeGroupMetrics)
  )
}

function isReservationMetrics(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return [
    'totalReservations',
    'totalPartySize',
    'arrivedReservations',
    'arrivedPartySize',
    'seatedReservations',
    'seatedPartySize',
    'cancelledReservations'
  ].every(key => typeof candidate[key] === 'number')
}

function isQueueMetrics(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return [
    'waitingTickets',
    'waitingPartySize',
    'calledTickets',
    'calledPartySize',
    'seatedTickets',
    'skippedTickets',
    'cancelledTickets',
    'expiredTickets'
  ].every(key => typeof candidate[key] === 'number')
}

function isTableMetrics(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return [
    'totalTables',
    'availableTables',
    'reservedTables',
    'occupiedTables',
    'cleaningTables',
    'temporaryGroups'
  ].every(key => typeof candidate[key] === 'number')
}

function isPartySizeGroupMetrics(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.label === 'string' &&
    typeof candidate.groups === 'number' &&
    typeof candidate.partySize === 'number'
  )
}
