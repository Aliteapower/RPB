import {cardApi} from '../cards/api'
import {storageApi} from '../wine-storage/api'
import {walletApi} from '../wallet/api'
import type {Customer,Account} from '../wallet/types'
import type {StaffScanCode} from '../shared-commerce/scanning/staffCode.mjs'
type TransactionKind='card'|'storage'
interface Owner {actor:string;kind:TransactionKind}
export function createStaffScanSession(store:string,actor:string,currentActor:()=>string,can:(permission:string)=>boolean){
 const cards=cardApi(store),storage=storageApi(store,false),wallet=walletApi(store),slot=`rpb.staff-scan.owner:${store}`
 let running=false
 const assertOwner=()=>{if(!actor||actor!==currentActor())throw Error('请使用原操作账号 / Use the original staff account')}
 function owner():Owner|null{try{return JSON.parse(sessionStorage.getItem(slot)??'null')}catch{return null}}
 function pending(){return{card:!!cards.pending(),storage:!!storage.pending(),owner:owner()}}
 function remember(kind:TransactionKind){sessionStorage.setItem(slot,JSON.stringify({actor,kind}))}
 function settle(){const p=pending();if(!p.card&&!p.storage)sessionStorage.removeItem(slot)}
 async function scan(value:StaffScanCode){
  assertOwner();if(running)throw Error('操作处理中 / Processing');if(pending().card||pending().storage)throw Error('请先恢复上次操作 / Recover pending operation')
  running=true
  try{
   if(value.kind==='card'){
    if(!can('wallet.cards.view')||!(can('wallet.cards.redeem')||can('wallet.cards.stamp')))throw Error('无卡核销权限 / Card redemption permission required')
    remember('card');const result=await cards.command('/authorized-redeem',{authorization:value.code},'request.authorized');assertOwner();return{kind:'card' as const,result}
   }
   if(value.kind==='storage'){
    if(!can('wine_storage.view')||!can('wine_storage.deliver'))throw Error('无取酒核销权限 / Bottle collection permission required')
    remember('storage');const result=await storage.command('/scan-deliver',{code:value.code});assertOwner();return{kind:'storage' as const,result}
   }
   if(value.kind==='payment'){
    if(!can('wallet.view'))throw Error('无钱包权限 / Wallet permission required')
    const result=await wallet.post<{customer:Customer;account:Account}>('/payment-codes/resolve',{code:value.code});assertOwner();return{kind:'customer' as const,customer:result.customer,account:result.account}
   }
   let customer:Customer|null=null
   if(can('wallet.topup.create')||can('wallet.points.view'))customer=await wallet.post<Customer>('/customer-codes/resolve',{code:value.code})
   else if(can('wallet.cards.view'))customer=await cards.post<Customer>('/identity',{code:value.code})
   else if(!(can('wallet.charge.create')&&can('wallet.collection.confirm')&&value.code.startsWith('WI.')))throw Error('无顾客识别权限 / Customer identification permission required')
   assertOwner();return{kind:'customer' as const,customer,account:null}
  }finally{running=false;settle()}
 }
 async function recover(){
  assertOwner();if(running)throw Error('操作处理中 / Processing')
  const p=pending(),o=p.owner
  if(!o||o.actor!==actor||(o.kind==='card'?!p.card:!p.storage))throw Error('请到原业务入口查询 / Recover in the original business screen')
  if(o.kind==='card'&&(!can('wallet.cards.view')||!(can('wallet.cards.redeem')||can('wallet.cards.stamp')))||o.kind==='storage'&&(!can('wine_storage.view')||!can('wine_storage.deliver')))throw Error('无核销权限 / Permission required')
  running=true
  try{const result=await(o.kind==='card'?cards.recover():storage.recover());assertOwner();return{kind:o.kind,result}}finally{running=false;settle()}
 }
 return{scan,recover,pending}
}
