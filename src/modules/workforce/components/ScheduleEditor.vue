<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {shiftRuleSummary} from '../restPolicy'
import {useScheduleText} from '../scheduleText'
import {WorkforceApiError} from '../api'
import {storeToday,addDays} from '../time'
import {restaurantShiftRange,restaurantSchedulePreview,restaurantText} from '../restaurantShifts'
import type {Schedule,ScheduleResult,Shift,Option} from '../types'
import PhaseStatus from './PhaseStatus.vue'
const props=defineProps<{id?:string;employeeId?:unknown;workDate?:unknown}>()
const emit=defineEmits<{saved:[schedule:Schedule];deleted:[id:string];close:[];blocked:[value:boolean]}>()
const existing=props.id||'',id=existing||crypto.randomUUID(),{api,home,t,p,loading,busy,failure,load,write,english}=usePhasePage(),{s}=useScheduleText()
const zone=ref('Asia/Singapore'),employees=ref<Option[]>([]),shifts=ref<Shift[]>([]),snapshot=ref<Schedule|null>(null),ready=ref(false)
const draft=ref({expectedVersion:0,employeeId:'',shiftId:'',workDate:storeToday(),status:'scheduled' as 'scheduled'|'cancelled'})
// Keep the UUID and exact body until a potentially committed request is resolved.
const pending=ref<typeof draft.value|null>(null),deletion=ref<{expectedVersion:number}|null>(null),blocked=computed(()=>busy.value||!!pending.value||!!deletion.value)
const selectedShift=computed(()=>draft.value.status==='cancelled'?snapshot.value?.shift:shifts.value.find(s=>s.id===draft.value.shiftId))
watch(blocked,value=>emit('blocked',value),{immediate:true,flush:'sync'})
onMounted(()=>{if(home.value?.employeeManage)void load(async()=>{
 const today=storeToday();const [shifts,page]=await Promise.all([api().get<Shift[]>('/shifts'),api().get<ScheduleResult>(`/schedule?from=${today}&to=${addDays(today,30)}`)])
 let item=page.items.find(v=>v.id===id)
 if(existing&&!item){const detail=await api().get<{attendance:{schedule:Schedule}}>('/attendance/'+id);item=detail.attendance.schedule}
 return {shifts,page,item}
},result=>{
 shifts.value=result.shifts;employees.value=result.page.employees;zone.value=result.item?.timezone||result.page.timezone
 const item=result.item
 if(item){snapshot.value=item;draft.value={expectedVersion:item.version,employeeId:item.employeeId,shiftId:item.shiftId,workDate:item.workDate,status:item.status};if(!employees.value.some(e=>e.id===item.employeeId))employees.value.push({id:item.employeeId,name:item.employeeName})}
 else {const today=storeToday(zone.value),employeeId=props.employeeId,date=props.workDate;draft.value.workDate=today
 if(typeof employeeId==='string'&&employees.value.some(e=>e.id===employeeId))draft.value.employeeId=employeeId
 if(typeof date==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(date)&&addDays(date,0)===date&&date>=today&&date<=addDays(today,90))draft.value.workDate=date}
 ready.value=true
})})
function close(){if(!blocked.value)emit('close')}
async function recover(body:typeof draft.value){
 try {
  const result=await api().get<{attendance:{schedule:Schedule}}>('/attendance/'+id),row=result?.attendance?.schedule
  if(row&&row.id===id&&row.version===body.expectedVersion+1&&row.employeeId===body.employeeId&&row.shiftId===body.shiftId&&row.workDate===body.workDate&&row.status===body.status)return row
 }catch{/* An unresolved request keeps its original body and UUID for retry. */}
 return null
}
function save(cancel=false,restore=false){
 if(busy.value||deletion.value)return
 if(!draft.value.employeeId||!draft.value.shiftId){failure.value=p('invalid');return}
 const original=snapshot.value,retrying=!!pending.value
 const body:typeof draft.value=pending.value||(cancel&&original?{expectedVersion:original.version,employeeId:original.employeeId,shiftId:original.shiftId,workDate:original.workDate,status:'cancelled'}:restore&&original?{expectedVersion:original.version,employeeId:original.employeeId,shiftId:original.shiftId,workDate:original.workDate,status:'scheduled'}:{...draft.value})
 pending.value=body;let saved:Schedule
 void write(async()=>{try{saved=await api().put<Schedule>('/schedule/'+id,body)}catch(error){
  const rejected=error instanceof WorkforceApiError&&error.status>=400&&error.status<500
  if(!rejected||retrying&&error instanceof WorkforceApiError&&error.status===409){const row=await recover(body);if(row){saved=row;return}}
  if(rejected)pending.value=null
  throw error
 }},()=>{pending.value=null;emit('saved',saved)})
}
function remove(){
 if(busy.value||pending.value||!snapshot.value)return
 if(!deletion.value)deletion.value={expectedVersion:snapshot.value.version}
 const body=deletion.value
 void write(async()=>{try{await api().put('/schedule/'+id+'/deletion',body)}catch(error){if(error instanceof WorkforceApiError&&error.status>=400&&error.status<500)deletion.value=null;throw error}},()=>{deletion.value=null;emit('deleted',id)})
}

</script>
<template><section class="wf-schedule-editor">
 <header class="wf-toolbar"><h2 id="wf-schedule-editor-title">{{p(existing?'editSchedule':'newSchedule')}}</h2><button type="button" class="wf-dialog-close" :aria-label="s('closeDialog')" :disabled="blocked" @click="close">×</button></header>
 <PhaseStatus :failure="failure===p('activeConflict')?s('scheduleProtected'):failure" :loading="loading"/>
 <p v-if="!home?.employeeManage" role="alert">{{t('noAccess')}}</p>
 <form v-else-if="ready&&!loading" @submit.prevent="save()">
  <fieldset :disabled="blocked||draft.status==='cancelled'">
   <label>{{p('employee')}}<select v-model="draft.employeeId" required><option value="" disabled>{{t('select')}}</option><option v-for="employee in employees" :key="employee.id" :value="employee.id">{{employee.name}}</option></select></label>
   <label>{{p('shift')}}<select v-model="draft.shiftId" required><option value="" disabled>{{t('select')}}</option><option v-for="shift in shifts.filter(shift=>shift.enabled||shift.id===snapshot?.shiftId)" :key="shift.id" :value="shift.id" :disabled="!shift.enabled">{{shift.name}} · {{restaurantShiftRange(shift,english())}}{{shift.rules?' · '+shiftRuleSummary(shift,'',english()):''}} {{!shift.enabled?t('disabled'):''}}</option></select></label>
   <label>{{p('workDate')}}<input v-model="draft.workDate" type="date" required :min="storeToday(zone)" :max="addDays(storeToday(zone),90)"></label>
   <p class="wf-hint">{{restaurantText(english()).business}}</p><p v-if="selectedShift" class="wf-hint" role="status">{{restaurantText(english()).actual}}: {{restaurantSchedulePreview(draft.workDate,selectedShift)}}</p><p class="wf-hint">{{p('timezone')}}: {{zone}}</p><p v-if="snapshot" class="wf-hint">{{p('snapshot')}}: {{snapshot.shift.name}} {{restaurantShiftRange(snapshot.shift,english())}} · {{shiftRuleSummary(snapshot.shift,p(snapshot.shift.breakMode),english())}} · {{p(snapshot.status)}}</p>
  </fieldset>
  <p v-if="snapshot?.status==='cancelled'" class="wf-hint">{{s('restoreHint')}}</p>
  <p v-if="snapshot" class="wf-hint">{{s('deleteScheduleHint')}}</p>
  <p v-if="deletion&&!busy" role="status" class="wf-hint">{{s('retryDelete')}}</p>
  <p v-if="pending&&!busy" role="status" class="wf-hint">{{s('retrySchedule')}}</p>
  <footer class="wf-form-actions"><button type="button" :disabled="blocked" @click="close">{{t('cancel')}}</button><button v-if="snapshot" type="button" class="danger" :disabled="busy||!!pending" @click="remove">{{busy?t('saving'):deletion?s('retryDeleteAction'):s('deleteSchedule')}}</button><button v-if="snapshot?.status==='scheduled'" type="button" :disabled="blocked" @click="save(true)">{{p('cancelSchedule')}}</button><button v-if="snapshot?.status==='cancelled'" type="button" class="primary" :disabled="busy||!!deletion" @click="save(false,true)">{{busy?t('saving'):pending?s('retryScheduleAction'):s('restoreSchedule')}}</button><button v-if="draft.status!=='cancelled'" class="primary" :disabled="busy||!!deletion">{{busy?t('saving'):pending?s('retryScheduleAction'):t('save')}}</button></footer>
 </form>
</section></template>

