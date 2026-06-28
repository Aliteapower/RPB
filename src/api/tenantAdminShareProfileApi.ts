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

export async function getTenantAdminShareProfile(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  return requestJson(shareProfileEndpoint(storeId), { method: 'GET', fetcher })
}

export async function updateTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  return requestJson(shareProfileEndpoint(storeId), { method: 'PATCH', body: request, fetcher })
}

export async function updateTenantAdminShareProfileTemplate(
  storeId: string,
  request: TenantAdminShareTemplateMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  return requestJson(`${shareProfileEndpoint(storeId)}/template`, { method: 'PATCH', body: request, fetcher })
}

export async function previewTenantAdminShareProfile(
  storeId: string,
  request: TenantAdminShareProfileMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSharePreviewResponse> {
  return requestJson(`${shareProfileEndpoint(storeId)}/preview`, { method: 'POST', body: request, fetcher })
}

export async function resetTenantAdminShareProfileTemplate(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminShareProfileResponse> {
  return requestJson(`${shareProfileEndpoint(storeId)}/default-template`, { method: 'POST', fetcher })
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

function shareProfileEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/share-profile`
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
