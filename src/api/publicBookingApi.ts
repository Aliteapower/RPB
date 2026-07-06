import type {
  CustomerAuthEmailCodeResponse,
  CustomerAuthLoginResponse,
  CustomerAuthMeResponse,
  PublicBookingContextResponse,
  PublicBookingCreateMutation,
  PublicBookingCreateResponse,
  PublicBookingEntryResponse,
  PublicBookingErrorResponse,
  TenantAdminCustomerEmailSettingsMutation,
  TenantAdminCustomerEmailSettingsResponse,
  TenantAdminCustomerOAuthProviderSettingsMutation,
  TenantAdminCustomerOAuthProviderSettingsResponse,
  TenantAdminPublicBookingAvailabilityRuleMutation,
  TenantAdminPublicBookingAvailabilityRuleResponse,
  TenantAdminPublicBookingAvailabilityRulesResponse,
  TenantAdminPublicBookingQuotaOverrideMutation,
  TenantAdminPublicBookingQuotaOverrideResponse,
  TenantAdminPublicBookingSettingsMutation,
  TenantAdminPublicBookingSettingsResponse
} from '../types/publicBooking'

export class PublicBookingApiError extends Error {
  readonly status: number
  readonly response: PublicBookingErrorResponse

  constructor(status: number, response: PublicBookingErrorResponse) {
    super(response.error)
    this.name = 'PublicBookingApiError'
    this.status = status
    this.response = response
  }
}

type PublicBookingFetcher = typeof fetch

export async function getPublicBookingContext(
  storeId: string,
  businessDate: string,
  fetcher?: PublicBookingFetcher
): Promise<PublicBookingContextResponse> {
  const params = new URLSearchParams()
  if (businessDate) {
    params.set('businessDate', businessDate)
  }
  const query = params.toString()
  return requestJson(publicBookingEndpoint(storeId, `/context${query ? `?${query}` : ''}`), {
    method: 'GET',
    fetcher
  })
}

export async function resolveTenantPublicBookingEntry(
  fetcher?: PublicBookingFetcher
): Promise<PublicBookingEntryResponse> {
  return requestJson('/api/v1/public/booking-entry', {
    method: 'GET',
    fetcher
  })
}

export async function requestCustomerEmailCode(
  storeId: string,
  email: string,
  fetcher?: PublicBookingFetcher
): Promise<CustomerAuthEmailCodeResponse> {
  return requestJson('/api/v1/public/customer-auth/email-code', {
    method: 'POST',
    body: { storeId, email },
    fetcher
  })
}

export async function loginCustomerWithEmail(
  storeId: string,
  email: string,
  code: string,
  displayName: string,
  fetcher?: PublicBookingFetcher
): Promise<CustomerAuthLoginResponse> {
  return requestJson('/api/v1/public/customer-auth/email-login', {
    method: 'POST',
    body: { storeId, email, code, displayName },
    fetcher
  })
}

export async function loginCustomerWithOAuth(
  storeId: string,
  provider: 'google' | 'facebook',
  token: string,
  fetcher?: PublicBookingFetcher
): Promise<CustomerAuthLoginResponse> {
  return requestJson('/api/v1/public/customer-auth/oauth-login', {
    method: 'POST',
    body: { storeId, provider, token },
    fetcher
  })
}

export async function getCustomerMe(fetcher?: PublicBookingFetcher): Promise<CustomerAuthMeResponse> {
  return requestJson('/api/v1/public/customer-auth/me', { method: 'GET', fetcher })
}

export async function createPublicBooking(
  storeId: string,
  idempotencyKey: string,
  request: PublicBookingCreateMutation,
  fetcher?: PublicBookingFetcher
): Promise<PublicBookingCreateResponse> {
  return requestJson(publicBookingEndpoint(storeId, '/reservations'), {
    method: 'POST',
    body: request,
    idempotencyKey,
    fetcher
  })
}

export async function getTenantAdminPublicBookingSettings(
  storeId: string,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminPublicBookingSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/settings'), { method: 'GET', fetcher })
}

export async function updateTenantAdminPublicBookingSettings(
  storeId: string,
  request: TenantAdminPublicBookingSettingsMutation,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminPublicBookingSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/settings'), {
    method: 'PUT',
    body: request,
    fetcher
  })
}

export async function updateTenantAdminPublicBookingQuotaOverride(
  storeId: string,
  request: TenantAdminPublicBookingQuotaOverrideMutation,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminPublicBookingQuotaOverrideResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/quota-overrides'), {
    method: 'PUT',
    body: request,
    fetcher
  })
}

export async function getTenantAdminPublicBookingAvailabilityRules(
  storeId: string,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminPublicBookingAvailabilityRulesResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/availability-rules'), {
    method: 'GET',
    fetcher
  })
}

export async function updateTenantAdminPublicBookingAvailabilityRule(
  storeId: string,
  request: TenantAdminPublicBookingAvailabilityRuleMutation,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminPublicBookingAvailabilityRuleResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/availability-rules'), {
    method: 'PUT',
    body: request,
    fetcher
  })
}

export async function deleteTenantAdminPublicBookingAvailabilityRule(
  storeId: string,
  ruleId: string,
  fetcher?: PublicBookingFetcher
): Promise<void> {
  await requestJson<unknown>(tenantAdminPublicBookingEndpoint(storeId, `/availability-rules/${ruleId}`), {
    method: 'DELETE',
    fetcher
  })
}

export async function getTenantAdminCustomerEmailSettings(
  storeId: string,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminCustomerEmailSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/customer-auth/email-settings'), {
    method: 'GET',
    fetcher
  })
}

export async function updateTenantAdminCustomerEmailSettings(
  storeId: string,
  request: TenantAdminCustomerEmailSettingsMutation,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminCustomerEmailSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, '/customer-auth/email-settings'), {
    method: 'PUT',
    body: request,
    fetcher
  })
}

export async function getTenantAdminCustomerOAuthProviderSettings(
  storeId: string,
  provider: 'google' | 'facebook',
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminCustomerOAuthProviderSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, `/customer-auth/oauth-providers/${provider}`), {
    method: 'GET',
    fetcher
  })
}

export async function updateTenantAdminCustomerOAuthProviderSettings(
  storeId: string,
  provider: 'google' | 'facebook',
  request: TenantAdminCustomerOAuthProviderSettingsMutation,
  fetcher?: PublicBookingFetcher
): Promise<TenantAdminCustomerOAuthProviderSettingsResponse> {
  return requestJson(tenantAdminPublicBookingEndpoint(storeId, `/customer-auth/oauth-providers/${provider}`), {
    method: 'PUT',
    body: request,
    fetcher
  })
}

async function requestJson<T>(
  endpoint: string,
  options: {
    method: 'GET' | 'POST' | 'PUT' | 'DELETE'
    body?: unknown
    idempotencyKey?: string
    fetcher?: PublicBookingFetcher
  }
): Promise<T> {
  let response: Response

  try {
    response = await (options.fetcher ?? fetch)(endpoint, {
      method: options.method,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(options.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : {})
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    })
  } catch {
    throw new PublicBookingApiError(0, { success: false, error: 'network_failure' })
  }

  const payload = await readJson(response)
  if (!response.ok || isPublicBookingErrorResponse(payload)) {
    throw new PublicBookingApiError(
      response.status,
      isPublicBookingErrorResponse(payload)
        ? payload
        : { success: false, error: 'request_failed' }
    )
  }

  return payload as T
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

function isPublicBookingErrorResponse(payload: unknown): payload is PublicBookingErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const candidate = payload as Partial<PublicBookingErrorResponse>
  return candidate.success === false && typeof candidate.error === 'string'
}

function publicBookingEndpoint(storeId: string, suffix: string): string {
  return `/api/v1/public/stores/${encodeURIComponent(storeId)}/booking${suffix}`
}

function tenantAdminPublicBookingEndpoint(storeId: string, suffix: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/public-booking${suffix}`
}
