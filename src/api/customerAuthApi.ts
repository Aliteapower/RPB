import type { CustomerAuthLoginResponse } from '../types/publicBooking'

export class CustomerPasswordApiError extends Error {
  constructor(readonly status: number, readonly code: string) { super(code) }
}

async function command(path: string, body: unknown): Promise<CustomerAuthLoginResponse> {
  let response: Response
  try {
    response = await fetch(`/api/v1/public/customer-auth/${path}`, {
      method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body)
    })
  } catch { throw new CustomerPasswordApiError(0, 'network_failure') }
  const data = await response.json().catch(() => null)
  if (!response.ok || data?.success !== true) throw new CustomerPasswordApiError(response.status, data?.error || 'request_failed')
  return data
}

export function loginCustomerWithPassword(storeId: string, phoneE164: string, password: string) {
  return command('password-login', { storeId, phoneE164, password })
}
export function changeCustomerPassword(currentPassword: string, newPassword: string) {
  return command('password-change', { currentPassword, newPassword })
}
export function logoutCustomer() {
  return command('logout', {})
}
