export interface PlatformTenant {
  id: string
  tenantCode: string
  displayName: string
  status: TenantStatus
  defaultLocale: string | null
  contactPhone: string | null
  address: string | null
  principalName: string | null
  logoMediaUrl: string | null
  deleted: boolean
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export type TenantStatus = 'created' | 'active' | 'suspended' | 'closed'
export type TenantListStatus = 'all' | 'active' | 'deleted'

export interface PlatformTenantMutation {
  tenantCode?: string
  displayName: string
  status: TenantStatus
  defaultLocale?: string | null
  contactPhone?: string | null
  address?: string | null
  principalName?: string | null
  initialPassword?: string | null
  password?: string | null
}

export interface PlatformTenantListQuery {
  keyword?: string
  status?: TenantListStatus
  includeDeleted?: boolean
  limit?: number
  offset?: number
}

export interface PlatformPage {
  limit: number
  offset: number
  total: number
}

export interface PlatformTenantListResponse {
  success: true
  tenants: PlatformTenant[]
  page: PlatformPage
}

export interface PlatformTenantResponse {
  success: true
  tenant: PlatformTenant
}

export interface PlatformApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}

export class PlatformApiError extends Error {
  readonly status: number
  readonly response: PlatformApiErrorResponse

  constructor(status: number, response: PlatformApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'PlatformApiError'
    this.status = status
    this.response = response
  }
}

type PlatformFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export async function listTenants(
  query: PlatformTenantListQuery = {},
  fetcher?: PlatformFetcher
): Promise<PlatformTenantListResponse> {
  return requestJson(buildListEndpoint(query), {
    method: 'GET',
    fetcher
  })
}

export async function getTenant(
  tenantId: string,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}`, {
    method: 'GET',
    fetcher
  })
}

export async function createTenant(
  request: PlatformTenantMutation,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson('/api/v1/platform/tenants', {
    method: 'POST',
    body: request,
    fetcher
  })
}

export async function updateTenant(
  tenantId: string,
  request: PlatformTenantMutation,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function deleteTenant(
  tenantId: string,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}`, {
    method: 'DELETE',
    fetcher
  })
}

export async function restoreTenant(
  tenantId: string,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/restore`, {
    method: 'POST',
    fetcher
  })
}

export async function uploadTenantLogo(
  tenantId: string,
  file: File,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return requestForm(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/logo`, {
    method: 'POST',
    body: formData,
    fetcher
  })
}

export async function clearTenantLogo(
  tenantId: string,
  fetcher?: PlatformFetcher
): Promise<PlatformTenantResponse> {
  return requestJson(`/api/v1/platform/tenants/${encodeURIComponent(tenantId)}/logo`, {
    method: 'DELETE',
    fetcher
  })
}

function buildListEndpoint(query: PlatformTenantListQuery): string {
  const params = new URLSearchParams()
  const keyword = query.keyword?.trim()
  if (keyword) {
    params.set('keyword', keyword)
  }
  if (query.status && query.status !== 'all') {
    params.set('status', query.status)
  }
  params.set('includeDeleted', query.includeDeleted === false ? 'false' : 'true')
  setNumberParam(params, 'limit', query.limit)
  setNumberParam(params, 'offset', query.offset)
  const queryString = params.toString()
  return queryString ? `/api/v1/platform/tenants?${queryString}` : '/api/v1/platform/tenants'
}

function setNumberParam(params: URLSearchParams, key: string, value: number | undefined): void {
  if (typeof value === 'number' && Number.isFinite(value)) {
    params.set(key, String(value))
  }
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    body?: unknown
    fetcher?: PlatformFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
  } catch {
    throw new PlatformApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isPlatformApiErrorResponse(payload)) {
    throw new PlatformApiError(
      response.status,
      isPlatformApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function requestForm<T>(
  endpoint: string,
  options: {
    method: 'POST' | 'PATCH'
    body: FormData
    fetcher?: PlatformFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendFormRequest(endpoint, options)
  } catch {
    throw new PlatformApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isPlatformApiErrorResponse(payload)) {
    throw new PlatformApiError(
      response.status,
      isPlatformApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    body?: unknown
    fetcher?: PlatformFetcher
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

async function sendFormRequest(
  endpoint: string,
  options: {
    method: 'POST' | 'PATCH'
    body: FormData
    fetcher?: PlatformFetcher
  }
): Promise<TextResponse> {
  const fetcher = options.fetcher ?? resolveFetch()

  if (fetcher) {
    return fetcher(endpoint, {
      method: options.method,
      credentials: 'include',
      headers: {
        Accept: 'application/json'
      },
      body: options.body
    })
  }

  return xhrRequest(endpoint, {
    method: options.method,
    headers: {
      Accept: 'application/json'
    },
    body: options.body
  })
}

function resolveFetch(): PlatformFetcher | undefined {
  const candidate = globalThis.fetch
  return typeof candidate === 'function' ? candidate.bind(globalThis) : undefined
}

function xhrRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    headers: Record<string, string>
    body?: string | FormData
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

function isPlatformApiErrorResponse(payload: unknown): payload is PlatformApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<PlatformApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): PlatformApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'platform.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
