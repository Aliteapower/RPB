import type {
  I18nCatalogApiErrorResponse,
  I18nCatalogResponse,
  PlatformI18nCatalogMutation,
  TenantAdminI18nCatalogMutation
} from '../types/i18nCatalog'

export class I18nCatalogApiError extends Error {
  readonly status: number
  readonly response: I18nCatalogApiErrorResponse

  constructor(status: number, response: I18nCatalogApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'I18nCatalogApiError'
    this.status = status
    this.response = response
  }
}

type I18nCatalogFetcher = typeof fetch

export async function getPlatformI18nCatalog(fetcher?: I18nCatalogFetcher): Promise<I18nCatalogResponse> {
  return requestJson('/api/v1/platform/i18n/catalog', { method: 'GET', fetcher })
}

export async function updatePlatformI18nCatalog(
  request: PlatformI18nCatalogMutation,
  fetcher?: I18nCatalogFetcher
): Promise<I18nCatalogResponse> {
  return requestJson('/api/v1/platform/i18n/catalog', { method: 'PATCH', body: request, fetcher })
}

export async function getTenantAdminI18nCatalog(
  storeId: string,
  fetcher?: I18nCatalogFetcher
): Promise<I18nCatalogResponse> {
  return requestJson(tenantEndpoint(storeId), { method: 'GET', fetcher })
}

export async function updateTenantAdminI18nCatalog(
  storeId: string,
  request: TenantAdminI18nCatalogMutation,
  fetcher?: I18nCatalogFetcher
): Promise<I18nCatalogResponse> {
  return requestJson(tenantEndpoint(storeId), { method: 'PATCH', body: request, fetcher })
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'PATCH'
    body?: unknown
    fetcher?: I18nCatalogFetcher
  }
): Promise<T> {
  let response: Response

  try {
    response = await (options.fetcher ?? fetch)(endpoint, {
      method: options.method,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    })
  } catch {
    throw new I18nCatalogApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isI18nCatalogApiErrorResponse(payload)) {
    throw new I18nCatalogApiError(
      response.status,
      isI18nCatalogApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

function tenantEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/i18n/catalog`
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

function isI18nCatalogApiErrorResponse(payload: unknown): payload is I18nCatalogApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<I18nCatalogApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): I18nCatalogApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'i18n.catalog.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
