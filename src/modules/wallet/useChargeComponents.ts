import { onUnmounted, ref, watch, type Ref } from 'vue'
import { walletApi } from './api'
import { chargePreview } from './scanCharge'
import type { Account, Bill } from './types'
export interface ChargeComponents { accountId:string;accountVersion:number;principalCents:number;bonusCents:number;allocations:Array<{lotId:string;principal:number;bonus:number;original?:boolean}> }
export function useChargeComponents(store:Ref<string>,account:Ref<Account|null>,amount:Ref<string|number>,code:Ref<string>,enabled:Ref<boolean>,bill?:Ref<Bill>){
 const components=ref<ChargeComponents|null>(null),loading=ref(false),error=ref('');let generation=0
 function clear(){generation++;components.value=null;loading.value=false;error.value=''}
 async function refresh(){clear();if(!enabled.value||!account.value||!chargePreview(amount.value,0))return;const current=generation,id=store.value,accountId=account.value.id;loading.value=true
  try{const result=await walletApi(id).post<ChargeComponents>('/charge-previews',{accountId,amount:String(amount.value),...(bill?{billId:bill.value.id}:{})});if(current!==generation)return;if(result.accountId!==accountId||!Number.isSafeInteger(result.accountVersion))throw new Error('Invalid wallet preview');components.value=result}
  catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(current===generation)loading.value=false}
 }
 watch([store,()=>account.value?.id,amount,code,enabled,()=>bill?.value.id,()=>bill?.value.version],()=>{void refresh()},{immediate:true,flush:'sync'})
 onUnmounted(clear)
 return {components,loading,error,refresh,clear}
}
