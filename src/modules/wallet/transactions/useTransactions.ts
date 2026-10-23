import {ref,watch,onUnmounted,type Ref} from 'vue'
import {walletApi} from '../api'
import {transactionSearch} from './query'
import type {TransactionPage} from './types'
export function useTransactions(store:Ref<string>,query:Ref<Record<string,unknown>>,customer=false){
 const data=ref<TransactionPage|null>(null),busy=ref(false),error=ref('');let generation=0
 async function refresh(){const version=++generation;busy.value=true;error.value='';data.value=null
 try{const result=await walletApi(store.value,customer).get<TransactionPage>('/transactions?'+transactionSearch(query.value,customer));if(version===generation)data.value=result}
 catch(e){if(version===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(version===generation)busy.value=false}}
 watch([store,query],()=>void refresh(),{immediate:true});onUnmounted(()=>generation++)
 return {data,busy,error,refresh}
}
