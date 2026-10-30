<script setup lang="ts">
import RestSwapLabels from '../components/RestSwapLabels.vue'
import CalendarDateLabels from '../components/CalendarDateLabels.vue'
import {nextTick,onBeforeUnmount,onMounted,ref} from 'vue'
import {useScheduleText} from '../scheduleText'
import type {ScheduleBoard,BulkResult} from '../bulkScheduleTypes'
import type {Schedule} from '../types'
import ScheduleDialog from '../components/ScheduleDialog.vue'
import WeekCopyDialog from '../components/WeekCopyDialog.vue'
import BulkScheduleDialog from '../components/BulkScheduleDialog.vue'
import {WorkforceApiError} from '../api'
import {startScheduleSync} from '../scheduleSync'
import StaffingEmployeesDialog from '../components/StaffingEmployeesDialog.vue'
import ScheduleStaffing from '../components/ScheduleStaffing.vue'
import WeekScheduleTable from '../components/WeekScheduleTable.vue'
import {usePhasePage} from '../phasePage'
import {addDays,storeToday,weekMonday} from '../time'
import {restaurantText} from '../restaurantShifts'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,home,store,current,t,p,base,loading,busy,failure,notice,load,format,english}=usePhasePage(),{s}=useScheduleText()
const week=ref(weekMonday(storeToday())),month=ref(storeToday().slice(0,7)),target=ref(addDays(week.value,7))
const view=ref<'weekly'|'monthly'|'list'>('weekly'),period=ref<'week'|'month'>('week'),displayedPeriod=ref<'week'|'month'>('week')
const displayedWeek=ref(week.value),displayedFrom=ref(week.value),displayedTo=ref(addDays(week.value,6)),position=ref(''),search=ref(''),offset=ref(0)
const data=ref<ScheduleBoard>({items:[],employees:[],timezone:'Asia/Singapore',total:0,limit:50,offset:0,weekScheduleCount:0,positions:[]})
const copyDraft=ref<{sourceWeek:string;targetWeek:string}|null>(null),editor=ref<{id?:string;employeeId?:string;workDate?:string}|null>(null),bulkOpen=ref(false),panel=ref<HTMLElement|null>(null)
interface BoardQuery {from:string;to:string;period:'week'|'month';position:string;search:string;offset:number}
let initial=true,lastQuery:BoardQuery|null=null,boardRevision=0,syncing=false,refreshAgain=false,stopSync:(()=>void)|undefined
const syncFailure=ref('')
const staffingSelection=ref<{shiftId:string;shiftName:string;from:string;to:string;positionId:string;search:string}|null>(null)
function monthEnd(first:string){const d=new Date(first+'T12:00:00Z');d.setUTCMonth(d.getUTCMonth()+1,0);return Number.isFinite(d.getTime())?d.toISOString().slice(0,10):''}
function create(employeeId?:string,workDate?:string){if(!busy.value&&!copyDraft.value)editor.value={employeeId,workDate}}
function edit(schedule:Schedule){if(!busy.value&&!copyDraft.value)editor.value={id:schedule.id}}
function openStaffing(shiftId:string,shiftName:string,date?:string){
 if(loading.value||busy.value||!lastQuery||staffingSelection.value)return
 staffingSelection.value={shiftId,shiftName,from:date||lastQuery.from,to:date||lastQuery.to,positionId:lastQuery.position,search:lastQuery.search}
}
function openBulk(){if(!busy.value&&!loading.value&&!copyDraft.value)bulkOpen.value=true}
function bulkSaved(result:BulkResult,storeId:string){
 const scroll=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap')?.scrollLeft||0
 bulkOpen.value=false;notice.value=`${home.value?.stores.find(store=>store.id===storeId)?.name||''} · ${s('done')} ${result.created} ${s('entries')}，${s('skip')} ${result.existing+result.conflicts} ${s('entries')}`
 void Promise.resolve(refresh(lastQuery||undefined)).then(()=>nextTick(()=>{const roster=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap');if(roster)roster.scrollLeft=scroll}))
}
function staffingChanged(){
 const scroll=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap')?.scrollLeft||0
 void Promise.resolve(refresh(lastQuery||undefined)).then(()=>nextTick(()=>{const roster=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap');if(roster)roster.scrollLeft=scroll}))
}
function saved(schedule:Schedule){
 const inRange=(row:Schedule)=>row.workDate>=displayedFrom.value&&row.workDate<=displayedTo.value
 const old=data.value.items.find(row=>row.id===schedule.id),delta=Number(inRange(schedule)&&schedule.status==='scheduled')-Number(!!old&&inRange(old)&&old.status==='scheduled')
 if(displayedPeriod.value==='week')data.value.weekScheduleCount+=delta
 if(data.value.periodScheduleCount!==undefined)data.value.periodScheduleCount+=delta
 const sameCell=(row:Schedule)=>row.employeeId===schedule.employeeId&&row.workDate===schedule.workDate
 const rows=data.value.items.filter(row=>row.id!==schedule.id&&!(sameCell(row)&&row.status==='cancelled'))
 if(inRange(schedule)&&data.value.employees.some(employee=>employee.id===schedule.employeeId)&&!(schedule.status==='cancelled'&&rows.some(row=>sameCell(row)&&row.status==='scheduled')))rows.push(schedule)
 data.value.items=rows.sort((a,b)=>a.workDate.localeCompare(b.workDate)||a.startAt.localeCompare(b.startAt)||a.id.localeCompare(b.id))
 editor.value=null;++boardRevision;notice.value=p('applied');if(data.value.staffing)void quietSync(true);void nextTick(()=>panel.value?.querySelector<HTMLButtonElement>(`[data-schedule-id="${schedule.id}"]`)?.focus())
}
function deleted(id:string){
 const row=data.value.items.find(row=>row.id===id)
 if(row?.status==='scheduled'){if(displayedPeriod.value==='week')data.value.weekScheduleCount--;if(data.value.periodScheduleCount!==undefined)data.value.periodScheduleCount--}
 if(row)data.value.items=data.value.items.filter(item=>item.id!==id&&!(item.employeeId===row.employeeId&&item.workDate===row.workDate&&item.status==='cancelled'))
 editor.value=null;++boardRevision;notice.value=p('applied');if(data.value.staffing)void quietSync(true)
}
function refresh(query?:BoardQuery){
 const from=period.value==='month'?month.value+'-01':week.value
 const q=query||{from,to:period.value==='month'?monthEnd(from):addDays(from,6),period:period.value,position:position.value,search:search.value,offset:offset.value}
 if(!q.from||!q.to||addDays(q.from,0)!==q.from||q.period==='week'&&weekMonday(q.from)!==q.from){failure.value=p('invalid');return}
 ++boardRevision;syncFailure.value=''
 const path=`/schedule-board?from=${q.from}${q.period==='month'?'&to='+q.to:''}&limit=50&offset=${q.offset}&search=${encodeURIComponent(q.search)}${q.position?'&positionId='+encodeURIComponent(q.position):''}`
 return load(()=>api().get<ScheduleBoard>(path),result=>{
  data.value=result;lastQuery={...q};displayedFrom.value=q.from;displayedTo.value=q.to;displayedPeriod.value=q.period
  if(q.period==='week'){if(displayedWeek.value!==q.from)target.value=addDays(q.from,7);displayedWeek.value=q.from;month.value=q.from.slice(0,7)}
  if(initial){initial=false;const actual=weekMonday(storeToday(result.timezone));if(q.period==='week'&&actual!==week.value){week.value=actual;target.value=addDays(actual,7);void refresh()}}
 })
}
function canSync(){return current()&&!!home.value?.employeeManage&&!!lastQuery&&!loading.value&&!busy.value&&!editor.value&&!bulkOpen.value&&!copyDraft.value&&!staffingSelection.value}
async function quietSync(force=false){
 if(syncing){if(force)refreshAgain=true;return}if(!canSync())return
 const query={...lastQuery!},owner=store.value,revision=boardRevision;syncing=true
 const applicable=()=>canSync()&&store.value===owner&&boardRevision===revision
 try{
  const path=`/schedule-board?from=${query.from}${query.period==='month'?'&to='+query.to:''}&limit=50&offset=${query.offset}&search=${encodeURIComponent(query.search)}${query.position?'&positionId='+encodeURIComponent(query.position):''}`
  const result=await api().get<ScheduleBoard>(path,AbortSignal.timeout(12000))
  if(applicable()){const scroll=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap')?.scrollLeft||0;data.value=result;syncFailure.value='';if(failure.value===p('noAccess'))failure.value='';void nextTick(()=>{const roster=panel.value?.querySelector<HTMLElement>('.wf-roster-wrap');if(roster)roster.scrollLeft=scroll})}
 }catch(error){if(applicable()){if(error instanceof WorkforceApiError&&(error.status===401||error.status===403)){data.value={...data.value,items:[],employees:[],positions:[],total:0,weekScheduleCount:0,periodScheduleCount:0,staffing:{shifts:[],days:[]}};failure.value=p('noAccess');syncFailure.value=''}else syncFailure.value=s('syncFailed')}}
 finally{syncing=false;if(refreshAgain){refreshAgain=false;void quietSync()}}
}
function switchView(next:'weekly'|'monthly'|'list'){view.value=next;if(next==='list')return;const nextPeriod=next==='monthly'?'month':'week';if(period.value!==nextPeriod){period.value=nextPeriod;offset.value=0;void refresh()}}
function move(n:number){offset.value=0;if(period.value==='month'){const d=new Date(month.value+'-01T12:00:00Z');if(!Number.isFinite(d.getTime())){failure.value=p('invalid');return}d.setUTCMonth(d.getUTCMonth()+n/7);month.value=d.toISOString().slice(0,7)}else week.value=addDays(week.value,n);void refresh()}
function page(delta:number){offset.value=Math.max(0,offset.value+delta);void refresh()}
function filter(){offset.value=0;void refresh()}
function copy(){
 if(displayedPeriod.value!=='week'||!target.value||weekMonday(target.value)!==target.value||target.value===displayedWeek.value){failure.value=p('invalid');return}
 failure.value='';copyDraft.value={sourceWeek:displayedWeek.value,targetWeek:target.value}
}
function copied(result:BulkResult,targetWeek:string){copyDraft.value=null;week.value=targetWeek;view.value='weekly';period.value='week';offset.value=0;notice.value=`${p('copied')} · ${s('done')} ${result.created} ${s('entries')}，${s('skip')} ${result.existing+result.conflicts} ${s('entries')}`;void refresh()}
onMounted(()=>{if(home.value?.employeeManage){void refresh();if(typeof window!=='undefined')stopSync=startScheduleSync(canSync,()=>quietSync())}})
onBeforeUnmount(()=>{++boardRevision;stopSync?.()})
</script>
<template><section v-if="home?.employeeManage" ref="panel" class="wf-panel wf-schedule-page">
 <header class="wf-toolbar wf-schedule-header"><h2>{{displayedPeriod==='month'?s('monthlySchedule'):p('schedule')}}</h2><small>{{p('timezone')}}: {{data.timezone}} · {{displayedFrom}} – {{displayedTo}}</small><RouterLink class="wf-button" :to="base+'/shifts'">{{p('shifts')}}</RouterLink></header><p class="wf-hint">{{restaurantText(english()).business}}</p>
 <div class="wf-schedule-controls">
  <form class="wf-schedule-query" @submit.prevent="filter"><label>{{period==='month'?s('month'):p('week')}}<input v-if="period==='month'" v-model="month" type="month" required :disabled="!!copyDraft||busy"><input v-else v-model="week" type="date" required :disabled="!!copyDraft||busy"></label><div class="wf-schedule-buttons"><button type="button" :disabled="loading||busy||!!copyDraft" @click="move(-7)">{{t('previous')}}</button><button type="button" :disabled="loading||busy||!!copyDraft" @click="move(7)">{{t('next')}}</button><button :disabled="loading||busy||!!copyDraft">{{p('filter')}}</button></div></form>
  <button type="button" class="primary" :disabled="loading||busy||!!copyDraft" @click="openBulk">{{s('bulk')}}</button>
  <button type="button" :disabled="busy||!!copyDraft" @click="create()">{{p('newSchedule')}}</button>
  <form v-if="period==='week'" class="wf-schedule-copy" @submit.prevent="copy"><label>{{p('targetWeek')}}<input v-model="target" type="date" required :disabled="busy||!!copyDraft"></label><button :disabled="busy||loading||data.weekScheduleCount===0">{{busy?t('saving'):target===addDays(displayedWeek,7)?s('copyToNextWeek'):p('copyWeek')}}</button></form>
 </div>
 <p v-if="period==='week'" class="wf-hint wf-schedule-copy-hint">{{s('copySource')}}: {{displayedWeek}} → {{target}} · {{p('copyHint')}}</p>
 <p v-if="period==='month'" class="wf-hint wf-schedule-copy-hint">{{s('copyWeekView')}}</p>
 <PhaseStatus :failure="failure" :loading="loading" :notice="notice"/>
 <p v-if="syncFailure" role="status" class="wf-hint">{{syncFailure}}</p>
 <form class="wf-filters" @submit.prevent="filter"><label>{{s('position')}}<select v-model="position" :disabled="loading||busy||!!copyDraft"><option value="">{{s('allPositions')}}</option><option v-for="item in data.positions" :key="item.id" :value="item.id">{{item.name}}</option></select></label><label>{{s('search')}}<input v-model="search" type="search" maxlength="100" :disabled="loading||busy||!!copyDraft"></label><button :disabled="loading||busy||!!copyDraft">{{p('filter')}}</button><div class="wf-view-switch" role="group"><button type="button" :aria-pressed="view==='weekly'" :disabled="loading||busy||!!copyDraft" @click="switchView('weekly')">{{s('weekly')}}</button><button type="button" :aria-pressed="view==='monthly'" :disabled="loading||busy||!!copyDraft" @click="switchView('monthly')">{{s('monthly')}}</button><button type="button" :aria-pressed="view==='list'" :disabled="loading||busy||!!copyDraft" @click="switchView('list')">{{s('list')}}</button></div></form>
 <ScheduleStaffing :value="data.staffing" @staffing="openStaffing"/>
 <p class="wf-hint wf-sync-hint">{{s('syncHint')}}</p>
 <WeekScheduleTable v-if="!loading&&view!=='list'" :data="data" :week="displayedFrom" :to="displayedTo" :base="base" @create="create" @edit="edit" @staffing="openStaffing"/>
 <div v-if="!loading&&view==='list'" class="wf-table-wrap"><table><thead><tr><th>{{p('workDate')}}</th><th>{{p('employee')}}</th><th>{{p('shift')}}</th><th>{{p('plan')}}</th><th>{{p('status')}}</th><th></th></tr></thead><tbody><tr v-for="row in data.items" :key="row.id"><td>{{row.workDate}}<RestSwapLabels :items="data.restSwaps" :date="row.workDate" :employee-id="row.employeeId"/><CalendarDateLabels :events="data.calendar" :date="row.workDate"/></td><td>{{row.employeeName}} <small>{{row.employeeNo}}</small></td><td>{{row.shift.name}}</td><td>{{format(row.startAt,row.timezone)}} – {{format(row.endAt,row.timezone)}}</td><td>{{p(row.status)}}</td><td><button type="button" :data-schedule-id="row.id" @click="edit(row)">{{t('manage')}}</button></td></tr><tr v-if="!data.items.length"><td colspan="6" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div>
 <footer class="wf-pagination"><span>{{data.total?offset+1:0}}–{{Math.min(offset+50,data.total)}} / {{data.total}} {{s('count')}}</span><button :disabled="loading||busy||!!copyDraft||offset===0" @click="page(-50)">{{t('previous')}}</button><button :disabled="loading||busy||!!copyDraft||offset+50>=data.total" @click="page(50)">{{t('next')}}</button></footer>
 <StaffingEmployeesDialog v-if="staffingSelection" v-bind="staffingSelection" @close="staffingSelection=null" @changed="staffingChanged"/>
 <WeekCopyDialog v-if="copyDraft" v-bind="copyDraft" @close="copyDraft=null" @saved="copied"/>
 <ScheduleDialog v-if="editor" v-bind="editor" @close="editor=null" @saved="saved" @deleted="deleted"/>
 <BulkScheduleDialog v-if="bulkOpen" :period-from="displayedFrom" :period-to="displayedTo" :initial-position="lastQuery?.position||''" :initial-search="lastQuery?.search||''" @close="bulkOpen=false" @saved="bulkSaved"/>
</section><p v-else role="alert">{{t('noAccess')}}</p></template>


