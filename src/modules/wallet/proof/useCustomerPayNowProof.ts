import {ref,watch,onUnmounted,type Ref} from 'vue'
import {walletApi} from '../api'
import type {ProofContext} from './types'
export interface CustomerProof {id:string;context:ProofContext;bankReference:string|null;checks:Record<string,string>;status:'waiting_confirmation'|'confirmed'|'closed';imageUrl:string}
export function useCustomerPayNowProof(input:Ref<{store:string;sourceType:string;sourceId:string;channel:string;amount:string}>,submitted:()=>void){
 const context=ref<ProofContext|null>(null),file=ref<File|null>(null),records=ref<CustomerProof[]>([]),busy=ref(false),loading=ref(false),error=ref(''),qr=ref('')
 let generation=0,key=crypto.randomUUID()
 const path=()=>`/collection-proofs/${input.value.sourceType}/${encodeURIComponent(input.value.sourceId)}`
 const message=(e:unknown)=>e instanceof Error?e.message:String(e)
 function select(value:File|null){file.value=value;key=crypto.randomUUID()}
 async function refresh(){
  const version=++generation;context.value=null;records.value=[];select(null);qr.value='';error.value='';busy.value=false;loading.value=true
  const api=walletApi(input.value.store,true),base=path(),channel=input.value.channel
  const results=await Promise.allSettled([api.get<ProofContext>(base+'/context?channel='+encodeURIComponent(channel)),api.get<CustomerProof[]>(base)])
  if(version!==generation)return
  if(results[0].status==='fulfilled')context.value=results[0].value;else error.value=message(results[0].reason)
  if(results[1].status==='fulfilled')records.value=results[1].value;else error.value=message(results[1].reason)
  loading.value=false
 }
 async function submit(){
  if(!file.value||!context.value||busy.value||loading.value)return
  const version=generation,data=new FormData();data.set('image',file.value);data.set('channel',input.value.channel);data.set('amount',input.value.amount);data.set('idempotencyKey',key)
  busy.value=true;error.value=''
  try{const result=await walletApi(input.value.store,true).upload<CustomerProof>(path()+'/submit',data);if(version!==generation)return;records.value=[result,...records.value.filter(p=>p.id!==result.id)];select(null);submitted()}
  catch(e){if(version===generation)error.value=message(e)}finally{if(version===generation)busy.value=false}
 }
 async function showQr(){
  if(!context.value||busy.value)return;const version=generation;busy.value=true;error.value=''
  try{const result=await walletApi(input.value.store,true).get<{payload:string}>(path()+'/paynow?channel='+encodeURIComponent(input.value.channel));if(version===generation)qr.value=result.payload}
  catch(e){if(version===generation)error.value=message(e)}finally{if(version===generation)busy.value=false}
 }
 watch(input,refresh,{immediate:true});onUnmounted(()=>{generation++})
 return{context,file,records,busy,loading,error,qr,select,refresh,submit,showQr}
}
