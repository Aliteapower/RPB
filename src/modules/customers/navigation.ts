import { toCountryPhoneE164 } from '../../utils/countryPhone'

export type CustomerEntrySource = 'wallet' | 'wallet-recharge'

export function customerManagementTarget(storeId:string, source:CustomerEntrySource, keyword='', create=false):string {
  const query=new URLSearchParams({from:source})
  if(keyword.trim())query.set('keyword',keyword.trim())
  if(create)query.set('create','1')
  return `/stores/${encodeURIComponent(storeId)}/admin/customers?${query}`
}

// A fixed source enum keeps the return link inside this store; no arbitrary return URL is accepted.
export function customerManagementReturnTarget(storeId:string, source:unknown):string|null {
  const base=`/stores/${encodeURIComponent(storeId)}/wallet`
  return source==='wallet-recharge'?`${base}/topup?entry=phone`:source==='wallet'?base:null
}

export function customerPhonePrefill(keyword:string):string {
  const value=keyword.replace(/[\s()\-]/g,'')
  if(/^\+\d{8,15}$/.test(value))return value
  return /^\d{8}$/.test(value)?toCountryPhoneE164(value)??'':''
}
