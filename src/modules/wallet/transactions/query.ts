import type { Transaction, Capabilities } from './types'
export const queryKeys=['period','year','month','startDate','endDate','type','status','reference','customerQuery','targetStoreId','limit','cursor'] as const
export function transactionQuery(query:Record<string,unknown>, customer=false):Record<string,string> {
 const result:Record<string,string>={}
 for(const key of queryKeys){const value=query[key];if(typeof value==='string'&&value&&!(customer&&key==='customerQuery'))result[key]=value}
 result.period ||= 'month'; result.type ||= 'all'; result.status ||= 'all'
 return result
}
export function transactionSearch(query:Record<string,unknown>,customer=false){return new URLSearchParams(transactionQuery(query,customer)).toString()}
export function transactionListPath(store:string,customer=false){return customer?`/wallet/${encodeURIComponent(store)}/transactions`:`/stores/${encodeURIComponent(store)}/wallet/transactions`}
/** Only same-role internal transaction lists, including another authorized store. Server checks store authorization. */
export function safeTransactionReturn(value:unknown,store:string,customer=false):string {
 const fallback=transactionListPath(store,customer)
 if(typeof value!=='string'||/[\\\r\n#]/.test(value))return fallback
 const path=value.split('?')[0]!
 const pattern=customer?/^\/wallet\/[A-Za-z0-9_-]+\/transactions$/:/^\/stores\/[A-Za-z0-9_-]+\/wallet\/transactions$/
 return pattern.test(path)?value:fallback
}
export function transactionDetailTarget(row:Transaction,customer:boolean,returnTo:string){
 const base=customer?`/wallet/${encodeURIComponent(row.storeId)}`:`/stores/${encodeURIComponent(row.storeId)}/wallet`
 const page=row.sourceType==='bill'?`bills/${row.id}`:!customer&&row.sourceType==='topup'?`topups/${row.id}`:!customer&&row.sourceType==='refund'?`refunds/${row.id}`:`transactions/${row.sourceType}/${row.id}`
 return {path:`${base}/${page}`,query:{returnTo}}
}
export function availableTypes(c:Capabilities){return ['all',...(c.money?['payment','topup']:[]),...(c.points?['points_grant','points_deduct','points_redemption']:[]),...(c.refunds?['refund']:[]),...(c.points||c.reversals?['reversal']:[])]}
export function summaryKeys(c:Capabilities){return [...(c.money?['consumptionCents','walletPaymentCents','externalPaymentCents','topupPrincipalCents','topupBonusCents','topupCreditCents']:[]),...(c.refunds?['refundCents']:[]),...(c.reversals?['moneyReversalCents']:[]),...(c.points?['pointsGranted','pointsDeducted','pointsRedeemed','pointsReversed','pointsReversalIn','pointsReversalOut']:[])]}
