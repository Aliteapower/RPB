import type {
  PlatformCallScreenSeedApiErrorResponse,
  PlatformCallScreenSeedMutation,
  PlatformCallScreenSeedResponse
} from '../types/platformCallScreenSeed'

export class PlatformCallScreenSeedApiError extends Error {
  readonly status: number
  readonly response: PlatformCallScreenSeedApiErrorResponse

  constructor(status: number, response: PlatformCallScreenSeedApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'PlatformCallScreenSeedApiError'
    this.status = status
    this.response = response
  }
}

type PlatformCallScreenSeedFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

const endpoint = '/api/v1/platform/call-screen/text-seed'

export async function getPlatformCallScreenTextSeed(
  fetcher?: PlatformCallScreenSeedFetcher
): Promise<PlatformCallScreenSeedResponse> {
  return requestJson(endpoint, { method: 'GET', fetcher })
}

export async function updatePlatformCallScreenTextSeed(
  request: PlatformCallScreenSeedMutation,
  fetcher?: PlatformCallScreenSeedFetcher
): Promise<PlatformCallScreenSeedResponse> {
  return requestJson(endpoint, { method: 'PATCH', body: request, fetcher })
}

async function requestJson<T>(
  requestEndpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    body?: unknown
    fetcher?: PlatformCallScreenSeedFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(requestEndpoint, options)
  } catch {
    throw new PlatformCallScreenSeedApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isPlatformCallScreenSeedApiErrorResponse(payload)) {
    throw new PlatformCallScreenSeedApiError(
      response.status,
      isPlatformCallScreenSeedApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  requestEndpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    body?: unknown
    fetcher?: PlatformCallScreenSeedFetcher
  }
): Promise<TextResponse> {
  const headers = {
    Accept: 'application/json',
    ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
  }
  const body = options.body === undefined ? undefined : JSON.stringify(options.body)
  const fetcher = options.fetcher ?? resolveFetch()

  if (fetcher) {
    return fetcher(requestEndpoint, {
      method: options.method,
      credentials: 'include',
      headers,
      body
    })
  }

  return xhrRequest(requestEndpoint, {
    method: options.method,
    headers,
    body
  })
}

function resolveFetch(): PlatformCallScreenSeedFetcher | undefined {
  const candidate = globalThis.fetch
  return typeof candidate === 'function' ? candidate.bind(globalThis) : undefined
}

function xhrRequest(
  requestEndpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    headers: Record<string, string>
    body?: string
  }
): Promise<TextResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(options.method, requestEndpoint, true)
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

function isPlatformCallScreenSeedApiErrorResponse(payload: unknown): payload is PlatformCallScreenSeedApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<PlatformCallScreenSeedApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): PlatformCallScreenSeedApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'platform.call_screen_seed.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
