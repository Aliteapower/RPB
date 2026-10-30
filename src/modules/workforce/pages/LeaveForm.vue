<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import {planLeaveRange,leaveTimeRange,adjustLeavePlan,type LeaveDayPeriod,type LeavePlanRow} from '../leavePlan'
import {WorkforceApiError} from '../api'
import {quickLeavePeriod} from '../leaveDuration'
import {leavePeriodValid,validDay,dateRangeValid} from '../timekeepingUi'
import {storeToday,localInput,localToInstant,addDays,hours} from '../time'
import type {CalendarPage,Leave,LeaveType} from '../phaseThreeTypes'
import type {Schedule,ScheduleResult,AttendanceDetail} from '../types'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),id=crypto.randomUUID(),{api,home,t,p,base,loading,busy,failure,notice,load,write,current,format}=usePhasePage(),{q}=usePhaseThreeText(),date=ref(typeof route.query.date==='string'?route.query.date:storeToday()),schedules=ref<Schedule[]>([]),types=ref<LeaveType[]>([]),selectedId=ref(typeof route.query.schedule==='string'?route.query.schedule:''),typeId=ref(''),whole=ref(true),start=ref(''),end=ref(''),reason=ref(''),zone=ref('Asia/Singapore')
const customHours=ref<number|string>(1),durationError=ref(''),activeHours=ref<number|null>(null)
const multi=ref(false),to=ref(date.value),calendar=ref<CalendarPage|null>(null),existing=ref<Leave[]>([]),loadedRange=ref(''),preview=ref(false)
type Batch={from:string;to:string;items:{id:string;expectedScheduleVersion:number;request:{scheduleId:string;typeId:string;startAt:string;endAt:string;reason:string}}[]}
const frozen=ref<Batch|null>(null),dayEdits=ref<Record<string,LeaveDayPeriod>>({}),excludedDates=ref<string[]>([])
watch([date,to,multi],()=>{dayEdits.value={};excludedDates.value=[]})
const rangeKey=computed(()=>date.value+'/'+(multi.value?to.value:date.value))
const plan=computed(()=>{if(!multi.value||loadedRange.value!==rangeKey.value)return [];let a='',b='';try{a=localToInstant(start.value,zone.value);b=localToInstant(end.value,zone.value)}catch{}return adjustLeavePlan(planLeaveRange(date.value,to.value,schedules.value,calendar.value,existing.value,home.value?.myself?.id||'',whole.value,selected.value,a,b),dayEdits.value,excludedDates.value,existing.value)})
const ready=computed(()=>plan.value.filter(r=>r.status==='ready')),blocked=computed(()=>plan.value.some(r=>['outside','conflict'].includes(r.status)))
const total=computed(()=>hours(ready.value.reduce((n,r)=>n+r.seconds,0)))
watch([date,to,multi,typeId,whole,start,end,reason,dayEdits,excludedDates],()=>{preview.value=false})
watch(start,()=>{if(activeHours.value!==null&&!whole.value)quickHours(activeHours.value)})
function multiChanged(){to.value=date.value;loadedRange.value='';schedules.value=[];preview.value=false;activeHours.value=null}
function wholeChanged(){activeHours.value=null;durationError.value='';if(whole.value)choose()}
function manualEnd(){activeHours.value=null;durationError.value=''}
function customChanged(){quickHours(Number(customHours.value))}
const selected=computed(()=>schedules.value.find(s=>s.id===selectedId.value))
function choose(){durationError.value='';activeHours.value=null;whole.value=true;const s=selected.value;if(s){start.value=localInput(s.startAt,s.timezone);end.value=localInput(s.endAt,s.timezone);zone.value=s.timezone}}
function refresh(initial=false){
 if(frozen.value)return
 if(!home.value?.myself){failure.value=p('noAccess');return}
 if(!validDay(date.value)||(multi.value&&!dateRangeValid(date.value,to.value))){failure.value=p('invalid');return}
 const key=rangeKey.value,rangeEnd=multi.value?to.value:date.value
 preview.value=false;loadedRange.value='';dayEdits.value={};excludedDates.value=[]
 void load(async()=>{const [r,k,a,c,l]=await Promise.all([
  api().get<ScheduleResult>(`${multi.value?'/leave-batch':'/schedule'}?from=${date.value}&to=${rangeEnd}`),api().get<LeaveType[]>('/leave-types'),
  initial&&selectedId.value?api().get<AttendanceDetail>('/attendance/'+encodeURIComponent(selectedId.value)):Promise.resolve(null),
  multi.value?api().get<CalendarPage>(`/calendar?from=${date.value}&to=${rangeEnd}`):Promise.resolve(null),
  multi.value?api().get<Leave[]>(`/leaves?from=${date.value}&to=${rangeEnd}`):Promise.resolve([])
 ]);return {r,k,a,c,l}},({r,k,a,c,l})=>{
  if(key!==rangeKey.value)return
  types.value=k.filter(v=>v.enabled);typeId.value=types.value.some(x=>x.id===typeId.value)?typeId.value:types.value[0]?.id||''
  schedules.value=r.items.filter(v=>v.employeeId===home.value?.myself?.id&&(multi.value||v.status==='scheduled'))
  if(a&&a.attendance.schedule.employeeId===home.value?.myself?.id&&a.attendance.schedule.status==='scheduled'&&!multi.value){const s=a.attendance.schedule;date.value=s.workDate;if(!schedules.value.some(x=>x.id===s.id))schedules.value.push(s)}
  calendar.value=c;existing.value=l;zone.value=r.timezone
  selectedId.value=schedules.value.some(v=>v.id===selectedId.value&&v.status==='scheduled')?selectedId.value:schedules.value.find(v=>v.status==='scheduled')?.id||''
  loadedRange.value=rangeKey.value;choose()
 })
}
function quickHours(hours:number){const s=selected.value;if(!s)return;const from=start.value||localInput(s.startAt,s.timezone),value=quickLeavePeriod(s,from,hours);if(!value){durationError.value=q('durationInvalid');return}activeHours.value=hours;whole.value=false;start.value=from;end.value=value;durationError.value=''}
function clearDurationError(){durationError.value=''}
function save(){if(multi.value){if(frozen.value){void submitBatch();return}if(!preview.value)showPreview();else submitBatch();return}if(durationError.value)return;if(!selected.value||!types.value.some(t=>t.id===typeId.value)){failure.value=p('invalid');return}let startAt:string,endAt:string;try{startAt=whole.value?selected.value.startAt:localToInstant(start.value,selected.value.timezone);endAt=whole.value?selected.value.endAt:localToInstant(end.value,selected.value.timezone)}catch{failure.value=p('timeInvalid');return}if(!leavePeriodValid(selected.value,startAt,endAt,reason.value)){failure.value=p('invalid');return}const body={scheduleId:selected.value.id,typeId:typeId.value,startAt,endAt,reason:reason.value.trim()},day=selected.value.workDate;void write(()=>api().put('/leaves/'+id,body),async()=>{if(current())await router.push({path:base.value+'/leaves/'+id,query:{from:day,to:day}})})}
function dayValue(row:LeavePlanRow,key:'start'|'end'){
 const s=row.schedule;if(!s)return ''
 const edit=dayEdits.value[s.id];if(edit)return edit[key]
 const instant=key==='start'?row.startAt:row.endAt
 return instant?localInput(instant,s.timezone):''
}
function editDay(row:LeavePlanRow,key:'start'|'end',value:string){
 if(busy.value||frozen.value||!row.schedule)return
 dayEdits.value={...dayEdits.value,[row.schedule.id]:{start:dayValue(row,'start'),end:dayValue(row,'end'),[key]:value}}
 preview.value=false
}
function toggleDay(day:string,include:boolean){
 if(busy.value||frozen.value)return
 excludedDates.value=include?excludedDates.value.filter(d=>d!==day):[...new Set([...excludedDates.value,day])]
 preview.value=false
}
function showPreview(){
 if(frozen.value||durationError.value)return
 if(loadedRange.value!==rangeKey.value||!reason.value.trim()||!ready.value.length||blocked.value){failure.value=q('rangeInvalid');return}
 failure.value='';preview.value=true
}
async function submitBatch(){
 if(!frozen.value){if(!preview.value||blocked.value||!ready.value.length)return;frozen.value={from:date.value,to:to.value,items:ready.value.map(r=>({id:crypto.randomUUID(),expectedScheduleVersion:r.schedule!.version,request:{scheduleId:r.schedule!.id,typeId:typeId.value,startAt:r.startAt,endAt:r.endAt,reason:reason.value.trim()}}))}}
 const command=frozen.value
 await write(async()=>{try{return await api().put('/leave-batch',command)}catch(e){if(e instanceof WorkforceApiError&&e.status<500){frozen.value=null;preview.value=false};throw e}},async()=>{frozen.value=null;if(current())await router.push({path:base.value+'/leaves',query:{from:command.from,to:command.to,groupId:command.items[0]?.id}})})
}
onMounted(()=>refresh(true))
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{q('newLeave')}}</h2><p class="wf-hint">{{q('leaveHint')}}</p><label class="wf-check"><input v-model="multi" type="checkbox" :disabled="busy||!!frozen" @change="multiChanged">{{q('multiDay')}}</label><form class="wf-filters" @submit.prevent="refresh()"><label>{{p('workDate')}}<input v-model="date" type="date" required :disabled="busy||!!frozen"></label><label v-if="multi">{{p('to')}}<input v-model="to" type="date" required :min="date" :max="addDays(date,30)" :disabled="busy||!!frozen"></label><button :disabled="loading||busy||!!frozen">{{p('filter')}}</button></form><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><p v-if="!loading&&!types.length" class="wf-hint">{{q('noType')}}</p><p v-if="!loading&&!schedules.length" class="wf-hint">{{q('noSchedule')}}</p><form v-if="!loading&&types.length&&(schedules.length||(multi&&loadedRange===rangeKey))&&home?.myself" @submit.prevent="save"><fieldset :disabled="busy||!!frozen"><label v-if="selected">{{q('selectSchedule')}}<select v-model="selectedId" required @change="choose"><option v-for="s in schedules.filter(s=>s.status==='scheduled')" :key="s.id" :value="s.id">{{s.workDate}} · {{s.shift.name}} · {{format(s.startAt,s.timezone)}} – {{format(s.endAt,s.timezone)}}</option></select></label><label>{{q('selectType')}}<select v-model="typeId" required><option v-for="k in types" :key="k.id" :value="k.id">{{k.name}} · {{q(k.paid?'paid':'unpaid')}}</option></select></label><div class="wf-leave-duration"><label class="wf-check"><input v-model="whole" type="checkbox" @change="wholeChanged">{{q('wholeShift')}}</label><div class="wf-leave-shortcuts" role="group" :aria-label="q('quickDuration')"><span>{{q('quickDuration')}}</span><button v-for="n in [1,2,4]" :key="n" type="button" :class="{primary:activeHours===n}" :aria-pressed="activeHours===n" @click="quickHours(n)">{{n}}{{q('hoursUnit')}}</button><label>{{q('customHours')}}<input v-model="customHours" type="number" step="any" inputmode="decimal" :aria-label="q('customHours')" @input="customChanged"></label><button type="button" :class="{primary:activeHours===Number(customHours)&&![1,2,4].includes(activeHours||0)}" @click="quickHours(Number(customHours))">{{q('applyDuration')}}</button></div><p v-if="activeHours!==null" role="status" class="wf-duration-selected">{{q('selectedDuration')}} {{activeHours}}{{q('hoursUnit')}} · {{start.replace('T',' ')}} → {{end.replace('T',' ')}}</p><p class="wf-hint">{{q('quickDurationHint')}}</p><p v-if="multi" class="wf-hint">{{q('rangePartialHint')}}</p><p v-if="durationError" role="alert" class="wf-error">{{durationError}}</p></div><div v-if="!whole" class="wf-grid"><label>{{q('leaveStart')}}<input v-model="start" required type="datetime-local" @input="clearDurationError"></label><label>{{q('leaveEnd')}}<input v-model="end" required type="datetime-local" @input="manualEnd"></label></div><label>{{p('reason')}}<textarea v-model="reason" required maxlength="500" rows="3"></textarea></label></fieldset><p class="wf-hint">{{p('timezone')}}: {{zone}} · {{q('allocationHint')}}</p><section v-if="multi&&loadedRange===rangeKey" class="wf-leave-preview"><h3>{{q('rangePreview')}} · {{ready.length}} {{q('shiftCount')}} · {{q('total')}} {{total}}</h3><div class="wf-table-wrap"><table><thead><tr><th>{{q('date')}}</th><th>{{q('selectSchedule')}}</th><th>{{q('period')}}</th><th>{{q('durationLabel')}}</th><th>{{q('rangeStatus')}}</th></tr></thead><tbody><tr v-for="(row,i) in plan" :key="i"><td><label v-if="row.schedule?.status==='scheduled'" class="wf-check wf-leave-day"><input type="checkbox" :checked="!excludedDates.includes(row.date)" :aria-label="q('includeDate')+' '+row.date" :disabled="busy||!!frozen" @change="toggleDay(row.date,($event.target as HTMLInputElement).checked)">{{row.date}}</label><span v-else>{{row.date}}</span></td><td>{{row.schedule?.shift.name||'—'}}<small v-if="row.schedule" class="wf-planned-period">{{q('plannedPeriod')}}: {{localInput(row.schedule.startAt,row.schedule.timezone).replace('T',' ')}} → {{localInput(row.schedule.endAt,row.schedule.timezone).replace('T',' ')}}</small></td><td><div v-if="row.schedule?.status==='scheduled'" class="wf-leave-day-period"><label>{{q('leaveStart')}}<input type="datetime-local" :value="dayValue(row,'start')" :aria-label="row.date+' '+q('leaveStart')" :min="localInput(row.schedule.startAt,row.schedule.timezone)" :max="localInput(row.schedule.endAt,row.schedule.timezone)" :disabled="busy||!!frozen||excludedDates.includes(row.date)" @input="editDay(row,'start',($event.target as HTMLInputElement).value)"></label><label>{{q('leaveEnd')}}<input type="datetime-local" :value="dayValue(row,'end')" :aria-label="row.date+' '+q('leaveEnd')" :min="localInput(row.schedule.startAt,row.schedule.timezone)" :max="localInput(row.schedule.endAt,row.schedule.timezone)" :disabled="busy||!!frozen||excludedDates.includes(row.date)" @input="editDay(row,'end',($event.target as HTMLInputElement).value)"></label><small>{{leaveTimeRange(row)}} · {{q('period24')}}</small></div><span v-else>{{leaveTimeRange(row)}}</span></td><td>{{row.seconds?hours(row.seconds):'—'}}</td><td>{{q(row.status==='cancelled'?'skipCancelled':row.status==='empty'?'skipEmpty':row.status)}}</td></tr></tbody></table></div><p class="wf-hint">{{q('dailyEditHint')}}</p><p class="wf-hint">{{q('rangeGrossHint')}}</p></section><p v-if="preview" role="status">{{q('previewConfirmed')}}</p><p v-if="frozen" role="alert" class="wf-hint">{{q('batchRetry')}}</p><footer class="wf-form-actions"><RouterLink v-if="!frozen" class="wf-button" :to="base+'/leaves'">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy||(multi&&(!ready.length||blocked||loadedRange!==rangeKey))">{{busy?t('saving'):multi?(frozen?q('retryBatch'):preview?q('submitBatch'):q('previewBatch')):t('save')}}</button></footer></form></section></template>

<style scoped>
.wf-planned-period{display:block;margin-top:5px;color:#657c98}.wf-leave-day-period{min-width:230px;display:grid;gap:6px}.wf-leave-day-period label{margin:0;font-size:12px}.wf-leave-day-period input{width:100%;margin-top:3px}.wf-leave-day{white-space:nowrap;margin:0}.wf-duration-selected{color:#225d7c;font-weight:600}.wf-leave-preview{margin-top:16px}.wf-leave-duration{margin:16px 0}.wf-leave-shortcuts{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.wf-leave-shortcuts label{display:flex;align-items:center;gap:8px;margin:0}.wf-leave-shortcuts input{width:90px}.wf-leave-duration .wf-hint{margin:8px 0}
</style>
