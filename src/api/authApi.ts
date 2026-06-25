import type {
  AuthApiErrorResponse,
  AuthLoginResponse,
  AuthLogoutResponse,
  AuthMeResponse,
  LoginRequest,
  SliderCaptchaResponse
} from '../types/auth'

export class AuthApiError extends Error {
  readonly status: number
  readonly response: AuthApiErrorResponse

  constructor(status: number, response: AuthApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'AuthApiError'
    this.status = status
    this.response = response
  }
}

type AuthFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export async function createSliderCaptcha(fetcher?: AuthFetcher): Promise<SliderCaptchaResponse> {
  return requestJson('/api/v1/auth/captcha/slider', {
    method: 'POST',
    fetcher
  })
}

export async function login(request: LoginRequest, fetcher?: AuthFetcher): Promise<AuthLoginResponse> {
  return requestJson('/api/v1/auth/login', {
    method: 'POST',
    body: request,
    fetcher
  })
}

export async function fetchCurrentUser(fetcher?: AuthFetcher): Promise<AuthMeResponse> {
  return requestJson('/api/v1/auth/me', {
    method: 'GET',
    fetcher
  })
}

export async function logout(fetcher?: AuthFetcher): Promise<AuthLogoutResponse> {
  return requestJson('/api/v1/auth/logout', {
    method: 'POST',
    fetcher
  })
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST'
    body?: unknown
    fetcher?: AuthFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new AuthApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isAuthApiErrorResponse(payload)) {
    throw new AuthApiError(
      response.status,
      isAuthApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST'
    body?: unknown
    fetcher?: AuthFetcher
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

function resolveFetch(): AuthFetcher | undefined {
  const candidate = globalThis.fetch
  return typeof candidate === 'function' ? candidate.bind(globalThis) : undefined
}

function xhrRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST'
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

function isAuthApiErrorResponse(payload: unknown): payload is AuthApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<AuthApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): AuthApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'auth.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
