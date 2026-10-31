import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {storageApi,StorageApiError} from '../api'
import type {Home,Intent} from '../types'
import {customerStorage,type StorageCustomer} from './scope'
import {useMerchantStorageText} from './text'
import {useAuthSessionStore} from '../../../stores/authSession'
import {takeScanHandoff} from '../../staff-scan/handoff'
export function useMerchantStorage(){
 const route=useRoute(),router=useRouter(),{t}=useMerchantStorageText(),store=computed(()=>String(route.params.storeId)),api=computed(()=>storageApi(store.value,false))
 const home=ref<Home|null>(null),customer=ref<StorageCustomer|null>(null),results=ref<StorageCustomer[]>([]),query=ref(''),task=ref('deposit'),busy=ref(false),loading=ref(false),searching=ref(false),searched=ref(false),pending=ref(false),error=ref(''),notice=ref(''),activeIntent=ref<Intent|null>(null),scanResult=ref<Intent|null>(null)
 const scoped=computed(()=>home.value&&customer.value?customerStorage(home.value,customer.value.id):null)
 const can=(permission:string)=>home.value?.permissions.includes('wine_storage.'+permission)??false
 let generation=0,searchGeneration=0
 function fail(e:unknown){error.value=e instanceof Error?e.message:String(e);pending.value=!!api.value.pending();if(e instanceof StorageApiError&&[401,403].includes(e.status)){customer.value=null;activeIntent.value=null;scanResult.value=null;results.value=[]}}
 function choose(value:StorageCustomer|null){customer.value=value;activeIntent.value=null;scanResult.value=null;results.value=[];searched.value=false;error.value='';notice.value=''}
 async function search(){const q=query.value.trim(),g=generation,request=++searchGeneration;results.value=[];if(!q)return;searched.value=false;searching.value=true;error.value='';try{const values=await api.value.get<StorageCustomer[]>('/customers?query='+encodeURIComponent(q));if(g===generation&&request===searchGeneration){results.value=values;searched.value=true}}catch(e){if(g===generation&&request===searchGeneration)fail(e)}finally{if(g===generation&&request===searchGeneration)searching.value=false}}
 function open(value:string){if(busy.value||((value==='deposit'||value==='inbox')&&!home.value?.newBusinessEnabled)||(value==='inbox'&&!can('receive')))return;scanResult.value=null;task.value=value;error.value='';notice.value='';void router.push({query:{...route.query,task:value}})}
 async function load(){const g=generation;loading.value=true;try{const value=await api.value.get<Home>('/home');if(g!==generation)return;home.value=value;if(!value.newBusinessEnabled&&(task.value==='deposit'||task.value==='inbox'))task.value='records';else if(task.value==='inbox'&&!value.permissions.includes('wine_storage.receive'))task.value='deposit';pending.value=!!api.value.pending()}catch(e){if(g===generation)fail(e)}finally{if(g===generation)loading.value=false}}
 async function run(work:()=>Promise<unknown>,message?:string){if(busy.value)return;busy.value=true;error.value='';const g=generation;try{await work();if(g===generation){if(message)notice.value=message;await load()}}catch(e){if(g===generation)fail(e)}finally{if(g===generation){busy.value=false;pending.value=!!api.value.pending()}}}
 function scanned(result:Intent){choose(null);scanResult.value={...result,code:null};task.value='withdraw'}
 async function scan(code:string){const g=generation,client=api.value;choose(null);const result=await client.command<Intent>('/scan-deliver',{code});if(g===generation)scanned(result)}
 async function recover(){await run(async()=>{const g=generation,client=api.value,result=await client.recover();if(g!==generation)return;if(result&&typeof result==='object'&&'selections'in result&&'status'in result&&result.status==='completed'){scanned(result as Intent);return}if(result&&typeof result==='object'&&'customerId'in result){const identified=await client.get<StorageCustomer>(`/customers/${encodeURIComponent(String(result.customerId))}`);if(g!==generation)return;choose(identified);if('selections'in result){activeIntent.value=result as Intent;task.value='withdraw'}else task.value='records'}})}
 function routeTask(value:unknown){if(value==='inbox'){if(home.value&&!home.value.newBusinessEnabled)return 'records';if(home.value&&!can('receive'))return 'deposit';return 'inbox'}return value==='withdraw'||value==='records'||value==='settings'||value==='inventory'?value:home.value&&!home.value.newBusinessEnabled?'records':'deposit'}
 watch(()=>route.query.task,value=>{task.value=routeTask(value)},{immediate:true})
 const auth=useAuthSessionStore()
 async function loadScanCustomer(handoff:ReturnType<typeof takeScanHandoff>,g:number){
  const client=api.value
  await load()
  if(!handoff?.customerId||g!==generation||!home.value)return
  searching.value=true
  try{
   const identified=await client.get<StorageCustomer>(`/customers/${encodeURIComponent(handoff.customerId)}`)
   if(g!==generation||handoff.actor!==(auth.user?.accountId??''))return
   choose(identified);task.value=handoff.task==='withdraw'?'withdraw':home.value.newBusinessEnabled?'deposit':'records'
  }catch(e){if(g===generation)fail(e)}finally{if(g===generation)searching.value=false}
 }
 watch(store,()=>{generation++;searchGeneration++;home.value=null;choose(null);query.value='';results.value=[];busy.value=false;searching.value=false;pending.value=false;task.value=routeTask(route.query.task);void loadScanCustomer(takeScanHandoff(store.value,auth.user?.accountId??'','storage'),generation)},{immediate:true})
 return{store,api,home,customer,results,query,task,busy,loading,searching,searched,pending,error,notice,activeIntent,scanResult,scoped,can,choose,search,open,load,run,scan,recover,fail,t}
}
