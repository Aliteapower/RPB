import type {
  CallScreenAdminApiErrorResponse,
  CallScreenAdSetListResponse,
  CallScreenAdSetMutation,
  CallScreenAdSetResponse,
  CallScreenMediaAssetResponse,
  CallScreenSettingsMutation,
  CallScreenSettingsResponse
} from '../types/callScreenAdmin'

export class CallScreenAdminApiError extends Error {
  readonly status: number
  readonly response: CallScreenAdminApiErrorResponse

  constructor(status: number, response: CallScreenAdminApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'CallScreenAdminApiError'
    this.status = status
    this.response = response
  }
}

type CallScreenAdminFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export async function getCallScreenSettings(
  storeId: string,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenSettingsResponse> {
  return requestJson(`${baseEndpoint(storeId)}/settings`, { method: 'GET', fetcher })
}

export async function updateCallScreenSettings(
  storeId: string,
  request: CallScreenSettingsMutation,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenSettingsResponse> {
  return requestJson(`${baseEndpoint(storeId)}/settings`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function listCallScreenAdSets(
  storeId: string,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenAdSetListResponse> {
  return requestJson(`${baseEndpoint(storeId)}/ad-sets`, { method: 'GET', fetcher })
}

export async function createCallScreenAdSet(
  storeId: string,
  request: CallScreenAdSetMutation,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenAdSetResponse> {
  return requestJson(`${baseEndpoint(storeId)}/ad-sets`, {
    method: 'POST',
    body: request,
    fetcher
  })
}

export async function getCallScreenAdSet(
  storeId: string,
  adSetId: string,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenAdSetResponse> {
  return requestJson(`${baseEndpoint(storeId)}/ad-sets/${encodeURIComponent(adSetId)}`, { method: 'GET', fetcher })
}

export async function updateCallScreenAdSet(
  storeId: string,
  adSetId: string,
  request: CallScreenAdSetMutation,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenAdSetResponse> {
  return requestJson(`${baseEndpoint(storeId)}/ad-sets/${encodeURIComponent(adSetId)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function uploadCallScreenMedia(
  storeId: string,
  file: File,
  fetcher?: CallScreenAdminFetcher
): Promise<CallScreenMediaAssetResponse> {
  const form = new FormData()
  form.append('file', file)
  return requestForm(`${baseEndpoint(storeId)}/media`, form, fetcher)
}

function baseEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/call-screen`
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: CallScreenAdminFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new CallScreenAdminApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isCallScreenAdminApiErrorResponse(payload)) {
    throw new CallScreenAdminApiError(
      response.status,
      isCallScreenAdminApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function requestForm<T>(
  endpoint: string,
  form: FormData,
  fetcher?: CallScreenAdminFetcher
): Promise<T> {
  let response: TextResponse
  try {
    const activeFetcher = fetcher ?? resolveFetch()
    if (activeFetcher) {
      response = await activeFetcher(endpoint, {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
        body: form
      })
    } else {
      response = await xhrFormRequest(endpoint, form)
    }
  } catch {
    throw new CallScreenAdminApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isCallScreenAdminApiErrorResponse(payload)) {
    throw new CallScreenAdminApiError(
      response.status,
      isCallScreenAdminApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: CallScreenAdminFetcher
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

function resolveFetch(): CallScreenAdminFetcher | undefined {
  const candidate = globalThis.fetch
  return typeof candidate === 'function' ? candidate.bind(globalThis) : undefined
}

function xhrRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
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

function xhrFormRequest(endpoint: string, form: FormData): Promise<TextResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', endpoint, true)
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.onload = () => {
      resolve({
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        text: async () => xhr.responseText
      })
    }
    xhr.onerror = () => reject(new TypeError('Network request failed'))
    xhr.ontimeout = () => reject(new TypeError('Network request timed out'))
    xhr.send(form)
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

function isCallScreenAdminApiErrorResponse(payload: unknown): payload is CallScreenAdminApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CallScreenAdminApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): CallScreenAdminApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'call_screen.admin.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
