import type { MeAppEntry, MeAppsApiErrorResponse, MeAppsResponse } from '../types/meApps'

export class MeAppsApiError extends Error {
  readonly status: number
  readonly response: MeAppsApiErrorResponse

  constructor(status: number, response: MeAppsApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'MeAppsApiError'
    this.status = status
    this.response = response
  }
}

export async function fetchMeApps(
  storeId: string,
  fetcher: typeof fetch = fetch
): Promise<MeAppsResponse> {
  const endpoint = `/api/me/apps?storeId=${encodeURIComponent(storeId)}`

  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new MeAppsApiError(0, {
      success: false,
      error: {
        code: 'NETWORK_FAILURE',
        messageKey: 'me.apps.network_failure',
        details: {}
      }
    })
  }

  const payload = await readJson(response)

  if (!response.ok || isMeAppsApiErrorResponse(payload)) {
    const apiError: MeAppsApiErrorResponse = isMeAppsApiErrorResponse(payload)
      ? payload
      : {
          success: false,
          error: {
            code: response.status >= 500 ? 'PERSISTENCE_ERROR' : 'REQUEST_FAILED',
            messageKey: 'me.apps.request_failed',
            details: {
              httpStatus: response.status
            }
          }
        }

    throw new MeAppsApiError(response.status, apiError)
  }

  if (!isMeAppsResponse(payload)) {
    throw new MeAppsApiError(response.status, {
      success: false,
      error: {
        code: 'INVALID_API_RESPONSE',
        messageKey: 'me.apps.invalid_api_response',
        details: {}
      }
    })
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

function isMeAppsApiErrorResponse(payload: unknown): payload is MeAppsApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<MeAppsApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function isMeAppsResponse(payload: unknown): payload is MeAppsResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<MeAppsResponse>
  return (
    candidate.success === true &&
    Array.isArray(candidate.apps) &&
    candidate.apps.every(isMeAppEntry)
  )
}

function isMeAppEntry(payload: unknown): payload is MeAppEntry {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<MeAppEntry>
  return (
    typeof candidate.appKey === 'string' &&
    typeof candidate.appName === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.entryRoute === 'string' &&
    typeof candidate.entryVisible === 'boolean' &&
    Array.isArray(candidate.permissions) &&
    candidate.permissions.every(permission => typeof permission === 'string')
  )
}
