<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {useScheduleText} from '../scheduleText'
import {WorkforceApiError} from '../api'
import {addDays} from '../time'
import type {BulkResult} from '../bulkScheduleTypes'
import type {WeekCopyPreview,WeekCopyRequest} from '../weekCopyTypes'
import PhaseStatus from './PhaseStatus.vue'
const props=defineProps<{sourceWeek:string;targetWeek:string}>(),emit=defineEmits<{saved:[result:BulkResult,targetWeek:string];close:[]}>()
const {api,home,store,current,t,p,loading,busy,failure,load,format}=usePhasePage(),{s}=useScheduleText()
const dialog=ref<HTMLDialogElement|null>(null),preview=ref<WeekCopyPreview|null>(null),offset=ref(0),command=ref<{id:string;request:WeekCopyRequest;fingerprint:string}|null>(null)
const blocked=computed(()=>busy.value||!!command.value),request:WeekCopyRequest={sourceWeek:props.sourceWeek,targetWeek:props.targetWeek}
const reasons:Record<string,string>={EXISTING_SCHEDULE:s('EXISTING_SCHEDULE'),TIME_OVERLAP:s('TIME_OVERLAP'),NOT_ASSIGNED:s('NOT_ASSIGNED'),SHIFT_UNAVAILABLE:s('SHIFT_UNAVAILABLE'),INVALID_TIME:s('INVALID_TIME'),OUTSIDE_WINDOW:s('OUTSIDE_WINDOW'),CANDIDATE_OVERLAP:s('CANDIDATE_OVERLAP')}
function close(){if(!blocked.value)emit('close')}
function check(){if(blocked.value)return;preview.value=null;offset.value=0;void load(()=>api().put<WeekCopyPreview>('/schedule-copy-bulk/preview',request),data=>{preview.value=data})}
async function submit(){
 if(busy.value||!preview.value||preview.value.ready===0)return
 if(!command.value)command.value={id:crypto.randomUUID(),request:{...request},fingerprint:preview.value.fingerprint}
 const pending=command.value,owner=store.value;busy.value=true;failure.value=''
 try{const result=await api().put<BulkResult>('/schedule-copy-bulk/'+pending.id,{request:pending.request,fingerprint:pending.fingerprint});if(current()&&owner===store.value){command.value=null;emit('saved',result,pending.request.targetWeek)}}
 catch(error){if(current()&&owner===store.value){if(error instanceof WorkforceApiError&&error.status>=400&&error.status<500){command.value=null;preview.value=null;failure.value=error.code==='WORKFORCE_COPY_CHANGED'?s('copyChanged'):error.code==='WORKFORCE_COPY_TOO_LARGE'?s('copyMax'):error.status===401||error.status===403?p('noAccess'):p('invalid')}else failure.value=s('copyRetry')}}
 finally{if(current())busy.value=false}
}
onMounted(()=>{dialog.value?.showModal?.();if(home.value?.employeeManage)check()})
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-bulk-dialog" aria-modal="true" aria-labelledby="wf-week-copy-title" @cancel.prevent="close">
 <header class="wf-toolbar"><h2 id="wf-week-copy-title">{{s('copyPreview')}}</h2><button type="button" class="wf-dialog-close" :aria-label="s('closeDialog')" :disabled="blocked" @click="close">×</button></header>
 <p class="wf-hint">{{home?.stores.find(item=>item.id===store)?.name}} · {{s('copyMax')}} {{s('noOverwrite')}}</p>
 <div class="wf-copy-periods"><p><strong>{{s('copySource')}}</strong><br>{{sourceWeek}} – {{addDays(sourceWeek,6)}}</p><p><strong>{{p('targetWeek')}}</strong><br>{{targetWeek}} – {{addDays(targetWeek,6)}}</p></div>
 <p class="wf-hint">{{s('copyTemplates')}}</p>
 <PhaseStatus :loading="loading" :failure="failure"/>
 <p v-if="!home?.employeeManage" role="alert">{{t('noAccess')}}</p>
 <template v-else>
  <button type="button" :disabled="loading||blocked" @click="check">{{s('repreview')}}</button>
  <section v-if="preview" class="wf-section">
   <div class="wf-bulk-summary"><span>{{s('count')}} <strong>{{preview.employeeCount}}</strong></span><span>{{s('ready')}} <strong>{{preview.ready}}</strong></span><span>{{s('existing')}} <strong>{{preview.existing}}</strong></span><span>{{s('conflict')}} <strong>{{preview.conflicts}}</strong></span></div>
   <p v-if="!preview.total" class="wf-hint">{{s('copyEmpty')}}</p>
   <div v-else class="wf-table-wrap"><table><thead><tr><th>{{p('employee')}}</th><th>{{s('sourceDate')}}</th><th>{{s('targetDate')}}</th><th>{{p('shift')}}</th><th>{{p('plan')}}</th><th>{{p('status')}}</th><th>{{s('reason')}}</th></tr></thead><tbody><tr v-for="row in preview.items.slice(offset,offset+50)" :key="row.sourceId"><td>{{row.employeeName}} <small>{{row.employeeNo}}</small></td><td>{{row.sourceDate}}</td><td>{{row.date}}</td><td>{{row.shiftName}}</td><td>{{row.startAt&&row.endAt?format(row.startAt,preview.timezone)+' – '+format(row.endAt,preview.timezone):'—'}}</td><td>{{row.status==='ready'?s('ready'):row.status==='existing'?s('existing'):s('conflict')}}</td><td class="wf-wrap-text">{{reasons[row.reason]||'—'}}</td></tr></tbody></table></div>
   <footer class="wf-pagination"><span>{{preview.total?offset+1:0}}–{{Math.min(offset+50,preview.total)}} / {{preview.total}}</span><button type="button" :disabled="busy||offset===0" @click="offset-=50">{{t('previous')}}</button><button type="button" :disabled="busy||offset+50>=preview.total" @click="offset+=50">{{t('next')}}</button></footer>
   <p v-if="command" role="status" class="wf-hint">{{s('copyRetry')}}</p>
   <footer class="wf-form-actions"><button type="button" :disabled="blocked" @click="close">{{t('cancel')}}</button><button type="button" class="primary" :disabled="busy||loading||preview.ready===0" @click="submit">{{busy?t('saving'):command?s('retryScheduleAction'):s('create')+' '+preview.ready+' '+s('entries')+'，'+s('skip')+' '+(preview.existing+preview.conflicts)+' '+s('entries')}}</button></footer>
  </section>
 </template>
</dialog></template>
