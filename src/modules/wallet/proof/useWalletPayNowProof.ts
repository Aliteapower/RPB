import {ref,watch,onUnmounted,type Ref} from 'vue'
import {walletApi} from '../api'
import type {ProofContext,WalletProof} from './types'
export function useWalletPayNowProof(input:Ref<{store:string;sourceType:string;sourceId:string;channel:string;amount:string}>){
 const context=ref<ProofContext|null>(null),proof=ref<WalletProof|null>(null),file=ref<File|null>(null),busy=ref(false),loading=ref(false),error=ref('')
 let generation=0,contextGeneration=0,key=crypto.randomUUID()
 const path=()=>`/collection-proofs/${input.value.sourceType}/${encodeURIComponent(input.value.sourceId)}`
 function clear(){generation++;proof.value=null;file.value=null;busy.value=false;error.value='';key=crypto.randomUUID()}
 async function refresh(){clear();const version=++contextGeneration;context.value=null;loading.value=true;try{const result=await walletApi(input.value.store).get<ProofContext>(path()+'/context?channel='+encodeURIComponent(input.value.channel));if(version===contextGeneration)context.value=result}catch(e){if(version===contextGeneration)error.value=e instanceof Error?e.message:String(e)}finally{if(version===contextGeneration)loading.value=false}}
 watch(input,refresh,{immediate:true})
 function select(value:File|null){clear();file.value=value}
 function adopt(value:WalletProof){const c=context.value;if(!c||value.context.sourceType!==input.value.sourceType||value.context.sourceId!==input.value.sourceId||value.context.channel!==input.value.channel||value.context.amountCents!==Math.round(Number(input.value.amount)*100)||value.context.expectedAccount!==c.expectedAccount)return;clear();proof.value=value}
 async function scan(){if(!file.value||busy.value||!context.value)return;const version=generation,data=new FormData();data.set('image',file.value);data.set('channel',input.value.channel);data.set('amount',input.value.amount);data.set('idempotencyKey',key);busy.value=true;error.value='';try{const result=await walletApi(input.value.store).upload<WalletProof>(path()+'/scan',data);if(version===generation)proof.value=result}catch(e){if(version===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(version===generation)busy.value=false}}
 onUnmounted(()=>{generation++;contextGeneration++})
 return{context,proof,file,busy,loading,error,select,scan,clear,refresh,adopt}
}
