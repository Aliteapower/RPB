<script setup lang="ts">
import {restaurantShiftRange} from '../restaurantShifts'
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {useScheduleText} from '../scheduleText'
import {WorkforceApiError} from '../api'
import type {Shift} from '../types'
import type {AdjustmentTarget,AdjustmentRequest,AdjustmentPreview,AdjustmentResult} from '../bulkAdjustmentTypes'
import PhaseStatus from './PhaseStatus.vue'
const props=defineProps<{date:string;sourceShiftId:string;targets:AdjustmentTarget[]}>(),emit=defineEmits<{close:[];saved:[result:AdjustmentResult]}>()
const {api,home,store,current,t,p,load,loading,busy,failure,format,english}=usePhasePage(),{s}=useScheduleText()
const dialog=ref<HTMLDialogElement|null>(null),shifts=ref<Shift[]>([]),target=ref(''),preview=ref<AdjustmentPreview|null>(null),lastRequest=ref<AdjustmentRequest|null>(null),offset=ref(0)
const command=ref<{id:string;request:AdjustmentRequest;fingerprint:string}|null>(null),blocked=computed(()=>busy.value||!!command.value)
const targets=props.targets.map(row=>({...row}))
const reasons:Record<string,string>={SOURCE_CHANGED:s('adjustSourceChanged'),IN_USE:s('scheduleProtected'),MULTIPLE_SEGMENTS:s('adjustMultiple'),OUTSIDE_WINDOW:s('OUTSIDE_WINDOW'),SHIFT_UNAVAILABLE:s('SHIFT_UNAVAILABLE'),INVALID_TIME:s('INVALID_TIME'),NOT_ASSIGNED:s('NOT_ASSIGNED'),TIME_OVERLAP:s('TIME_OVERLAP'),UNCHANGED:s('adjustUnchanged')}
watch(target,()=>{preview.value=null;lastRequest.value=null;offset.value=0})
function close(){if(!blocked.value)emit('close')}
function options(){void load(()=>api().get<Shift[]>('/shifts'),rows=>{shifts.value=rows.filter(row=>row.enabled&&row.id!==props.sourceShiftId)})}
function check(){if(blocked.value||loading.value||!target.value)return;const request:AdjustmentRequest={date:props.date,sourceShiftId:props.sourceShiftId,targetShiftId:target.value,targets};preview.value=null;lastRequest.value=null;offset.value=0;void load(()=>api().put<AdjustmentPreview>('/schedule-adjustments/preview',request),result=>{if(target.value===request.targetShiftId){preview.value=result;lastRequest.value=request}})}
async function submit(){
 if(busy.value||!preview.value||!lastRequest.value||preview.value.ready===0)return
 if(!command.value)command.value={id:crypto.randomUUID(),request:lastRequest.value,fingerprint:preview.value.fingerprint}
 const pending=command.value,owner=store.value;busy.value=true;failure.value=''
 try{const result=await api().put<AdjustmentResult>('/schedule-adjustments/'+pending.id,{request:pending.request,fingerprint:pending.fingerprint});if(current()&&owner===store.value){command.value=null;emit('saved',result)}}
 catch(error){if(current()&&owner===store.value){if(error instanceof WorkforceApiError&&error.status>=400&&error.status<500){command.value=null;preview.value=null;lastRequest.value=null;failure.value=error.code==='WORKFORCE_ADJUST_CHANGED'?s('adjustChanged'):error.status===401||error.status===403?p('noAccess'):p('invalid')}else failure.value=s('adjustRetry')}}
 finally{if(current())busy.value=false}
}
onMounted(()=>{dialog.value?.showModal?.();if(home.value?.employeeManage)options()})
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-bulk-dialog" aria-modal="true" aria-labelledby="wf-bulk-adjust-title" @cancel.prevent="close">
 <header class="wf-toolbar"><h2 id="wf-bulk-adjust-title">{{s('bulkAdjust')}}</h2><button type="button" class="wf-dialog-close" :aria-label="s('closeDialog')" :disabled="blocked" @click="close">×</button></header>
 <p>{{home?.stores.find(row=>row.id===store)?.name}} · {{date}} · {{targets.length}} {{s('peopleUnit')}}</p>
 <p class="wf-hint">{{s('adjustHint')}}</p>
 <PhaseStatus :loading="loading" :failure="failure"/>
 <p v-if="!home?.employeeManage" role="alert">{{p('noAccess')}}</p>
 <template v-else>
  <div class="wf-filters"><label>{{s('adjustTarget')}}<select v-model="target" :disabled="blocked||loading"><option value="" disabled>{{t('select')}}</option><option v-for="shift in shifts" :key="shift.id" :value="shift.id">{{shift.name}} · {{restaurantShiftRange(shift,english())}}</option></select></label><button type="button" :disabled="blocked||loading||!target" @click="check">{{s('preview')}}</button></div>
  <p v-if="!loading&&!shifts.length" class="wf-hint">{{s('adjustNoShift')}}</p>
  <button v-if="failure&&!shifts.length" type="button" :disabled="blocked||loading" @click="options">{{p('reload')}}</button>
  <section v-if="preview" class="wf-section"><div class="wf-bulk-summary"><span>{{s('adjustReady')}} <strong>{{preview.ready}}</strong></span><span>{{s('adjustUnchanged')}} <strong>{{preview.unchanged}}</strong></span><span>{{s('conflict')}} <strong>{{preview.conflicts}}</strong></span></div>
   <div class="wf-table-wrap"><table><thead><tr><th>{{p('employee')}}</th><th>{{s('adjustOriginal')}}</th><th>{{s('adjustTarget')}}</th><th>{{p('status')}}</th><th>{{s('reason')}}</th></tr></thead><tbody><tr v-for="row in preview.items.slice(offset,offset+50)" :key="row.scheduleId"><td>{{row.employeeName}} <small>{{row.employeeNo}}</small></td><td>{{row.sourceShiftName}}<br>{{format(row.sourceStartAt,row.sourceTimezone)}} – {{format(row.sourceEndAt,row.sourceTimezone)}}</td><td>{{preview.targetShiftName}}<br>{{row.startAt&&row.endAt?format(row.startAt,preview.timezone)+' – '+format(row.endAt,preview.timezone):'—'}}</td><td>{{row.status==='ready'?s('adjustReady'):row.status==='unchanged'?s('adjustUnchanged'):s('conflict')}}</td><td class="wf-wrap-text">{{reasons[row.reason]||'—'}}</td></tr></tbody></table></div>
   <footer class="wf-pagination"><span>{{offset+1}}–{{Math.min(offset+50,preview.total)}} / {{preview.total}}</span><button type="button" :disabled="busy||offset===0" @click="offset-=50">{{t('previous')}}</button><button type="button" :disabled="busy||offset+50>=preview.total" @click="offset+=50">{{t('next')}}</button></footer>
   <footer class="wf-form-actions"><button type="button" :disabled="blocked" @click="close">{{t('cancel')}}</button><button type="button" class="primary" :disabled="busy||preview.ready===0" @click="submit">{{busy?t('saving'):command?s('retryScheduleAction'):s('adjustAction')+' '+preview.ready+' '+s('entries')+'，'+s('skip')+' '+(preview.unchanged+preview.conflicts)+' '+s('entries')}}</button></footer>
  </section>
 </template>
</dialog></template>
