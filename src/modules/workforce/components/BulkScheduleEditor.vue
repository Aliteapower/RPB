<script setup lang="ts">
import {restaurantShiftRange} from '../restaurantShifts'
import {computed,onMounted,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {useScheduleText} from '../scheduleText'
import {WorkforceApiError,workforceApi} from '../api'
import {addDays,storeToday} from '../time'
import type {BulkOptions,BulkRequest,BulkPreview,BulkResult} from '../bulkScheduleTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const props=defineProps<{inline?:boolean;periodFrom?:string;periodTo?:string;initialPosition?:string;initialSearch?:string}>()
const emit=defineEmits<{saved:[result:BulkResult,storeId:string];blocked:[value:boolean];close:[];storeChanged:[storeId:string]}>()
const {home,store:contextStore,current,t,p,base,loading,english,busy,failure,notice,load}=usePhasePage(),{s,days}=useScheduleText()
const store=ref(contextStore.value),api=()=>workforceApi(store.value)
const options=ref<BulkOptions>({items:[],total:0,limit:50,offset:0,positions:[],shifts:[],timezone:'Asia/Singapore'}),position=ref(props.initialPosition||''),search=ref(props.initialSearch||''),offset=ref(0),selected=ref<string[]>([]),shift=ref(''),from=ref(storeToday()),to=ref(storeToday()),weekdays=ref([1,2,3,4,5,6,7]),preview=ref<BulkPreview|null>(null),previewOffset=ref(0)
const command=ref<{id:string;request:BulkRequest;fingerprint:string}|null>(null),locked=computed(()=>!!command.value||busy.value);let initial=true,resetting=false
watch(locked,value=>emit('blocked',value),{immediate:true,flush:'sync'})
function close(){if(!locked.value)emit('close')}
function input():BulkRequest{return {employeeIds:[...selected.value].sort(),shiftId:shift.value,from:from.value,to:to.value,weekdays:[...weekdays.value].sort()}}
function query(at:number){return `/schedule-bulk/options?limit=50&offset=${at}&positionId=${encodeURIComponent(position.value)}&search=${encodeURIComponent(search.value)}`.replace('positionId=&','')}
function refresh(){void load(()=>api().get<BulkOptions>(query(offset.value)),r=>{options.value=r;if(initial){initial=false;const today=storeToday(r.timezone),last=addDays(today,90);from.value=props.periodFrom&&props.periodFrom>=today?(props.periodFrom<=last?props.periodFrom:last):today;to.value=props.periodTo&&props.periodTo>=from.value?(props.periodTo<=last?props.periodTo:last):from.value}})}
function filter(){selected.value=[];offset.value=0;refresh()}
watch([position,search],()=>{if(!resetting)filter()},{flush:'sync'})
watch([selected,shift,from,to,weekdays],()=>{preview.value=null;previewOffset.value=0;notice.value=''}, {deep:true})
function page(delta:number){offset.value=Math.max(0,offset.value+delta);refresh()}
function selectPage(){selected.value=[...new Set([...selected.value,...options.value.items.map(e=>e.id)])].slice(0,500)}
function selectAll(){if(options.value.total>500){failure.value=s('tooMany');return}void load(async()=>{const ids:string[]=[];for(let at=0;at<options.value.total;at+=50){const page=await api().get<BulkOptions>(query(at));if(page.total>500)throw new Error('capacity');ids.push(...page.items.map(e=>e.id))}return ids},ids=>{selected.value=[...new Set(ids)]})}
function check(){preview.value=null;void load(()=>api().put<BulkPreview>('/schedule-bulk/preview',input()),r=>{preview.value=r;previewOffset.value=0})}
async function submit(){
 if(busy.value||!preview.value)return
 if(!command.value)command.value={id:crypto.randomUUID(),request:input(),fingerprint:preview.value.fingerprint}
 const c=command.value,owner=store.value;busy.value=true;failure.value='';notice.value=''
 try{const result=await api().put<BulkResult>('/schedule-bulk/'+c.id,{request:c.request,fingerprint:c.fingerprint});if(current()&&owner===store.value){command.value=null;preview.value=null;notice.value=`${s('done')} ${result.created} ${s('entries')}，${s('skip')} ${result.existing+result.conflicts} ${s('entries')}`;emit('saved',result,owner)}}
 catch(e){if(current()&&owner===store.value){if(e instanceof WorkforceApiError&&e.status>=400&&e.status<500){command.value=null;preview.value=null;failure.value=e.code==='WORKFORCE_BULK_CHANGED'?s('changed'):e.status===403||e.status===401?p('noAccess'):p('invalid')}else failure.value=s('retry')}}
 finally{if(current())busy.value=false}
}
function changeStore(event:Event){
 const id=(event.target as HTMLSelectElement).value;if(id===store.value||locked.value||!home.value?.stores.some(item=>item.id===id))return
 if(!props.inline){emit('storeChanged',id);return}
 resetting=true;store.value=id;options.value={items:[],total:0,limit:50,offset:0,positions:[],shifts:[],timezone:'Asia/Singapore'};selected.value=[];shift.value='';position.value='';search.value='';offset.value=0;preview.value=null;initial=true;resetting=false;refresh()
}
onMounted(()=>{if(home.value?.employeeManage)refresh()})
</script>
<template><section v-if="home?.employeeManage" class="wf-bulk-page" :class="{'wf-panel':!inline}">
 <header class="wf-toolbar"><h2 id="wf-bulk-schedule-title">{{s('bulk')}}</h2><button v-if="inline" type="button" class="wf-dialog-close" :aria-label="s('closeDialog')" :disabled="locked" @click="close">×</button><RouterLink v-else-if="!command" :to="base+'/schedule'">{{s('back')}}</RouterLink></header>
 <p class="wf-hint">{{s('noOverwrite')}} {{s('limits')}}</p>
 <PhaseStatus :loading="loading" :failure="failure" :notice="notice"/>
 <fieldset :disabled="locked||loading">
  <div class="wf-filters"><label>{{s('store')}}<select :value="store" @change="changeStore"><option v-for="item in home.stores" :key="item.id" :value="item.id">{{item.name}}</option></select></label><label>{{s('position')}}<select v-model="position"><option value="">{{s('allPositions')}}</option><option v-for="item in options.positions" :key="item.id" :value="item.id">{{item.name}}{{item.enabled?'':' · '+s('disabled')}}</option></select></label><label>{{s('search')}}<input v-model="search" type="search" maxlength="100"></label></div>
  <div class="wf-toolbar wf-bulk-selection"><strong>{{s('select')}} · {{s('selected')}} {{selected.length}} / {{options.total}}</strong><div class="wf-inline"><button type="button" @click="selectPage">{{s('selectPage')}}</button><button type="button" @click="selectAll">{{s('selectAll')}}</button><button type="button" @click="selected=[]">{{s('clear')}}</button></div></div>
  <p class="wf-hint">{{s('filterClears')}}</p>
  <div class="wf-bulk-employees"><label v-for="employee in options.items" :key="employee.id" class="wf-check"><input v-model="selected" type="checkbox" :value="employee.id" :disabled="selected.length>=500&&!selected.includes(employee.id)"><span>{{employee.name}} <small>{{employee.employeeNo}}</small></span></label><p v-if="!options.items.length">{{t('empty')}}</p></div>
  <footer class="wf-pagination"><span>{{options.total?offset+1:0}}–{{Math.min(offset+50,options.total)}} / {{options.total}}</span><button type="button" :disabled="offset===0" @click="page(-50)">{{t('previous')}}</button><button type="button" :disabled="offset+50>=options.total" @click="page(50)">{{t('next')}}</button></footer>
  <div class="wf-filters wf-section"><label>{{p('from')}}<input v-model="from" type="date" :min="storeToday(options.timezone)"></label><label>{{p('to')}}<input v-model="to" type="date" :min="from"></label><label>{{p('shift')}}<select v-model="shift"><option value="">—</option><option v-for="item in options.shifts.filter(v=>v.enabled)" :key="item.id" :value="item.id">{{item.name}} · {{restaurantShiftRange(item,english())}}</option></select></label></div>
  <fieldset class="wf-weekdays"><legend>{{s('weekdays')}}</legend><label v-for="(name,i) in days()" :key="i" class="wf-check"><input v-model="weekdays" type="checkbox" :value="i+1">{{name}}</label></fieldset>
  <button type="button" :disabled="!selected.length||!shift||!from||!to||!weekdays.length" @click="check">{{s('preview')}}</button>
 </fieldset>
 <section v-if="preview" class="wf-section"><h3>{{s('preview')}} · {{preview.total}} {{s('entries')}}</h3><p class="wf-hint">{{s('previewHint')}}</p><div class="wf-bulk-summary"><span>{{s('ready')}} <strong>{{preview.ready}}</strong></span><span>{{s('existing')}} <strong>{{preview.existing}}</strong></span><span>{{s('conflict')}} <strong>{{preview.conflicts}}</strong></span></div>
  <div class="wf-table-wrap"><table><thead><tr><th>{{s('count')}}</th><th>{{p('workDate')}}</th><th>{{p('status')}}</th><th>{{s('reason')}}</th></tr></thead><tbody><tr v-for="row in preview.items.slice(previewOffset,previewOffset+50)" :key="row.employeeId+row.date"><td>{{row.employeeName}} <small>{{row.employeeNo}}</small></td><td>{{row.date}}</td><td>{{row.status==='ready'?s('ready'):row.status==='existing'?s('existing'):s('conflict')}}</td><td>{{row.reason==='EXISTING_SCHEDULE'?s('EXISTING_SCHEDULE'):row.reason==='TIME_OVERLAP'?s('TIME_OVERLAP'):row.reason==='NOT_ASSIGNED'?s('NOT_ASSIGNED'):'—'}}</td></tr></tbody></table></div>
  <footer class="wf-pagination"><button type="button" :disabled="previewOffset===0" @click="previewOffset-=50">{{t('previous')}}</button><button type="button" :disabled="previewOffset+50>=preview.items.length" @click="previewOffset+=50">{{t('next')}}</button></footer>
  <p v-if="command" class="wf-error" role="status">{{s('retry')}}</p><div class="wf-form-actions"><button class="primary" :disabled="busy||preview.ready===0" @click="submit">{{busy?t('saving'):s('create')+' '+preview.ready+' '+s('entries')+'，'+s('skip')+' '+(preview.existing+preview.conflicts)+' '+s('entries')}}</button></div>
 </section>
</section><p v-else role="alert">{{t('noAccess')}}</p></template>
