import type {
  PlatformBillingApiErrorResponse,
  PlatformProductLineListResponse,
  PlatformProductLineMutation,
  PlatformProductLinePriceMutation,
  PlatformProductLineResponse,
  ProductSubscriptionMutation,
  ProductSubscriptionStatusMutation,
  TenantProductSubscriptionListResponse,
  TenantProductSubscriptionResponse
} from '../types/platformProductLineBilling'

export class PlatformBillingApiError extends Error {
  readonly status: number
  readonly response: PlatformBillingApiErrorResponse

  constructor(status: number, response: PlatformBillingApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'PlatformBillingApiError'
    this.status = status
    this.response = response
  }
}

type PlatformBillingFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export async function listProductLines(fetcher?: PlatformBillingFetcher): Promise<PlatformProductLineListResponse> {
  return requestJson('/api/v1/platform/product-lines', {
    method: 'GET',
    fetcher
  })
}

export async function updateProductLine(
  appKey: string,
  request: PlatformProductLineMutation,
  fetcher?: PlatformBillingFetcher
): Promise<PlatformProductLineResponse> {
  return requestJson(`/api/v1/platform/product-lines/${encodeURIComponent(appKey)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function updateProductLinePrices(
  appKey: string,
  request: PlatformProductLinePriceMutation,
  fetcher?: PlatformBillingFetcher
): Promise<PlatformProductLineResponse> {
  return requestJson(`/api/v1/platform/product-lines/${encodeURIComponent(appKey)}/prices`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function listTenantProductSubscriptions(
  tenantId: string,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionListResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions`, {
    method: 'GET',
    fetcher
  })
}

export async function purchaseProductSubscription(
  tenantId: string,
  request: ProductSubscriptionMutation,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/purchase`, {
    method: 'POST',
    body: request,
    fetcher
  })
}

export async function renewProductSubscription(
  tenantId: string,
  subscriptionId: string,
  request: ProductSubscriptionMutation,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionResponse> {
  return requestJson(
    `/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/${encodeURIComponent(subscriptionId)}/renew`,
    {
      method: 'POST',
      body: request,
      fetcher
    }
  )
}

export async function suspendProductSubscription(
  tenantId: string,
  subscriptionId: string,
  request: ProductSubscriptionStatusMutation,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionResponse> {
  return requestJson(
    `/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/${encodeURIComponent(subscriptionId)}/suspend`,
    {
      method: 'POST',
      body: request,
      fetcher
    }
  )
}

export async function cancelProductSubscription(
  tenantId: string,
  subscriptionId: string,
  request: ProductSubscriptionStatusMutation,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionResponse> {
  return requestJson(
    `/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/${encodeURIComponent(subscriptionId)}/cancel`,
    {
      method: 'POST',
      body: request,
      fetcher
    }
  )
}

export async function convertLegacyProductSubscription(
  tenantId: string,
  subscriptionId: string,
  request: ProductSubscriptionMutation,
  fetcher?: PlatformBillingFetcher
): Promise<TenantProductSubscriptionResponse> {
  return requestJson(
    `/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/product-subscriptions/${encodeURIComponent(subscriptionId)}/convert-from-legacy`,
    {
      method: 'POST',
      body: request,
      fetcher
    }
  )
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: PlatformBillingFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new PlatformBillingApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isPlatformBillingApiErrorResponse(payload)) {
    throw new PlatformBillingApiError(
      response.status,
      isPlatformBillingApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: PlatformBillingFetcher
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

function resolveFetch(): PlatformBillingFetcher | undefined {
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

function isPlatformBillingApiErrorResponse(payload: unknown): payload is PlatformBillingApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<PlatformBillingApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): PlatformBillingApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'platform.billing.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
