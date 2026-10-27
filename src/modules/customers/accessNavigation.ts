export type CustomerAccessSource='wallet'|'wallet-settings'|'cards'|'storage'|'booking'|'admin'|'staff'
const sources:CustomerAccessSource[]=['wallet','wallet-settings','cards','storage','booking','admin','staff']
export function customerAccessTarget(store:string,source:CustomerAccessSource,kind:'activation'|'email'){
 const query=new URLSearchParams({accessSource:source});if(kind==='activation')query.set('action','activation')
 return `/stores/${encodeURIComponent(store)}/admin/${kind==='activation'?'customers':'customer-email'}?${query}`
}
export function customerAccessReturnTarget(store:string,source:unknown){
 const base=`/stores/${encodeURIComponent(store)}`
 const paths:Record<CustomerAccessSource,string>={wallet:'/wallet/more','wallet-settings':'/wallet/settings',cards:'/wallet/cards',storage:'/wine-storage?task=settings',booking:'/admin/public-booking',admin:'/admin/profile',staff:'/staff'}
 return base+paths[sources.includes(source as CustomerAccessSource)?source as CustomerAccessSource:'staff']
}
