export interface TenantAdminPage {
  limit: number
  offset: number
  total: number
}

export interface TenantAdminStaff {
  id: string
  employeeNo: string
  name: string
  phone: string | null
  email: string | null
  status: 'active' | 'disabled' | 'locked'
  createdAt: string
  updatedAt: string
}

export interface TenantAdminStaffMutation {
  employeeNo?: string
  name: string
  phone?: string | null
  email?: string | null
  status?: 'active' | 'disabled' | 'locked'
  password?: string | null
}

export interface TenantAdminTable {
  id: string
  areaId: string
  areaName: string
  areaSortOrder: number
  tableCode: string
  tableSortOrder: number
  capacity: number
  status: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface TenantAdminTableMutation {
  areaName: string
  tableCode: string
  capacity: number
  enabled: boolean
  areaSortOrder?: number
  tableSortOrder?: number
}

export interface TenantAdminSettings {
  storeName: string
  timezone: string
  locale: string
  dateFormat: string
  timeFormat: string
  currency: string
  reservationHoldMinutes: number
  queueCallHoldMinutes: number
  expectedDiningMinutes: number
}

export interface TenantAdminListQuery {
  keyword?: string
  limit?: number
  offset?: number
}

export interface TenantAdminStaffListResponse {
  success: true
  staff: TenantAdminStaff[]
  page: TenantAdminPage
}

export interface TenantAdminStaffResponse {
  success: true
  staff: TenantAdminStaff
}

export interface TenantAdminTableListResponse {
  success: true
  tables: TenantAdminTable[]
  page: TenantAdminPage
}

export interface TenantAdminTableResponse {
  success: true
  table: TenantAdminTable
}

export interface TenantAdminTableImportResponse {
  success: true
  imported: {
    totalRows: number
    created: number
    updated: number
  }
}

export interface TenantAdminSettingsResponse {
  success: true
  settings: TenantAdminSettings
}

export interface TenantAdminApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}

export class TenantAdminApiError extends Error {
  readonly status: number
  readonly response: TenantAdminApiErrorResponse

  constructor(status: number, response: TenantAdminApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'TenantAdminApiError'
    this.status = status
    this.response = response
  }
}

type TenantAdminFetcher = typeof fetch

interface TextResponse {
  readonly ok: boolean
  readonly status: number
  text(): Promise<string>
}

interface BlobResponse extends TextResponse {
  blob(): Promise<Blob>
}

export async function listStaff(
  storeId: string,
  query: TenantAdminListQuery = {},
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminStaffListResponse> {
  return requestJson(buildListEndpoint(storeId, '/tenant-admin/staff', query), { method: 'GET', fetcher })
}

export async function getStaff(
  storeId: string,
  staffId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminStaffResponse> {
  return requestJson(`${baseEndpoint(storeId)}/staff/${encodeURIComponent(staffId)}`, { method: 'GET', fetcher })
}

export async function createStaff(
  storeId: string,
  request: TenantAdminStaffMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminStaffResponse> {
  return requestJson(`${baseEndpoint(storeId)}/staff`, { method: 'POST', body: request, fetcher })
}

export async function updateStaff(
  storeId: string,
  staffId: string,
  request: TenantAdminStaffMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminStaffResponse> {
  return requestJson(`${baseEndpoint(storeId)}/staff/${encodeURIComponent(staffId)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function listTables(
  storeId: string,
  query: TenantAdminListQuery = {},
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminTableListResponse> {
  return requestJson(buildListEndpoint(storeId, '/tenant-admin/tables', query), { method: 'GET', fetcher })
}

export async function getTable(
  storeId: string,
  tableId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminTableResponse> {
  return requestJson(`${baseEndpoint(storeId)}/tables/${encodeURIComponent(tableId)}`, { method: 'GET', fetcher })
}

export async function createTable(
  storeId: string,
  request: TenantAdminTableMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminTableResponse> {
  return requestJson(`${baseEndpoint(storeId)}/tables`, { method: 'POST', body: request, fetcher })
}

export async function updateTable(
  storeId: string,
  tableId: string,
  request: TenantAdminTableMutation,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminTableResponse> {
  return requestJson(`${baseEndpoint(storeId)}/tables/${encodeURIComponent(tableId)}`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

export async function exportTables(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<Blob> {
  return requestBlob(`${baseEndpoint(storeId)}/tables/export`, fetcher)
}

export async function importTables(
  storeId: string,
  file: File,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminTableImportResponse> {
  const form = new FormData()
  form.append('file', file)
  return requestForm(`${baseEndpoint(storeId)}/tables/import`, form, fetcher)
}

export async function getSettings(
  storeId: string,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSettingsResponse> {
  return requestJson(`/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/settings`, { method: 'GET', fetcher })
}

export async function updateSettings(
  storeId: string,
  request: TenantAdminSettings,
  fetcher?: TenantAdminFetcher
): Promise<TenantAdminSettingsResponse> {
  return requestJson(`/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/settings`, {
    method: 'PATCH',
    body: request,
    fetcher
  })
}

function baseEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin`
}

function buildListEndpoint(storeId: string, pathSuffix: string, query: TenantAdminListQuery): string {
  const params = new URLSearchParams()
  const keyword = query.keyword?.trim()
  if (keyword) {
    params.set('keyword', keyword)
  }
  setNumberParam(params, 'limit', query.limit)
  setNumberParam(params, 'offset', query.offset)
  const queryString = params.toString()
  return `/api/v1/stores/${encodeURIComponent(storeId)}${pathSuffix}${queryString ? `?${queryString}` : ''}`
}

function setNumberParam(params: URLSearchParams, key: string, value: number | undefined): void {
  if (typeof value === 'number' && Number.isFinite(value)) {
    params.set(key, String(value))
  }
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: TenantAdminFetcher
  }
): Promise<T> {
  let response: TextResponse

  try {
    response = await sendRequest(endpoint, options)
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

async function requestForm<T>(
  endpoint: string,
  form: FormData,
  fetcher?: TenantAdminFetcher
): Promise<T> {
  let response: TextResponse

  try {
    const activeFetcher = fetcher ?? resolveFetch()
    if (activeFetcher) {
      response = await activeFetcher(endpoint, {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
        body: form
      })
    } else {
      response = await xhrFormRequest(endpoint, form)
    }
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

async function requestBlob(endpoint: string, fetcher?: TenantAdminFetcher): Promise<Blob> {
  let response: BlobResponse

  try {
    const activeFetcher = fetcher ?? resolveFetch()
    if (activeFetcher) {
      response = await activeFetcher(endpoint, {
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
      })
    } else {
      response = await xhrBlobRequest(endpoint)
    }
  } catch {
    throw new TenantAdminApiError(0, unknownError())
  }

  if (!response.ok) {
    const payload = await readJson(response)
    throw new TenantAdminApiError(
      response.status,
      isTenantAdminApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  return response.blob()
}

async function sendRequest(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PATCH'
    body?: unknown
    fetcher?: TenantAdminFetcher
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

function resolveFetch(): TenantAdminFetcher | undefined {
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

function xhrFormRequest(endpoint: string, form: FormData): Promise<TextResponse> {
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
    xhr.send(form)
  })
}

function xhrBlobRequest(endpoint: string): Promise<BlobResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', endpoint, true)
    xhr.withCredentials = true
    xhr.responseType = 'blob'
    xhr.setRequestHeader('Accept', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    xhr.onload = () => {
      const blob = xhr.response as Blob
      resolve({
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        text: async () => blob.text(),
        blob: async () => blob
      })
    }
    xhr.onerror = () => reject(new TypeError('Network request failed'))
    xhr.ontimeout = () => reject(new TypeError('Network request timed out'))
    xhr.send()
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

function isTenantAdminApiErrorResponse(payload: unknown): payload is TenantAdminApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<TenantAdminApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
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
