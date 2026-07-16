import type {
  PlatformReservationMealPeriodSeedMutation,
  PlatformReservationMealPeriodSeedResponse,
  ReservationMealPeriodApiErrorResponse,
  ReservationMealPeriodSettingsMutation,
  ReservationMealPeriodSettingsResponse,
  ReservationTimeSlotListResponse
} from '../types/reservationMealPeriod'

export class ReservationMealPeriodApiError extends Error {
  readonly status: number
  readonly response: ReservationMealPeriodApiErrorResponse

  constructor(status: number, response: ReservationMealPeriodApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'ReservationMealPeriodApiError'
    this.status = status
    this.response = response
  }
}

type ReservationMealPeriodFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export async function getPlatformReservationMealPeriodSeed(
  fetcher?: ReservationMealPeriodFetcher
): Promise<PlatformReservationMealPeriodSeedResponse> {
  return requestJson('/api/v1/platform/reservation/meal-period-seed', { method: 'GET', fetcher })
}

export async function updatePlatformReservationMealPeriodSeed(
  request: PlatformReservationMealPeriodSeedMutation,
  fetcher?: ReservationMealPeriodFetcher
): Promise<PlatformReservationMealPeriodSeedResponse> {
  return requestJson('/api/v1/platform/reservation/meal-period-seed', {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function getStoreReservationMealPeriods(
  storeId: string,
  fetcher?: ReservationMealPeriodFetcher
): Promise<ReservationMealPeriodSettingsResponse> {
  return requestJson(`${tenantEndpoint(storeId)}/reservation-meal-periods`, { method: 'GET', fetcher })
}

export async function updateStoreReservationMealPeriods(
  storeId: string,
  request: ReservationMealPeriodSettingsMutation,
  fetcher?: ReservationMealPeriodFetcher
): Promise<ReservationMealPeriodSettingsResponse> {
  return requestJson(`${tenantEndpoint(storeId)}/reservation-meal-periods`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function fetchReservationTimeSlots(
  storeId: string,
  businessDate: string,
  fetcher?: ReservationMealPeriodFetcher
): Promise<ReservationTimeSlotListResponse> {
  const params = new URLSearchParams()
  if (businessDate) {
    params.set('businessDate', businessDate)
  }
  const query = params.toString()
  return requestJson(
    `/api/v1/stores/${encodeURIComponent(storeId)}/reservations/time-slots${query ? `?${query}` : ''}`,
    { method: 'GET', fetcher }
  )
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    body?: unknown
    fetcher?: ReservationMealPeriodFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new ReservationMealPeriodApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isReservationMealPeriodApiErrorResponse(payload)) {
    throw new ReservationMealPeriodApiError(
      response.status,
      isReservationMealPeriodApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    body?: unknown
    fetcher?: ReservationMealPeriodFetcher
  }
): Promise<TextResponse> {
  const headers = {
    Accept: 'application/json',
    ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
  }
  const body = options.body === undefined ? undefined : JSON.stringify(options.body)
  const fetcher = options.fetcher ?? resolveFetch()

  if (fetcher) {
    return fetcher(endpoint, {
      method: options.method,
      credentials: 'include',
      headers,
      body
    })
  }

  return xhrRequest(endpoint, {
    method: options.method,
    headers,
    body
  })
}

function tenantEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin`
}

function resolveFetch(): ReservationMealPeriodFetcher | undefined {
  const candidate = globalThis.fetch
  return typeof candidate === 'function' ? candidate.bind(globalThis) : undefined
}

function xhrRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    headers: Record<string, string>
    body?: string
  }
): Promise<TextResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(options.method, endpoint, true)
    xhr.withCredentials = true

    Object.entries(options.headers).forEach(([name, value]) => {
      xhr.setRequestHeader(name, value)
    })

    xhr.onload = () => {
      resolve({
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        text: async () => xhr.responseText
      })
    }
    xhr.onerror = () => reject(new TypeError('Network request failed'))
    xhr.ontimeout = () => reject(new TypeError('Network request timed out'))
    xhr.send(options.body)
  })
}

async function readJson(response: TextResponse): Promise<unknown> {
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

function isReservationMealPeriodApiErrorResponse(payload: unknown): payload is ReservationMealPeriodApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<ReservationMealPeriodApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): ReservationMealPeriodApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'reservation.meal_period.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
