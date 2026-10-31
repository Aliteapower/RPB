<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {useScheduleText} from '../scheduleText'
import type {StaffingEmployee,StaffingEmployeePage} from '../bulkScheduleTypes'
import {useStaffingSelection} from '../staffingSelection'
import {WorkforceApiError} from '../api'
import type {AdjustmentResult} from '../bulkAdjustmentTypes'
import BulkAdjustmentDialog from './BulkAdjustmentDialog.vue'
import ScheduleDialog from './ScheduleDialog.vue'
import PhaseStatus from './PhaseStatus.vue'
const props=defineProps<{shiftId:string;shiftName:string;from:string;to:string;positionId?:string;search?:string}>(),emit=defineEmits<{close:[];changed:[]}>()
const {api,home,store,t,p,load,loading,failure,notice,format}=usePhasePage(),{s}=useScheduleText()
const dialog=ref<HTMLDialogElement|null>(null),offset=ref(0),data=ref<StaffingEmployeePage>({items:[],total:0,people:0,limit:50,offset:0})
const selection=useStaffingSelection(props.from,props.to),batchOpen=ref(false),selectedDate=selection.date,selected=selection.selected
const blocked=computed(()=>!!editorId.value||batchOpen.value)
const editorId=ref(''),chooser=ref<StaffingEmployee|null>(null),closeButton=ref<HTMLButtonElement|null>(null),choicePanel=ref<HTMLDivElement|null>(null)
function setChoicePanel(element:unknown){choicePanel.value=element as HTMLDivElement|null}
function close(){if(!blocked.value)emit('close')}
function editEmployee(row:StaffingEmployee){if(blocked.value)return;if(row.schedules?.length===1)editorId.value=row.schedules[0].id;else if(row.schedules?.length){chooser.value=row;void nextTick(()=>choicePanel.value?.scrollIntoView?.({block:'nearest'}))}}
function choose(id:string){chooser.value=null;editorId.value=id}
function closeEditor(){editorId.value='';void nextTick(()=>closeButton.value?.focus())}
function changed(){closeEditor();chooser.value=null;selection.clear();notice.value=p('applied');refresh();emit('changed')}
function refresh(){
 data.value={items:[],total:0,people:0,limit:50,offset:offset.value}
 const query=new URLSearchParams({from:props.from,to:props.to,shiftId:props.shiftId,search:props.search||'',limit:'50',offset:String(offset.value)})
 if(props.positionId)query.set('positionId',props.positionId)
 void load(()=>api().get<StaffingEmployeePage>('/schedule-staffing/employees?'+query.toString()),result=>{if(offset.value>0&&offset.value>=result.total){offset.value=Math.max(0,Math.floor((result.total-1)/50)*50);refresh();return}data.value=result;selection.acceptPage(result.items)})
}
function reload(){selection.clear();chooser.value=null;refresh()}
function selectAll(){
 if(!selectedDate.value||blocked.value||loading.value)return
 const date=selectedDate.value
 void load(async()=>{const rows:StaffingEmployee[]=[];for(let at=0;;at+=100){const query=new URLSearchParams({from:date,to:date,shiftId:props.shiftId,search:props.search||'',limit:'100',offset:String(at)});if(props.positionId)query.set('positionId',props.positionId);const page=await api().get<StaffingEmployeePage>('/schedule-staffing/employees?'+query.toString());if(page.total>500)throw new WorkforceApiError(400,'WORKFORCE_ADJUST_TOO_MANY');rows.push(...page.items);if(at+page.limit>=page.total)break}return rows},rows=>{if(date===selectedDate.value)selection.replace(rows)})
}
function adjusted(result:AdjustmentResult){batchOpen.value=false;changed();notice.value=s('adjustDone')+' '+result.updated+' '+s('entries')+'，'+s('skip')+' '+(result.unchanged+result.conflicts)+' '+s('entries')}
function page(delta:number){offset.value=Math.max(0,offset.value+delta);refresh()}
onMounted(()=>{dialog.value?.showModal?.();if(home.value?.employeeManage)refresh()})
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-staffing-dialog" aria-modal="true" aria-labelledby="wf-staffing-employees-title" @cancel.prevent="close">
 <header class="wf-toolbar"><h2 id="wf-staffing-employees-title">{{s('staffingEmployees')}}</h2><button ref="closeButton" type="button" class="wf-dialog-close" :aria-label="s('closeDialog')" :disabled="blocked" @click="close">×</button></header>
 <p>{{home?.stores.find(item=>item.id===store)?.name}} · <strong>{{shiftName}}</strong> · {{from}}{{from===to?'':' – '+to}}</p>
 <p class="wf-hint">{{s('staffingListHint')}}</p>
 <PhaseStatus :loading="loading" :failure="failure" :notice="notice"/>
 <p v-if="!home?.employeeManage" role="alert">{{p('noAccess')}}</p>
 <template v-else-if="!loading&&!failure">
  <div class="wf-filters"><label>{{s('adjustDate')}}<input v-model="selectedDate" type="date" :min="from" :max="to" :disabled="blocked" @change="selection.clear()"></label><button type="button" :disabled="blocked" @click="selection.selectPage(data.items)">{{s('selectPage')}}</button><button type="button" :disabled="blocked||!selectedDate" @click="selectAll">{{s('adjustSelectAll')}}</button><button type="button" :disabled="blocked||!selected.length" @click="selection.clear">{{s('clearSelection')}}</button><button type="button" :disabled="blocked" @click="reload">{{p('reload')}}</button><button type="button" class="primary" :disabled="blocked||!selected.length||!selectedDate" @click="batchOpen=true">{{s('bulkAdjust')}}</button></div>
  <p class="wf-hint">{{s('selected')}} {{selected.length}} {{s('peopleUnit')}} · {{s('adjustSelectHint')}}</p>
  <p class="wf-hint">{{data.people}} {{s('peopleUnit')}} · {{data.total}} {{s('personDaysUnit')}}</p>
  <div class="wf-table-wrap"><table><thead><tr><th>{{s('selectEmployee')}}</th><th>{{p('workDate')}}</th><th>{{p('shift')}}</th><th>{{t('employeeNo')}}</th><th>{{t('name')}}</th></tr></thead><tbody><tr v-for="row in data.items" :key="row.employeeId+':'+row.date"><td><input type="checkbox" :checked="selection.checked(row)" :disabled="blocked||!selection.eligible(row)" :aria-label="s('selectEmployee')+' · '+row.name+' · '+row.date" @change="selection.toggle(row,($event.target as HTMLInputElement).checked)"><small v-if="(row.schedules?.length||0)>1">{{s('adjustMultiple')}}</small></td><td>{{row.date}}</td><td>{{row.shiftName}}</td><td>{{row.employeeNo}}</td><td><button type="button" class="wf-count-button" :disabled="!row.schedules?.length||blocked" :aria-label="s('adjustStaffingEmployee')+' · '+row.name+' · '+row.date" @click="editEmployee(row)">{{row.name}}</button><div v-if="chooser?.employeeId===row.employeeId&&chooser.date===row.date" :ref="setChoicePanel" class="wf-staffing-slot-picker"><p>{{s('chooseStaffingSlot')}}</p><div class="wf-staffing-counts"><button type="button" v-for="slot in row.schedules" :key="slot.id" :data-staffing-schedule="slot.id" @click="choose(slot.id)">{{format(slot.startAt,slot.timezone)}} – {{format(slot.endAt,slot.timezone)}}</button></div></div></td></tr><tr v-if="!data.items.length"><td colspan="5" class="wf-empty">{{s('noStaffing')}}</td></tr></tbody></table></div>

  <footer class="wf-pagination"><span>{{data.total?offset+1:0}}–{{Math.min(offset+data.limit,data.total)}} / {{data.total}}</span><button type="button" :disabled="blocked||offset===0" @click="page(-50)">{{t('previous')}}</button><button type="button" :disabled="blocked||offset+data.limit>=data.total" @click="page(50)">{{t('next')}}</button></footer>
 </template>
 <button v-if="failure&&home?.employeeManage" type="button" :disabled="loading" @click="refresh">{{p('reload')}}</button>
 <footer class="wf-form-actions"><button type="button" :disabled="blocked" @click="close">{{s('closeDialog')}}</button></footer>
 <BulkAdjustmentDialog v-if="batchOpen" :date="selectedDate" :source-shift-id="shiftId" :targets="selected" @close="batchOpen=false" @saved="adjusted"/>
 <ScheduleDialog v-if="editorId" :id="editorId" @close="closeEditor" @saved="changed" @deleted="changed"/>
</dialog></template>
