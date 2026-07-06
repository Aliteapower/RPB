import {
  TenantAdminApiError,
  type TenantAdminApiErrorResponse
} from './tenantAdminApi'
import type {
  TenantAdminShareProfileMutation,
  TenantAdminShareProfileResponse,
  TenantAdminShareTemplateMutation,
  TenantAdminSharePreviewResponse
} from '../types/tenantAdminShareProfile'

type TenantAdminFetcher = typeof fetch
interface TenantAdminRequestArgs {
  locale?: string
  fetcher?: TenantAdminFetcher
}

export async function getTenantAdminShareProfile(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function getTenantAdminShareProfile(
  storeId: string,
  locale?: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function getTenantAdminShareProfile(
  storeId: string,
  localeOrFetcher?: string | TenantAdminFetcher,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  const args = requestArgs(localeOrFetcher, fetcher)
  return requestJson(shareProfileEndpoint(storeId, args.locale), { method: 'GET', fetcher: args.fetcher })
}

export async function updateTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function updateTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  locale?: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function updateTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  localeOrFetcher?: string | TenantAdminFetcher,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  const args = requestArgs(localeOrFetcher, fetcher)
  return requestJson(shareProfileEndpoint(storeId, args.locale), { method: 'PATCH', body: request, fetcher: args.fetcher })
}

export async function updateTenantAdminShareProfileTemplate(
  storeId: string,
  request: TenantAdminShareTemplateMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function updateTenantAdminShareProfileTemplate(
  storeId: string,
  request: TenantAdminShareTemplateMutation,
  locale?: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function updateTenantAdminShareProfileTemplate(
  storeId: string,
  request: TenantAdminShareTemplateMutation,
  localeOrFetcher?: string | TenantAdminFetcher,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  const args = requestArgs(localeOrFetcher, fetcher)
  return requestJson(shareProfileEndpoint(storeId, args.locale, '/template'), { method: 'PATCH', body: request, fetcher: args.fetcher })
}

export async function previewTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSharePreviewResponse>
export async function previewTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  locale?: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSharePreviewResponse>
export async function previewTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  localeOrFetcher?: string | TenantAdminFetcher,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSharePreviewResponse> {
  const args = requestArgs(localeOrFetcher, fetcher)
  return requestJson(shareProfileEndpoint(storeId, args.locale, '/preview'), { method: 'POST', body: request, fetcher: args.fetcher })
}

export async function resetTenantAdminShareProfileTemplate(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function resetTenantAdminShareProfileTemplate(
  storeId: string,
  locale?: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse>
export async function resetTenantAdminShareProfileTemplate(
  storeId: string,
  localeOrFetcher?: string | TenantAdminFetcher,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  const args = requestArgs(localeOrFetcher, fetcher)
  return requestJson(shareProfileEndpoint(storeId, args.locale, '/default-template'), { method: 'POST', fetcher: args.fetcher })
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: TenantAdminFetcher
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
    throw new TenantAdminApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isTenantAdminApiErrorResponse(payload)) {
    throw new TenantAdminApiError(
      response.status,
      isTenantAdminApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return payload as T
}

function requestArgs(localeOrFetcher?: string | TenantAdminFetcher, fetcher?: TenantAdminFetcher): TenantAdminRequestArgs {
  if (typeof localeOrFetcher === 'function') {
    return { fetcher: localeOrFetcher }
  }
  return { locale: localeOrFetcher, fetcher }
}

function shareProfileEndpoint(storeId: string, locale?: string, pathSuffix = ''): string {
  const searchParams = new URLSearchParams()
  if (locale?.trim()) {
    searchParams.set('locale', locale.trim())
  }
  const suffix = searchParams.toString() ? `?${searchParams.toString()}` : ''
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/share-profile${pathSuffix}${suffix}`
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

function isTenantAdminApiErrorResponse(payload: unknown): payload is TenantAdminApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TenantAdminApiErrorResponse>
  return candidate.success === false && typeof candidate.error?.code === 'string'
}

function unknownError(httpStatus?: number): TenantAdminApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'tenant.admin.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
