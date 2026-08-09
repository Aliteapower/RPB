import type {
  PaymentApiErrorResponse,
  PaymentIntentCreateRequest,
  PaymentIntentCreateResponse,
  PaymentManualConfirmRequest,
  PaymentManualConfirmResponse,
  PaymentBusinessDayResponse,
  PaymentProfileMutation,
  PaymentProfileResponse,
  PaymentProfileTestQrResponse,
  PaymentProofCandidatesQuery,
  PaymentProofCandidatesResponse,
  PaymentProofScanRequest,
  PaymentProofScanResponse,
  QuickPayRecordsQuery,
  QuickPayRecordsResponse,
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

export async function getQuickPayRecords(
  storeId: string,
  query: QuickPayRecordsQuery = {},
  fetcher?: PaymentFetcher
): Promise<QuickPayRecordsResponse> {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    const text = String(value ?? '').trim()
    if (text) {
      params.set(key, text)
    }
  })
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return requestJson(`${intentEndpoint(storeId)}/quick-pay-records${suffix}`, { method: 'GET', fetcher })
}

export async function manualConfirmQuickPay(
  storeId: string,
  sessionNo: string,
  request: PaymentManualConfirmRequest,
  fetcher?: PaymentFetcher
): Promise<PaymentManualConfirmResponse> {
  return requestJson(`${intentEndpoint(storeId)}/sessions/${encodeURIComponent(sessionNo)}/manual-confirm`, {
    method: 'POST',
    body: request,
    fetcher
  })
}

export async function getPaymentProofCandidates(
  storeId: string,
  query: PaymentProofCandidatesQuery = {},
  fetcher?: PaymentFetcher
): Promise<PaymentProofCandidatesResponse> {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    const text = String(value ?? '').trim()
    if (text) {
      params.set(key, text)
    }
  })
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return requestJson(`${proofReviewEndpoint(storeId)}/candidates${suffix}`, { method: 'GET', fetcher })
}

export async function scanPaymentProof(
  storeId: string,
  request: PaymentProofScanRequest,
  fetcher?: PaymentFetcher
): Promise<PaymentProofScanResponse> {
  const form = new FormData()
  form.set('image', request.image)
  form.set('idempotencyKey', request.idempotencyKey)
  if (request.businessDate?.trim()) {
    form.set('businessDate', request.businessDate.trim())
  }
  if (request.terminalCode?.trim()) {
    form.set('terminalCode', request.terminalCode.trim())
  }
  return requestMultipart(`${proofReviewEndpoint(storeId)}/scan`, form, fetcher)
}

export async function getPaymentBusinessDay(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<PaymentBusinessDayResponse> {
  return requestJson(businessDayEndpoint(storeId), { method: 'GET', fetcher })
}

export async function openPaymentBusinessDay(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<PaymentBusinessDayResponse> {
  return requestJson(businessDayEndpoint(storeId), { method: 'POST', fetcher })
}

export async function endPaymentBusinessDay(
  storeId: string,
  fetcher?: PaymentFetcher
): Promise<PaymentBusinessDayResponse> {
  return requestJson(`${businessDayEndpoint(storeId)}/end-day`, { method: 'POST', fetcher })
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

function businessDayEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/payments/business-day`
}

function proofReviewEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/payments/proof-review`
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
  const apiError = normalizePaymentApiErrorResponse(payload)
  if (!response.ok || apiError) {
    throw new PaymentApiError(response.status, apiError ?? unknownError(response.status))
  }

  return payload as T
}

async function requestMultipart<T>(endpoint: string, body: FormData, fetcher?: PaymentFetcher): Promise<T> {
  let response: TextResponse

  try {
    const sender = fetcher ?? resolveFetch()
    if (sender) {
      response = await sender(endpoint, {
        method: 'POST',
        credentials: 'include',
        headers: {
          Accept: 'application/json'
        },
        body
      })
    } else {
      response = await xhrMultipartRequest(endpoint, body)
    }
  } catch {
    throw new PaymentApiError(0, unknownError())
  }

  const payload = await readJson(response)
  const apiError = normalizePaymentApiErrorResponse(payload)
  if (!response.ok || apiError) {
    throw new PaymentApiError(response.status, apiError ?? unknownError(response.status))
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

function xhrMultipartRequest(endpoint: string, body: FormData): Promise<TextResponse> {
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
    xhr.send(body)
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

function normalizePaymentApiErrorResponse(payload: unknown): PaymentApiErrorResponse | null {
  if (!payload || typeof payload !== 'object') {
    return null
  }

  const candidate = payload as Partial<PaymentApiErrorResponse>
  if (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  ) {
    return candidate as PaymentApiErrorResponse
  }

  const flat = payload as {
    success?: unknown
    code?: unknown
    message?: unknown
  }
  if (flat.success === false && typeof flat.code === 'string') {
    return {
      success: false,
      error: {
        code: flat.code,
        messageKey: typeof flat.message === 'string' ? flat.message : flat.code,
        details: {}
      }
    }
  }

  return null
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
