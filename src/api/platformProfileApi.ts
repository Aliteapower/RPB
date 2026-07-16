export interface PlatformSocialLink {
  id: string
  displayName: string
  url: string
  logoMediaUrl: string | null
  sortOrder: number
  status: 'active' | 'disabled'
  createdAt: string
  updatedAt: string
  version: number
}

export interface PlatformProfile {
  platformName: string
  uen: string | null
  address: string | null
  phone: string | null
  email: string | null
  website: string | null
  logoMediaUrl: string | null
  createdAt: string
  updatedAt: string
  version: number
  socialLinks: PlatformSocialLink[]
}

export interface PlatformProfileMutation {
  platformName: string
  uen?: string | null
  address?: string | null
  phone?: string | null
  email?: string | null
  website?: string | null
}

export interface PlatformSocialLinkMutation {
  displayName: string
  url: string
  sortOrder?: number | null
  status?: 'active' | 'disabled'
}

export interface PlatformProfileResponse {
  success: true
  profile: PlatformProfile
}

export interface PlatformProfileApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}

export class PlatformProfileApiError extends Error {
  readonly status: number
  readonly response: PlatformProfileApiErrorResponse

  constructor(status: number, response: PlatformProfileApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'PlatformProfileApiError'
    this.status = status
    this.response = response
  }
}

type Fetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

export function getPlatformProfile(fetcher?: Fetcher): Promise<PlatformProfileResponse> {
  return requestJson('/api/v1/platform/profile', { method: 'GET', fetcher })
}

export function updatePlatformProfile(
  request: PlatformProfileMutation,
  fetcher?: Fetcher
): Promise<PlatformProfileResponse> {
  return requestJson('/api/v1/platform/profile', { method: 'PATCH', body: request, fetcher })
}

export function uploadPlatformProfileLogo(file: File, fetcher?: Fetcher): Promise<PlatformProfileResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return requestForm('/api/v1/platform/profile/logo', { method: 'POST', body: formData, fetcher })
}

export function clearPlatformProfileLogo(fetcher?: Fetcher): Promise<PlatformProfileResponse> {
  return requestJson('/api/v1/platform/profile/logo', { method: 'DELETE', fetcher })
}

export function createPlatformSocialLink(
  request: PlatformSocialLinkMutation,
  fetcher?: Fetcher
): Promise<PlatformProfileResponse> {
  return requestJson('/api/v1/platform/profile/social-links', { method: 'POST', body: request, fetcher })
}

export function updatePlatformSocialLink(
  linkId: string,
  request: PlatformSocialLinkMutation,
  fetcher?: Fetcher
): Promise<PlatformProfileResponse> {
  return requestJson(`/api/v1/platform/profile/social-links/${encodeURIComponent(linkId)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export function deletePlatformSocialLink(linkId: string, fetcher?: Fetcher): Promise<PlatformProfileResponse> {
  return requestJson(`/api/v1/platform/profile/social-links/${encodeURIComponent(linkId)}`, {
    method: 'DELETE',
    fetcher
  })
}

export function uploadPlatformSocialLinkLogo(
  linkId: string,
  file: File,
  fetcher?: Fetcher
): Promise<PlatformProfileResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return requestForm(`/api/v1/platform/profile/social-links/${encodeURIComponent(linkId)}/logo`, {
    method: 'POST',
    body: formData,
    fetcher
  })
}

export function clearPlatformSocialLinkLogo(linkId: string, fetcher?: Fetcher): Promise<PlatformProfileResponse> {
  return requestJson(`/api/v1/platform/profile/social-links/${encodeURIComponent(linkId)}/logo`, {
    method: 'DELETE',
    fetcher
  })
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    body?: unknown
    fetcher?: Fetcher
  }
): Promise<T> {
  const headers = {
    Accept: 'application/json',
    ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
  }
  const body = options.body === undefined ? undefined : JSON.stringify(options.body)
  return request(endpoint, { method: options.method, headers, body, fetcher: options.fetcher })
}

async function requestForm<T>(
  endpoint: string,
  options: {
    method: 'POST' | 'PATCH'
    body: FormData
    fetcher?: Fetcher
  }
): Promise<T> {
  return request(endpoint, {
    method: options.method,
    headers: { Accept: 'application/json' },
    body: options.body,
    fetcher: options.fetcher
  })
}

async function request<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    headers: Record<string, string>
    body?: string | FormData
    fetcher?: Fetcher
  }
): Promise<T> {
  let response: TextResponse
  try {
    response = await send(endpoint, options)
  } catch {
    throw new PlatformProfileApiError(0, unknownError())
  }

  const payload = await readJson(response)
  if (!response.ok || isErrorResponse(payload)) {
    throw new PlatformProfileApiError(response.status, isErrorResponse(payload) ? payload : unknownError(response.status))
  }

  return payload as T
}

function send(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
    headers: Record<string, string>
    body?: string | FormData
    fetcher?: Fetcher
  }
): Promise<TextResponse> {
  const fetcher = options.fetcher ?? (typeof globalThis.fetch === 'function' ? globalThis.fetch.bind(globalThis) : undefined)
  if (fetcher) {
    return fetcher(endpoint, {
      method: options.method,
      credentials: 'include',
      headers: options.headers,
      body: options.body
    })
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(options.method, endpoint, true)
    xhr.withCredentials = true
    Object.entries(options.headers).forEach(([name, value]) => xhr.setRequestHeader(name, value))
    xhr.onload = () => resolve({
      ok: xhr.status >= 200 && xhr.status < 300,
      status: xhr.status,
      text: async () => xhr.responseText
    })
    xhr.onerror = () => reject(new TypeError('Network request failed'))
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

function isErrorResponse(payload: unknown): payload is PlatformProfileApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<PlatformProfileApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function unknownError(httpStatus?: number): PlatformProfileApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'platform.profile.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}
