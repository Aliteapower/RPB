import type { ComposerTranslation } from 'vue-i18n'
export type CollectionType='cash'|'paynow'|'credit_card'|'other'
export interface CollectionMethod {code?:string;name:string;type:CollectionType}
export interface CollectionMethodSettings {version:number;methods:CollectionMethod[];affectedStoreIds:string[]}
export const channelType=(code:string)=>code.split(':')[0] as CollectionType
export function channelLabel(t:ComposerTranslation,code:string,labels?:Record<string,string>,snapshot?:string){return snapshot || labels?.[code] || t(channelType(code)==='other'?'wallet.operation.otherCollection':`wallet.channels.${channelType(code)}`)}
export function topupChannelName(topup:{offerSnapshot?:unknown}){return (topup.offerSnapshot as {channelName?:string}|undefined)?.channelName}
