import type {
  CustomerPhoneLookupApiErrorResponse,
  CustomerPhoneLookupResponse
} from '../types/customerPhoneLookup'

export class CustomerPhoneLookupApiError extends Error {
  readonly status: number
  readonly response: CustomerPhoneLookupApiErrorResponse

  constructor(status: number, response: CustomerPhoneLookupApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'CustomerPhoneLookupApiError'
    this.status = status
    this.response = response
  }
}

export async function lookupCustomerByPhone(
  storeId: string,
  phoneE164: string,
  fetcher: typeof fetch = fetch
): Promise<CustomerPhoneLookupResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/customers/phone-lookup?phoneE164=${encodeURIComponent(phoneE164)}`
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new CustomerPhoneLookupApiError(0, createUnknownErrorResponse())
  }

  const payload = await readJson(response)

  if (!response.ok || isCustomerPhoneLookupApiErrorResponse(payload)) {
    const apiError = isCustomerPhoneLookupApiErrorResponse(payload)
      ? payload
      : createUnknownErrorResponse(response.status)

    throw new CustomerPhoneLookupApiError(response.status, apiError)
  }

  if (!isCustomerPhoneLookupResponse(payload)) {
    throw new CustomerPhoneLookupApiError(response.status, createUnknownErrorResponse(response.status))
  }

  return payload
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

function createUnknownErrorResponse(httpStatus?: number): CustomerPhoneLookupApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'customer.phone_lookup.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isCustomerPhoneLookupApiErrorResponse(
  payload: unknown
): payload is CustomerPhoneLookupApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CustomerPhoneLookupApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isCustomerPhoneLookupResponse(payload: unknown): payload is CustomerPhoneLookupResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<CustomerPhoneLookupResponse>
  return (
    candidate.success === true &&
    typeof candidate.found === 'boolean' &&
    (!candidate.found ||
      (!!candidate.customer &&
        typeof candidate.customer.customerId === 'string' &&
        typeof candidate.customer.phoneE164 === 'string'))
  )
}
