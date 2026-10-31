<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue'
import {usePayrollPage} from '../payrollPage'
import {useCdacText} from '../cdac'
import {useCdacBulkText,validCdacBulk,type CdacBulkSelection,type CdacBulkPreview,type CdacBulkResult} from '../cdacBulk'
import {WorkforceApiError} from '../api'
import {storeToday} from '../time'
import type {Option} from '../types'
import PhaseStatus from './PhaseStatus.vue'
const props=defineProps<{companyId:string;employees:Option[]}>(),emit=defineEmits<{close:[];saved:[result:CdacBulkResult]}>()
const {api,t,p,r,current,store,base,loading,busy,failure,load}=usePayrollPage(),{d}=useCdacText(),{b}=useCdacBulkText()
const dialog=ref<HTMLDialogElement|null>(null),search=ref(''),selected=ref<string[]>([]),draft=reactive({month:storeToday().slice(0,7),status:'applicable' as 'applicable'|'exempt'|'manual',reference:'',reason:''}),result=ref<CdacBulkResult|null>(null),preview=ref<CdacBulkPreview|null>(null),offset=ref(0)
const pending=ref<{id:string;write:{selection:CdacBulkSelection;fingerprint:string;reason:string}}|null>(null)
const frozen=computed(()=>loading.value||busy.value||!!pending.value),filtered=computed(()=>props.employees.filter(e=>e.name.toLowerCase().includes(search.value.trim().toLowerCase()))),visibleRows=computed(()=>preview.value?.rows.slice(offset.value,offset.value+50)||[])
function selection():CdacBulkSelection{return {companyId:props.companyId,month:draft.month,employeeIds:[...selected.value].sort(),cdac:{status:draft.status,reference:draft.reference.trim()}}}
watch([draft,selected],()=>{preview.value=null;offset.value=0},{deep:true,flush:'sync'})
function toggle(id:string,checked:boolean){selected.value=checked?[...new Set([...selected.value,id])]:selected.value.filter(v=>v!==id)}
function close(){if(!frozen.value)emit('close')}
function check(){if(frozen.value)return;const c=selection();if(!validCdacBulk(c)||!draft.reason.trim()){failure.value=r('invalid');return}preview.value=null;void load(()=>api().put<CdacBulkPreview>('/pay-profiles/cdac-bulk/preview',c),v=>preview.value=v)}
async function save(){
 if(busy.value||loading.value||!preview.value?.ready||!draft.reason.trim())return
 if(!pending.value)pending.value={id:crypto.randomUUID(),write:{selection:selection(),fingerprint:preview.value.fingerprint,reason:draft.reason.trim()}}
 const command=pending.value,owner=store.value;busy.value=true;failure.value=''
 try{const result=await api().put<CdacBulkResult>('/pay-profiles/cdac-bulk/'+command.id,command.write);if(current()&&owner===store.value){pending.value=null;completed(result)}}
 catch(e){if(current()&&owner===store.value){if(e instanceof WorkforceApiError&&e.status>=400&&e.status<500){pending.value=null;preview.value=null;failure.value=e.code==='WORKFORCE_CDAC_BULK_CHANGED'?b('changed'):e.status===401||e.status===403?p('noAccess'):r('invalid')}else failure.value=b('retryHint')}}
 finally{if(current())busy.value=false}
}
function retryFailed(){if(frozen.value||!result.value)return;selected.value=result.value.requests.filter(row=>row.status!=='submitted').map(row=>row.employeeId);result.value=null;preview.value=null;offset.value=0;failure.value=''}
function completed(value:CdacBulkResult){result.value=value;emit('saved',value)}
onMounted(()=>dialog.value?.showModal?.());onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-bulk-dialog" aria-modal="true" aria-labelledby="wf-cdac-bulk-title" @cancel.prevent="close"><section>
 <header class="wf-toolbar"><h2 id="wf-cdac-bulk-title">{{b('title')}}</h2><button type="button" :disabled="frozen" @click="close">{{t('cancel')}}</button></header>
 <PhaseStatus :loading="loading" :failure="failure"/><p class="wf-hint">{{b('hint')}}</p>
 <fieldset :disabled="frozen||!!result"><div class="wf-grid"><label>{{b('month')}}<input v-model="draft.month" type="month" min="2000-01" max="2099-12" required></label><label>{{d('status')}}<select v-model="draft.status"><option value="applicable">{{d('applicable')}}</option><option value="exempt">{{d('exempt')}}</option><option value="manual">{{d('manual')}}</option></select></label></div>
 <label>{{d('reference')}}<textarea v-model="draft.reference" :required="draft.status!=='applicable'" maxlength="500" rows="2"></textarea></label><p v-if="draft.status==='manual'" class="wf-hint">{{b('manualHint')}}</p>
 <label>{{b('reason')}}<textarea v-model="draft.reason" required maxlength="1000" rows="2"></textarea></label><label>{{b('search')}}<input v-model="search" type="search"></label><div class="wf-toolbar"><button type="button" :disabled="!filtered.length||filtered.length>500" @click="selected=filtered.map(e=>e.id)">{{b('all')}}</button><button type="button" @click="selected=[]">{{b('clear')}}</button><span>{{b('selected')}} {{selected.length}} / 500</span></div>
 <div class="wf-cdac-people"><label v-for="e in filtered" :key="e.id" class="wf-inline"><input type="checkbox" :checked="selected.includes(e.id)" :aria-label="e.name" @change="toggle(e.id,($event.target as HTMLInputElement).checked)">{{e.name}}</label><p v-if="!filtered.length">{{t('empty')}}</p></div>
 <button type="button" :disabled="!validCdacBulk(selection())||!draft.reason.trim()" @click="check">{{b('preview')}}</button></fieldset>
 <section v-if="preview&&!result" class="wf-section"><h3>{{b('result')}} · {{preview.month}}</h3><p>{{b('ready')}} {{preview.ready}} · {{b('unchanged')}} {{preview.unchanged}} · {{b('blocked')}} {{preview.blocked}}</p><p class="wf-hint">{{b('skip')}}</p>
 <div class="wf-table-wrap"><table><thead><tr><th>{{r('employee')}}</th><th>{{b('source')}}</th><th>{{b('before')}}</th><th>{{b('after')}}</th><th>{{b('until')}}</th><th>{{b('result')}}</th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.employeeId"><td>{{row.employeeName}}</td><td>{{row.sourceEffectiveFrom||'—'}}</td><td>{{d(row.previousStatus==='legacy'?'legacy':row.previousStatus as 'applicable'|'exempt'|'manual')}}</td><td>{{row.status==='ready'?d(draft.status)+' · '+preview.month+'-01':'—'}}</td><td>{{row.nextEffectiveFrom||b('ongoing')}}</td><td>{{b(row.status)}} {{row.reason?b(row.reason):''}}</td></tr></tbody></table></div>
 <footer class="wf-toolbar"><span>{{offset+1}}–{{Math.min(offset+50,preview.rows.length)}} / {{preview.rows.length}}</span><button type="button" :disabled="frozen||offset===0" @click="offset-=50">{{t('previous')}}</button><button type="button" :disabled="frozen||offset+50>=preview.rows.length" @click="offset+=50">{{t('next')}}</button></footer>
 <footer class="wf-form-actions"><button type="button" class="primary" :disabled="busy||!preview.ready" @click="save">{{busy?t('saving'):pending?b('retry'):b('save')}} · {{preview.ready}}</button></footer></section>
<section v-if="result" class="wf-section"><h3>{{b('requests')}}</h3><p class="wf-hint">{{b('hint')}}</p><div class="wf-table-wrap"><table><thead><tr><th>{{r('employee')}}</th><th>{{b('result')}}</th><th>{{b('requests')}}</th></tr></thead><tbody><tr v-for="row in result.requests" :key="row.employeeId"><td>{{employees.find(e=>e.id===row.employeeId)?.name||row.employeeId}}</td><td>{{b(row.status)}} <span v-if="row.error">{{b(row.error)}}</span></td><td><RouterLink v-if="row.requestId" :to="{path:base+'/pay-change-requests',query:{companyId,requestId:row.requestId}}">{{row.requestId}}</RouterLink><span v-else>—</span></td></tr></tbody></table></div><p>{{b('unchanged')}} {{result.preview.unchanged}} · {{b('blocked')}} {{result.preview.blocked}}</p><button v-if="result.requests.some(row=>row.status!=='submitted')" type="button" @click="retryFailed">{{b('retryFailed')}}</button></section>
</section></dialog></template>
<style scoped>.wf-cdac-people{max-height:190px;overflow:auto;border:1px solid var(--wf-border,#d8e2ef);border-radius:6px;padding:8px;margin:10px 0;display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:4px}.wf-cdac-people label{margin:0}.wf-cdac-people input{width:auto}</style>
