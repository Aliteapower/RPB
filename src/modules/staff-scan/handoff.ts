type Target='recharge'|'cards'|'storage'
interface Handoff {store:string;actor:string;target:Target;code:string;task?:string;customerId?:string}
let current:(Handoff&{expires:number})|null=null
/** One-use, short-lived memory only; credentials never go in URLs or browser storage. */
export function setScanHandoff(value:Handoff){current={...value,expires:Date.now()+30000}}
export function takeScanHandoff(store:string,actor:string,target:Target):Handoff|null{
 const value=current;current=null
 return value&&value.store===store&&value.actor===actor&&value.target===target&&value.expires>Date.now()?value:null
}
