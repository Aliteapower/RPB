export interface CustomerPhoneLookupCustomer {
  customerId: string
  displayName?: string | null
  nickname?: string | null
  phoneE164: string
}

export interface CustomerPhoneLookupResponse {
  success: true
  found: boolean
  customer?: CustomerPhoneLookupCustomer | null
}

export interface CustomerPhoneLookupApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface CustomerPhoneLookupApiErrorResponse {
  success: false
  error: CustomerPhoneLookupApiErrorBody
}

export type CustomerPhoneLookupApiResponse =
  | CustomerPhoneLookupResponse
  | CustomerPhoneLookupApiErrorResponse
