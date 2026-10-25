import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {storageApi,StorageApiError} from '../api'
import type {Home,Intent} from '../types'
import {useCustomerStorageText} from './text'
export type CustomerStorageView='deposit'|'withdraw'|'records'
export function useCustomerStorage(){
 const route=useRoute(),router=useRouter(),{t}=useCustomerStorageText(),store=computed(()=>String(route.params.storeId)),api=computed(()=>storageApi(store.value,true))
 const home=ref<Home|null>(null),loading=ref(false),busy=ref(false),login=ref(false),pending=ref(false),error=ref(''),notice=ref(''),activeIntent=ref<Intent|null>(null),scannedCode=ref('')
 const parseView=(v:unknown):CustomerStorageView=>v==='withdraw'||v==='records'?v:'deposit',view=ref<CustomerStorageView>(parseView(route.query.view))
 let generation=0
 watch(()=>route.query.view,v=>{view.value=parseView(v)})
 function open(target:CustomerStorageView,record?:string){if(busy.value)return;view.value=target;error.value='';if(target!=='records')notice.value='';void router.push({query:{...route.query,view:target,record:record??undefined}})}
 function fail(e:unknown){error.value=e instanceof Error?e.message:String(e);if(e instanceof StorageApiError&&e.status===401){generation++;login.value=true;home.value=null;activeIntent.value=null;scannedCode.value='';notice.value='';loading.value=false;busy.value=false;error.value=t('signIn')}pending.value=!!api.value.pending()}
 async function load(){const g=generation;loading.value=true;try{const data=await api.value.get<Home>('/home');if(g!==generation)return;home.value=data;error.value='';login.value=false;pending.value=!!api.value.pending();const hash=new URLSearchParams(route.hash.slice(1)).get('storage');if(hash&&/^WS\.[A-Za-z0-9_-]{43}$/.test(hash)){await router.replace({hash:''});if(g===generation)await scan(hash)}}catch(e){if(g===generation)fail(e)}finally{if(g===generation)loading.value=false}}
 async function run(work:()=>Promise<unknown>){if(busy.value)return;busy.value=true;error.value='';const g=generation;try{await work();if(g===generation)await load()}catch(e){if(g===generation)fail(e)}finally{if(g===generation){busy.value=false;pending.value=!!api.value.pending()}}}
 async function scan(code:string){const g=generation,result=await api.value.scan(code);if(g!==generation)return;activeIntent.value=result;scannedCode.value=code;view.value='records';void router.push({query:{...route.query,view:'records',record:'collections'}})}
 async function recover(){await run(async()=>{const g=generation,result=await api.value.recover();if(g!==generation)return;if(result&&typeof result==='object'&&'selections'in result){activeIntent.value=result as Intent;view.value='records';void router.push({query:{view:'records',record:'collections'}})}else if(result){view.value='records';void router.push({query:{view:'records',record:'requests'}})}})}
 function created(intent:Intent){activeIntent.value=intent;notice.value=t('codeCreated');open('records','collections');void load()}
 function applied(){notice.value=t('applicationSent');open('records','requests');void load()}
 watch(store,()=>{generation++;home.value=null;error.value='';notice.value='';login.value=false;busy.value=false;activeIntent.value=null;scannedCode.value='';view.value=parseView(route.query.view);void load()},{immediate:true})
 return{route,store,api,home,loading,busy,login,pending,error,notice,view,activeIntent,scannedCode,open,load,run,scan,recover,created,applied,fail}
}
