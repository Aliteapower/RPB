import { onUnmounted, ref, watch, type Ref } from 'vue'
import { walletApi, WalletApiError } from './api'
import { useWalletTask } from './useWalletTask'
import { rows, type PointsAccount, type PointsEntry, type PointsResult } from './marketingTypes'
export function usePoints(store:Ref<string>,customer:boolean,customerId:Ref<string|undefined>,enabled:()=>boolean=()=>true){
 const task=useWalletTask(),account=ref<PointsAccount|null>(null),entries=ref<PointsEntry[]>([]),ready=ref(false),more=ref(false),stale=ref(false)
 let generation=0
 const base=()=>customer?'/points':`/customers/${encodeURIComponent(customerId.value??'')}/points`
 async function refresh(append=false){if(!enabled()||(!customer&&!customerId.value))return;const current=generation;task.busy.value=true;task.error.value='';try{const api=walletApi(store.value,customer);const [a,e]=await Promise.all([api.get<PointsAccount>(base()),Promise.resolve([])]);if(current!==generation)return;account.value=a;stale.value=false;const result=rows(e);entries.value=append?[...entries.value,...result]:result;more.value=result.length===50;ready.value=true}catch(e){if(current===generation)task.error.value=e instanceof Error?e.message:String(e)}finally{if(current===generation)task.busy.value=false}}
 async function command(kind:'grants'|'deductions'|'redemptions'|'reverse',body:Record<string,unknown>,entryId?:string){if(!account.value||!customerId.value||stale.value)return;const current=generation;await task.run(async()=>{try{const result=await walletApi(store.value).command<PointsResult>(kind==='reverse'?`/points/entries/${encodeURIComponent(entryId!)}/reverse`:`${base()}/${kind}`,{...body,...(kind==='grants'?{}:{expectedVersion:account.value!.version})});if(current!==generation)return;account.value=result.account;entries.value=[result.entry,...entries.value.filter(e=>e.id!==result.entry.id)];if(entryId)entries.value=entries.value.map(e=>e.id===entryId?{...e,reversed:true}:e)}catch(e){if(current===generation&&e instanceof WalletApiError&&['POINTS_VERSION_STALE','POINTS_ENTRY_ALREADY_REVERSED'].includes(e.code))stale.value=true;throw e}})}
 watch([store,customerId],()=>{generation++;account.value=null;entries.value=[];ready.value=false;void refresh()},{immediate:true})
 onUnmounted(()=>{generation++})
 return {...task,account,entries,ready,more,stale,refresh,command}
}
