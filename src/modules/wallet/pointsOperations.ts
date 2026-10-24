import type { Customer } from './types'
import type { TransactionDetail } from './transactions/types'
import { staffWalletPath } from './navigation'
export function canReversePoints(detail: Pick<TransactionDetail,'transaction'|'related'> | null) {
  const row=detail?.transaction
  return !!row && row.sourceType==='points' && row.status==='completed' && row.type!=='reversal' && !!row.customerId
    && !detail!.related.some(r=>r.type==='reversal'&&r.originalId===row.id)
}
export function rechargeRecordsTarget(store:string,category:'points'|'topup',customer?:Pick<Customer,'phone'>|null) {
  return {path:staffWalletPath(store,'transactions'),query:{category,...(customer?.phone?{customerQuery:customer.phone}:{})}}
}
