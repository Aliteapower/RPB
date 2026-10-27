import {computed,onUnmounted,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthSessionStore} from '../../stores/authSession'
import {cardApi} from './api'
import {businessDay} from './format'
import type {Plan,Holding,CardEntry,Purchase} from './types'
export function useCardsWorkspace(customer=false){const route=useRoute(),auth=useAuthSessionStore(),store=computed(()=>String(route.params.storeId)),busy=ref(false),error=ref(''),notice=ref(''),plans=ref<Plan[]>([]),holdings=ref<Holding[]>([]),entries=ref<CardEntry[]>([]),purchases=ref<Purchase[]>([]),enabled=ref(false),pending=ref(false),moreHoldings=ref(false),moreEntries=ref(false),morePurchases=ref(false);let generation=0,observedAt=Date.now()
 const api=()=>cardApi(store.value,customer),can=(p:string)=>auth.user?.permissions.includes(p)===true
 const dayChanged=()=>holdings.value.some(h=>businessDay(h.timezone,new Date(observedAt))!==businessDay(h.timezone))
 async function load(){const revision=generation,queriedAt=Date.now();const values=await Promise.all([api().get<Plan[]>('/plans'),api().get<Holding[]>('/holdings'),api().get<CardEntry[]>('/entries'),api().get<Purchase[]>('/purchases'),customer?Promise.resolve({enabled:true}):api().get<{enabled:boolean}>('/settings')]);if(revision!==generation)return;[plans.value,holdings.value,entries.value,purchases.value]=values;enabled.value=values[4].enabled;moreHoldings.value=holdings.value.length===500;moreEntries.value=entries.value.length===500;morePurchases.value=purchases.value.length===500;observedAt=queriedAt}
 async function run(work:()=>Promise<void>){if(busy.value)return;const revision=generation;busy.value=true;error.value='';notice.value='';try{await work()}catch(e){if(revision===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(revision===generation){busy.value=false;pending.value=!!api().pending()}}}
 async function loadMore(kind:'holdings'|'entries'|'purchases'){const g=generation;if(kind==='holdings'){const rows=await api().get<Holding[]>(`/holdings?before=${holdings.value.at(-1)?.id}`);if(g===generation){holdings.value.push(...rows);moreHoldings.value=rows.length===500}}else if(kind==='entries'){const rows=await api().get<CardEntry[]>(`/entries?before=${entries.value.at(-1)?.id}`);if(g===generation){entries.value.push(...rows);moreEntries.value=rows.length===500}}else{const rows=await api().get<Purchase[]>(`/purchases?before=${purchases.value.at(-1)?.id}`);if(g===generation){purchases.value.push(...rows);morePurchases.value=rows.length===500}}}
 async function recover(){await api().recover();await load();notice.value='✓'}
 watch(store,()=>{generation++;busy.value=false;plans.value=[];holdings.value=[];entries.value=[];purchases.value=[];pending.value=!!api().pending();void run(load)},{immediate:true});const dayTimer=window.setInterval(()=>{if(!busy.value&&!pending.value&&dayChanged())void run(load)},30000);onUnmounted(()=>{generation++;window.clearInterval(dayTimer)})
 return {store,busy,error,notice,plans,holdings,entries,purchases,enabled,pending,can,api,run,load,recover,loadMore,moreHoldings,moreEntries,morePurchases}
}
