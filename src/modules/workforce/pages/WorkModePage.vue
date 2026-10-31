<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {useWorkforceText} from '../text'
import {usePhasePage} from '../phasePage'
import {WorkforceApiError} from '../api'
import {emptyPoint,tomorrow,permittedModes,useWorkModeText,workModeError,type WorkAssignment,type WorkModeData,type WorkMode,type HomeAddress} from '../workMode'
import WorkModeFields from '../components/WorkModeFields.vue'
import WorkPointFields from '../components/WorkPointFields.vue'
import PhaseStatus from '../components/PhaseStatus.vue'
import WorkModePolicyList from '../components/WorkModePolicyList.vue'
import {groupAssignments,workModeDisplayText} from '../workModeDisplay'
const {error}=useWorkforceText()
const {api,home,base,current,loading,busy,failure,notice,load,english}=usePhasePage(),route=useRoute(),{w}=useWorkModeText()
const pointBusy=ref(false)
const data=ref<WorkModeData|null>(null),mode=ref<WorkMode>('legacy'),allowedModes=ref<WorkMode[]|undefined>(undefined),inherit=ref(true),effectiveFrom=ref(''),addressDate=ref(''),point=ref(emptyPoint()),notes=ref<Record<string,string>>({})
type Pending={path:string;body:unknown;assignmentVersion?:number};const pending=ref<Pending|null>(null)
const employeeId=String(route.params.employeeId||''),self=computed(()=>!!data.value&&home.value?.myself?.id===data.value.employeeId),minimum=computed(()=>data.value?tomorrow(data.value.serverDate):''),assignmentMinimum=computed(()=>{const dates=data.value?.assignments.map(a=>a.effectiveFrom)||[];return dates.reduce((max,d)=>d>=max?tomorrow(d):max,minimum.value)})
const tab=ref<'settings'|'addresses'|'history'>('settings')
const ui=computed(()=>workModeDisplayText(english()))
const policyGroups=computed(()=>groupAssignments(data.value?.assignments||[],data.value?.serverDate||''))
const currentAssignment=computed(()=>policyGroups.value.find(g=>g.status==='current')?.assignment)
const upcoming=computed(()=>policyGroups.value.filter(g=>g.status==='upcoming').reverse())
const pendingAddresses=computed(()=>data.value?.addresses.filter(a=>a.status==='pending')||[])
const reviewedAddresses=computed(()=>[...(data.value?.addresses.filter(a=>a.status!=='pending')||[])].sort((a,b)=>b.effectiveFrom.localeCompare(a.effectiveFrom)||b.createdAt.localeCompare(a.createdAt)))
const activeAddress=computed(()=>reviewedAddresses.value.find(a=>a.status==='approved'&&a.effectiveFrom<=(data.value?.serverDate||'')))
function addressStatus(a:HomeAddress){return a.status!=='approved'?w(a.status):a.effectiveFrom>(data.value?.serverDate||'')?w('approvedFuture'):a.id===activeAddress.value?.id?ui.value.activeAddress:ui.value.olderAddress}
function refresh(){void load(()=>api().get<WorkModeData>(employeeId?`/employees/${encodeURIComponent(employeeId)}/work-mode`:'/work-mode'),v=>{data.value=v;const latest=v.assignments.reduce<WorkAssignment|undefined>((last,a)=>!last||a.version>last.version?a:last,undefined);mode.value=latest?.mode||'legacy';allowedModes.value=permittedModes(mode.value,latest?.allowedModes);inherit.value=!latest||latest.origin==='position';if(!effectiveFrom.value||effectiveFrom.value<minimum.value)effectiveFrom.value=assignmentMinimum.value;if(!addressDate.value||addressDate.value<minimum.value)addressDate.value=minimum.value})}
async function send(command?:Pending){if(busy.value||(!pending.value&&!command))return;if(!pending.value)pending.value=JSON.parse(JSON.stringify(command));busy.value=true;failure.value='';notice.value='';try{const original=pending.value!,result=await api().put<WorkAssignment|undefined>(original.path,original.body);if(!current())return;pending.value=null;notice.value=w(original.assignmentVersion!==undefined&&result?.version===original.assignmentVersion?'noChange':'saved');refresh()}catch(e){if(!current())return;if(e instanceof WorkforceApiError&&e.status>=400&&e.status<500)pending.value=null;failure.value=workModeError(e,english())||error(e)}finally{if(current())busy.value=false}}
function saveMode(){if(!data.value?.canManage||pending.value)return;if(effectiveFrom.value<minimum.value){failure.value=w('invalidDate');return}const expectedVersion=Math.max(0,...data.value.assignments.map(a=>a.version));void send({path:`/employees/${encodeURIComponent(data.value.employeeId)}/work-modes/${crypto.randomUUID()}`,assignmentVersion:expectedVersion,body:{expectedVersion,effectiveFrom:effectiveFrom.value,mode:mode.value,inheritPosition:inherit.value,...(!inherit.value?{allowedModes:permittedModes(mode.value,allowedModes.value)}:{})}})}
function applyAddress(){if(!self.value||pending.value||pointBusy.value)return;if(addressDate.value<minimum.value){failure.value=workModeError({code:'WORKFORCE_HOME_EFFECTIVE_DATE_INVALID'},english());return}void send({path:`/home-addresses/${crypto.randomUUID()}`,body:{point:point.value,effectiveFrom:addressDate.value}})}
function selfReview(a:HomeAddress){return self.value||a.applicantId===home.value?.myself?.accountId}
function addressBlock(a:HomeAddress){return a.effectiveFrom<minimum.value?'expiredAddress':data.value?.addresses.some(other=>other.id!==a.id&&other.status==='approved'&&other.effectiveFrom===a.effectiveFrom)?'dateConflict':null}
function decide(a:HomeAddress,status:'approved'|'rejected'){if(!data.value?.canReview||selfReview(a)||a.status!=='pending'||pending.value||!notes.value[a.id]?.trim()||notes.value[a.id]!.trim().length>500||(status==='approved'&&addressBlock(a)))return;void send({path:`/home-addresses/${encodeURIComponent(a.id)}/decision`,body:{expectedVersion:a.version,status,note:notes.value[a.id]!.trim()}})}
onMounted(refresh)
</script>
<template>
 <section class="wf-panel wm-page">
  <header class="wf-toolbar"><h2>{{w('title')}}<span v-if="data"> · {{data.employeeName}}</span></h2><button :disabled="busy||loading||pointBusy||!!pending" @click="refresh">{{w('refresh')}}</button></header>
  <div class="wf-inline wm-back"><RouterLink v-if="employeeId" :to="base+'/employees'">{{w('backEmployees')}}</RouterLink><RouterLink v-else :to="base+'/clock'">{{w('backClock')}}</RouterLink></div>
  <PhaseStatus :failure="failure" :notice="notice" :loading="loading"/>
  <div v-if="pending" role="status"><p>{{w('unknown')}}</p><button :disabled="busy" @click="send()">{{w('retry')}}</button></div>
  <template v-if="data&&!loading">
   <nav class="wm-nav" :aria-label="w('title')"><button v-for="key in (['settings','addresses','history'] as const)" :key="key" :data-workmode-tab="key" :aria-pressed="tab===key" :aria-controls="'wm-'+key" :disabled="busy||pointBusy" @click="tab=key">{{ui[key]}}<span v-if="key==='addresses'&&pendingAddresses.length" class="wf-badge wf-badge-warning">{{pendingAddresses.length}}</span></button></nav>
   <section v-if="tab==='settings'" id="wm-settings">
    <article class="wm-current-summary"><header><h3>{{ui.currentPolicy}}</h3><span class="wf-badge wm-current">{{ui.current}}</span></header><strong>{{(data.policy?.options?.map(o=>o.mode)||[data.policy?.mode||'legacy']).map(m=>w(m)).join(' / ')}}</strong><p v-if="(data.policy?.options?.length||0)>1">{{w('defaultMode')}}: {{w(data.policy!.mode)}}</p><p v-if="currentAssignment">{{w('effectiveFrom')}}: {{currentAssignment.effectiveFrom}} · {{w(currentAssignment.origin==='position'?'position':'personal')}}</p><p class="wf-hint">{{ui.settingsHint}}</p></article>
    <section v-if="upcoming.length" class="wm-section"><h3>{{ui.upcomingPolicies}}</h3><WorkModePolicyList :groups="upcoming"/></section>
    <form v-if="data.canManage" class="wm-section" @submit.prevent="saveMode"><h3>{{ui.editPolicy}}</h3><p class="wf-hint">{{w('nextDay')}}</p><fieldset :disabled="busy||!!pending"><label class="wf-check"><input v-model="inherit" type="checkbox">{{w('inherit')}}</label><WorkModeFields v-if="!inherit" v-model="mode" v-model:modes="allowedModes"/><label>{{w('effectiveFrom')}}<input v-model="effectiveFrom" type="date" :min="minimum" required></label><div class="wf-form-actions"><button class="primary">{{ui.savePolicy}}</button></div></fieldset></form>
   </section>
   <section v-else-if="tab==='addresses'" id="wm-addresses">
    <p class="wf-hint">{{ui.addressHint}}</p>
    <form v-if="self" class="wm-section" @submit.prevent="applyAddress"><fieldset :disabled="busy||!!pending"><legend>{{w('address')}}</legend><WorkPointFields :point="point" @locating="pointBusy=$event"/><label>{{w('effectiveFrom')}}<input v-model="addressDate" type="date" :min="minimum" required></label><div class="wf-form-actions"><button class="primary" :disabled="pointBusy">{{w('submit')}}</button></div></fieldset></form>
    <section data-address-group="pending"><h3>{{w('pendingAddresses')}} <span class="wf-badge">{{pendingAddresses.length}}</span></h3><p v-if="!pendingAddresses.length" class="wf-empty">{{w('empty')}}</p>
     <article v-for="a in pendingAddresses" :key="a.id" class="wm-address-card"><header><strong>{{a.point.label}}</strong><span class="wf-badge wf-badge-warning">{{w('pending')}}</span></header><p>{{w('effectiveFrom')}}: {{a.effectiveFrom}}</p><details><summary>{{ui.coordinates}}</summary><p>{{a.point.latitude}}, {{a.point.longitude}} · {{a.point.radiusMeters}} m</p></details><p v-if="addressBlock(a)" class="wf-hint">{{w(addressBlock(a)!)}} {{w('earliestDate')}}: {{minimum}}</p><p v-if="selfReview(a)" class="wf-hint">{{w('selfReview')}}</p><div v-else-if="data.canReview" class="wm-review"><label>{{w('note')}}<input v-model="notes[a.id]" maxlength="500" required :disabled="busy||!!pending"></label><div class="wf-form-actions"><button :disabled="busy||!!pending||!notes[a.id]?.trim()||!!addressBlock(a)" class="primary" @click="decide(a,'approved')">{{w('approve')}}</button><button :disabled="busy||!!pending||!notes[a.id]?.trim()" @click="decide(a,'rejected')">{{w('reject')}}</button></div></div></article>
    </section>
    <details class="wm-section" data-address-group="history"><summary>{{ui.addressHistory}} · {{reviewedAddresses.length}}</summary><p v-if="!reviewedAddresses.length" class="wf-empty">{{w('empty')}}</p><article v-for="a in reviewedAddresses" :key="a.id" class="wm-address-card"><header><strong>{{a.point.label}}</strong><span class="wf-badge">{{addressStatus(a)}}</span></header><p>{{w('effectiveFrom')}}: {{a.effectiveFrom}}</p><p v-if="a.reviewerId">{{w('reviewer')}}: {{data.reviewerNames?.[a.reviewerId]?.trim()||ui.unknownReviewer}}</p><p v-if="a.reviewNote">{{w('reviewNote')}}: {{a.reviewNote}}</p><details><summary>{{ui.coordinates}}</summary><p>{{a.point.latitude}}, {{a.point.longitude}} · {{a.point.radiusMeters}} m</p></details></article></details>
   </section>
   <section v-else id="wm-history"><h3>{{w('history')}}</h3><p class="wf-hint">{{ui.historyHint}}</p><WorkModePolicyList :groups="policyGroups"/><p v-if="!policyGroups.length" class="wf-empty">{{w('empty')}}</p></section>
  </template>
 </section>
</template>
<style scoped>
.wm-back{margin-bottom:16px}.wm-nav{display:flex;gap:6px;flex-wrap:wrap;border-bottom:1px solid #e0e6ed;padding-bottom:12px;margin-bottom:18px}.wm-nav button{gap:8px}.wm-nav [aria-pressed=true]{background:#23567c;border-color:#23567c;color:#fff}.wm-page h3{font-size:14px;margin:0 0 12px}.wm-section{margin-top:20px;padding-top:18px;border-top:1px solid #e5eaf0}.wm-current-summary{background:#f2f7fb;border:1px solid #d8e6f0;border-radius:8px;padding:14px}.wm-page header{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}.wm-current-summary header{margin-bottom:12px}.wm-current-summary h3{margin:0}.wm-current-summary>strong{font-size:16px}.wm-page :deep(.wm-current){color:#28634a;background:#e5f2eb}.wm-page :deep(.wm-policy-list){display:grid;gap:10px}.wm-page :deep(.wm-policy-card),.wm-address-card{padding:12px 14px;border:1px solid #e0e6ed;border-radius:7px;overflow-wrap:anywhere}.wm-page :deep(.wm-policy-card header){display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.wm-page :deep(.wm-policy-card p),.wm-address-card p{margin:7px 0}.wm-address-card{margin-top:10px}.wm-page :deep(summary){cursor:pointer;color:#53677e;font-size:12px;min-height:28px;padding:4px 0}.wm-page :deep(summary:focus-visible){outline:2px solid #87b6d6;outline-offset:2px}.wm-review{margin-top:14px}.wm-page :deep(ul){margin:6px 0;padding-left:18px}.wm-page :deep(.wf-hint){margin-bottom:0}@media(max-width:600px){.wm-nav button{flex:1 1 auto}.wm-page :deep(.wm-policy-card),.wm-address-card{padding:12px}.wm-page .wf-form-actions{flex-wrap:wrap}}
</style>
