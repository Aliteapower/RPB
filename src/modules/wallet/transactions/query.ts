import type { Transaction, Capabilities } from './types'
export const queryKeys=['period','year','month','startDate','endDate','type','status','reference','customerQuery','targetStoreId','category','direction','limit','cursor'] as const
export function transactionQuery(query:Record<string,unknown>, customer=false):Record<string,string> {
 const result:Record<string,string>={}
 for(const key of queryKeys){const value=query[key];if(typeof value==='string'&&value&&!(customer&&key==='customerQuery'))result[key]=value}
 result.period ||= 'month'; result.type ||= 'all'; result.status ||= 'all'
 result.category ||= 'auto';result.direction ||= 'all'
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
export function availableCategories(c:Capabilities,customer=false){
 if(customer)return ['all',...(c.money||c.refunds||c.reversals?['money']:[]),...(c.points?['points']:[])]
 return [...(c.money||c.refunds?['consumption']:[]),...(c.money||c.refunds||c.reversals?['topup']:[]),...(c.points?['points']:[])]
}
export function effectiveCategory(category:string|undefined,type='all'){return category&&category!=='auto'?category:type==='topup'?'topup':type.startsWith('points_')||type==='reversal'?'points':'consumption'}
export function availableTypes(c:Capabilities,category='all'){
 const all=['all',...(c.money?['payment','topup']:[]),...(c.points?['points_grant','points_deduct','points_redemption']:[]),...(c.refunds?['refund']:[]),...(c.points||c.reversals?['reversal']:[])]
 const types=category==='money'?['all','payment','topup','refund','reversal']:category==='consumption'?['all','payment','refund']:category==='topup'?['all','topup','refund','reversal']:category==='points'?['all','points_grant','points_deduct','points_redemption','reversal']:all
 return types.filter(type=>all.includes(type))
}
export function summaryKeys(c:Capabilities,category='all'){
 if(category==='money')return summaryKeys({...c,points:false},'all')
 return [...(c.money&&(category==='all'||category==='consumption')?['consumptionCents','walletPaymentCents','externalPaymentCents']:[]),...(c.money&&(category==='all'||category==='topup')?['topupPrincipalCents','topupBonusCents','topupCreditCents']:[]),...(c.refunds&&category!=='points'?['refundCents']:[]),...(c.reversals&&(category==='all'||category==='topup')?['moneyReversalCents']:[]),...(c.points&&(category==='all'||category==='points')?['pointsGranted','pointsDeducted','pointsRedeemed','pointsReversed','pointsReversalIn','pointsReversalOut']:[])]
}
export function switchTransactionFilter(query:Record<string,unknown>,value:string,folder=false,customer=false){const next=transactionQuery(query,customer);delete next.cursor;if(folder){next.category=value;next.type='all'}else next.direction=value;return next}
