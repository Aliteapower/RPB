<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useWorkforceText} from '../text'
import {usePhasePage} from '../phasePage'
import {emptyPoint,useWorkModeText} from '../workMode'
import type {ClockSiteSettings,ClockSiteWrite} from '../clockSite'
import {useClockSiteText} from '../clockSiteText'
import ClockLocationCheck from '../components/ClockLocationCheck.vue'
import WorkPointFields from '../components/WorkPointFields.vue'
import PhaseStatus from '../components/PhaseStatus.vue'

const {s}=useClockSiteText(),{w}=useWorkModeText(),{error,t}=useWorkforceText()
const {api,home,store,base,current,loading,busy,failure,notice,load}=usePhasePage()
const address=ref(''),pointBusy=ref(false),data=ref<ClockSiteSettings|null>(null)
const point=ref(emptyPoint()),pending=ref<ClockSiteWrite|null>(null)
function refresh(){
 void load(()=>api().get<ClockSiteSettings>('/clock-site/settings'),value=>{
  data.value=value
  address.value=value.address||''
  point.value=value.point?{...value.point}:emptyPoint()
 })
}
async function save(){
 if(busy.value||pointBusy.value||!home.value?.employeeManage||!data.value)return
 const originalStore=store.value
 if(!pending.value)pending.value={expectedVersion:data.value.version,expectedStoreVersion:data.value.storeVersion,address:address.value,point:{...point.value}}
 busy.value=true;failure.value='';notice.value=''
 try{
  await api().put('/clock-site/settings',pending.value)
  if(!current()||store.value!==originalStore)return
  pending.value=null;notice.value=w('saved');refresh()
 }catch(e){
  if(!current()||store.value!==originalStore)return
  if(e&&typeof e==='object'&&'status'in e&&Number(e.status)>=400&&Number(e.status)<500)pending.value=null
  failure.value=error(e)
 }finally{if(current()&&store.value===originalStore)busy.value=false}
}
watch(store,()=>{data.value=null;pending.value=null;busy.value=false;pointBusy.value=false;if(home.value?.employeeManage)refresh()})
onMounted(()=>{if(home.value?.employeeManage)refresh()})
</script>
<template>
 <section v-if="home?.employeeManage" class="wf-panel wf-form-panel">
  <header class="wf-toolbar"><h2>{{w('site')}}</h2><button :disabled="busy||loading||pointBusy||!!pending" @click="refresh">{{w('refresh')}}</button></header>
  <RouterLink :to="base+'/management'">{{w('back')}}</RouterLink>
  <p class="wf-hint">{{w('siteHint')}}</p>
  <PhaseStatus :failure="failure" :notice="notice" :loading="loading"/>
  <div v-if="pending" role="status"><p>{{w('unknown')}}</p><button :disabled="busy" @click="save">{{w('retry')}}</button></div>
  <form v-if="data&&!loading" @submit.prevent="save">
   <fieldset :disabled="busy||!!pending">
    <h3>{{s('identity')}}</h3>
    <dl class="wf-details"><dt>{{s('store')}}</dt><dd>{{data.storeName}} · {{data.storeCode}} · {{data.storeId}}</dd><dt>{{s('entity')}}</dt><dd>{{data.operatingEntityName||'—'}} · {{data.operatingEntityId||'—'}}</dd></dl>
    <p>{{s('scope')}}</p>
    <label>{{s('address')}}<input v-model="address" maxlength="500" :readonly="!data.canEditAddress"></label>
    <p v-if="!data.canEditAddress">{{s('readonly')}}</p>
    <p>{{s('legacy')}}</p>
    <WorkPointFields :point="point" @locating="pointBusy=$event"/>
    <button class="primary" :disabled="pointBusy">{{w('save')}}</button>
   </fieldset>
   <ClockLocationCheck :store-id="store" :point="point" :version="data.version" :api="api()" :disabled="busy||pointBusy||!!pending"/>
  </form>
 </section>
 <p v-else role="alert">{{t('noAccess')}}</p>
</template>
