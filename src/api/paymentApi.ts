import type {
  PaymentApiErrorResponse,
  PaymentIntentCreateRequest,
  PaymentIntentCreateResponse,
  PaymentProfileMutation,
  PaymentProfileResponse,
  PaymentProfileTestQrResponse,
  QuickPayTerminalConfigResponse,
  PaymentSession
} from '../types/payment'

type PaymentFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export class PaymentApiError extends Error {
  readonly status: number
  readonly response: PaymentApiErrorResponse

  constructor(status: number, response: PaymentApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'PaymentApiError'
    this.status = status
    this.response = response
  }
}

export async function getPaymentProfile(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<PaymentProfileResponse> {
  return requestJson(profileEndpoint(storeId), { method: 'GET', fetcher })
}

export async function updatePaymentProfile(
  storeId: string,
  request: PaymentProfileMutation,
  fetcher?: PaymentFetcher
): Promise<PaymentProfileResponse> {
  return requestJson(profileEndpoint(storeId), { method: 'PATCH', body: request, fetcher })
}

export async function generatePaymentProfileTestQr(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<PaymentProfileTestQrResponse> {
  return requestJson(`${profileEndpoint(storeId)}/test-qr`, { method: 'POST', fetcher })
}

export async function createPaymentIntent(
  storeId: string,
  request: PaymentIntentCreateRequest,
  fetcher?: PaymentFetcher
): Promise<PaymentIntentCreateResponse> {
  return requestJson(intentEndpoint(storeId), { method: 'POST', body: request, fetcher })
}

export async function getQuickPayTerminalConfig(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<QuickPayTerminalConfigResponse> {
  return requestJson(`${intentEndpoint(storeId)}/terminal-config`, { method: 'GET', fetcher })
}

export async function getPaymentSession(
  storeId: string,
  sessionNo: string,
  fetcher?: PaymentFetcher
): Promise<PaymentSession> {
  return requestJson(`${intentEndpoint(storeId)}/sessions/${encodeURIComponent(sessionNo)}`, {
    method: 'GET',
    fetcher
  })
}

function profileEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/payment/profile`
}

function intentEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/payments/intents`
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: PaymentFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new PaymentApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isPaymentApiErrorResponse(payload)) {
    throw new PaymentApiError(
      response.status,
      isPaymentApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: PaymentFetcher
  }
): Promise<TextResponse> {
  const headers = {
    Accept: 'application/json',
    ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
  }
  const fetcher = options.fetcher ?? resolveFetch()
  const body = options.body === undefined ? undefined : JSON.stringify(options.body)

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

function resolveFetch(): PaymentFetcher | undefined {
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

function isPaymentApiErrorResponse(payload: unknown): payload is PaymentApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<PaymentApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): PaymentApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'payment.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
