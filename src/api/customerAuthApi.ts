import type { CustomerAuthLoginResponse } from '../types/publicBooking'

export class CustomerPasswordApiError extends Error {
  constructor(readonly status: number, readonly code: string) { super(code) }
}

async function command<T = CustomerAuthLoginResponse>(path: string, body: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/v1/public/customer-auth/${path}`, {
      method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-RPB-Customer-Auth': '1' },
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

export function requestRecoveryEmailCode(storeId: string, email: string) {
  return command<{success: true}>('recovery-email-code', { storeId, email })
}
export function confirmRecoveryEmail(storeId: string, email: string, code: string) {
  return command<{success: true}>('recovery-email-confirm', { storeId, email, code })
}
export function requestPasswordResetCode(storeId: string, phoneE164: string, email: string) {
  return command<{success: true}>('password-reset-code', { storeId, phoneE164, email })
}
export function resetCustomerPassword(storeId: string, phoneE164: string, email: string, code: string, newPassword: string) {
  return command<{success: true}>('password-reset', { storeId, phoneE164, email, code, newPassword })
}
export function activateCustomer(storeId: string, phoneE164: string, code: string, newPassword: string) {
  return command<{success:true}>('activate', {storeId,phoneE164,code,newPassword})
}
export function issueCustomerActivation(storeId:string, customerId:string, identityConfirmed:boolean) {
  return activationIssue(`/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/customers/${encodeURIComponent(customerId)}/activation-codes`,identityConfirmed)
}
async function activationIssue(path:string, identityConfirmed:boolean):Promise<{success:true;code:string;expiresAt:string}> {
  let response:Response
  try{response=await fetch(path,{method:'POST',credentials:'include',headers:{'Content-Type':'application/json',Accept:'application/json','X-RPB-Customer-Auth':'1'},body:JSON.stringify({identityConfirmed})})}
  catch{throw new CustomerPasswordApiError(0,'network_failure')}
  const data=await response.json().catch(()=>null)
  if(!response.ok||data?.success!==true)throw new CustomerPasswordApiError(response.status,typeof data?.error==='string'?data.error:'request_failed')
  return data
}
