import { toCountryPhoneE164 } from '../../utils/countryPhone'

export function customerPhonePrefill(keyword:string):string {
  const value=keyword.replace(/[\s()\-]/g,'')
  if(/^\+\d{8,15}$/.test(value))return value
  return /^\d{8}$/.test(value)?toCountryPhoneE164(value)??'':''
}
