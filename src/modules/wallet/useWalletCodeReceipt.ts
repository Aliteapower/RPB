import { onUnmounted, ref, watch, type Ref } from 'vue'
import { walletApi } from './api'
import type { Bill, PaymentCode } from './types'

/** Poll the exact issued code, including already-paid bills absent from pending-bills. No token in URLs/storage. */
export function useWalletCodeReceipt(store: Ref<string>, code: Ref<PaymentCode|null>, received: (bill: Bill) => Promise<unknown>) {
  const error=ref('');let generation=0,running=false,done=false
  watch([store,code],()=>{generation++;done=false;error.value=''})
  async function check(){
    const issued=code.value,current=generation,id=store.value
    if(!issued || running || done || document.visibilityState!=='visible')return
    running=true
    try {
      const result=await walletApi(id,true).post<{bill:Bill|null}>('/payment-codes/status',{code:issued.code})
      if(current!==generation)return
      error.value=''
      if(result.bill){done=true;await received(result.bill)}
    }catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}
    finally{running=false}
  }
  const timer=window.setInterval(()=>void check(),2000)
  onUnmounted(()=>{generation++;window.clearInterval(timer)})
  return {error}
}
