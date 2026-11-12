import {computed,onUnmounted,reactive,ref,watch,type Ref} from 'vue'
import {cardApi} from '../api'
import {businessDay} from '../format'
import type {ReportFilters,ReportPage} from './types'
export function useCardBusinessReport(store:Ref<string>,timezone:()=>string){
 const mode=ref<'details'|'summary'|'expiry'>('details'),page=ref(0),size=ref(25),data=ref<ReportPage|null>(null),loading=ref(false),error=ref(''),resetKey=ref(0),fromValid=ref(true),toValid=ref(true)
 const today=()=>businessDay(timezone()),defaults=():ReportFilters=>({from:today().slice(0,8)+'01',to:today(),query:'',planId:'',kind:'all',section:'business',groupBy:'plan'})
 const draft=reactive<ReportFilters>(defaults()),applied=ref<ReportFilters>({...draft}),dirty=computed(()=>JSON.stringify(draft)!==JSON.stringify(applied.value))
 let generation=0,request=0
 const epoch=()=>generation
 async function load(){const g=generation,r=++request,id=store.value;loading.value=true;error.value='';data.value=null
  const params=new URLSearchParams({...applied.value,page:String(page.value),size:String(size.value)});if(!applied.value.planId)params.delete('planId')
  try{const result=await cardApi(id).get<ReportPage>('/reports?'+params);if(g===generation&&r===request)data.value=result}
  catch(e){if(g===generation&&r===request)error.value=e instanceof Error?e.message:String(e)}finally{if(g===generation&&r===request)loading.value=false}
 }
 function search(){if(!fromValid.value||!toValid.value||!draft.from||!draft.to||draft.from>draft.to){error.value='日期范围无效 / Invalid date range';return}if((Date.parse(draft.to)-Date.parse(draft.from))/86400000>365){error.value='日期范围最多366天 / Maximum 366 days';return}page.value=0;applied.value={...draft,query:draft.query.trim()};draft.query=draft.query.trim();void load()}
 function reset(){resetKey.value++;fromValid.value=true;toValid.value=true;Object.assign(draft,defaults(),{section:mode.value==='expiry'?'expiry':'business'});search()}
 function move(delta:number){if(loading.value||dirty.value||!data.value)return;const next=page.value+delta;if(next<0||next*size.value>=data.value.total)return;page.value=next;void load()}
 watch(mode,(value,old)=>{if((value==='expiry')!==(old==='expiry')){const section=value==='expiry'?'expiry':'business';if(section===draft.section)return;request++;loading.value=false;data.value=null;draft.section=section;draft.kind='all';search()}})
 watch(store,()=>{generation++;request++;mode.value='details';Object.assign(draft,defaults());fromValid.value=true;toValid.value=true;resetKey.value++;search()},{immediate:true,flush:'sync'})
 onUnmounted(()=>{generation++;request++})
 return{mode,page,size,data,loading,error,draft,applied,dirty,resetKey,fromValid,toValid,load,search,reset,move,epoch}
}
