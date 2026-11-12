import {staffWalletPath} from './navigation'
export type CashierKind='collect'|'money'|'points'
export interface CashierAction {id:CashierKind;label:string;target:{path:string;query?:Record<string,string>}}
export function cashierActions(store:string,can:(permission:string)=>boolean):CashierAction[]{
 return [
  ...(can('wallet.charge.create')&&can('wallet.collection.confirm')?[{id:'collect' as const,label:'wallet.personal.collectEntry',target:{path:staffWalletPath(store,'collect')}}]:[]),
  ...(can('wallet.view')&&can('wallet.topup.create')?[{id:'money' as const,label:'wallet.transactions.folder_topup',target:{path:staffWalletPath(store,'topup'),query:{kind:'money',scan:'1'}}}]:[]),
  ...(can('wallet.points.view')?[{id:'points' as const,label:'wallet.transactions.folder_points',target:{path:staffWalletPath(store,'topup'),query:{kind:'points',scan:'1'}}}]:[])
 ]
}
