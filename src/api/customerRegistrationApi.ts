import type {CustomerPrivacyNotice,TenantAdminCustomerMutation} from './tenantAdminApi'
export class CustomerRegistrationApiError extends Error {constructor(readonly code:string){super(code)}}
async function request<T>(path:string,body?:unknown):Promise<T>{
 try{const response=await fetch('/api/v1/public/customer-auth/'+path,{method:body?'POST':'GET',credentials:'same-origin',headers:{Accept:'application/json',...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});const data=await response.json();if(!response.ok)throw new CustomerRegistrationApiError(data?.error??'request_failed');return data as T}
 catch(e){if(e instanceof CustomerRegistrationApiError)throw e;throw new CustomerRegistrationApiError('request_failed')}
}
export const getRegistrationNotice=(storeId:string)=>request<CustomerPrivacyNotice>('registration-notice?storeId='+encodeURIComponent(storeId))
export const registerCustomer=(storeId:string,profile:TenantAdminCustomerMutation,noticeVersion:string,accepted:boolean)=>request<{success:true;customerId:string}>('register',{storeId,...profile,privacyConsent:{accepted,noticeVersion}})
