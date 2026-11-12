<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {workforceApi,WorkforceApiError} from '../api'
import {hours,localInput,localToInstant} from '../time'
import {restaurantShiftRange} from '../restaurantShifts'
import type {Schedule,Shift} from '../types'
import DateInput from '../../../components/common/DateInput.vue'
import PhaseStatus from './PhaseStatus.vue'

const props=defineProps<{source:Schedule}>()
const emit=defineEmits<{close:[];saved:[source:Schedule];blocked:[value:boolean]}>()
const {api,store,home,english,t,p,busy,write,load,loading,failure,format}=usePhasePage()
const targetStore=ref(''),targetShift=ref(''),split=ref(false),splitAt=ref('')
const shifts=ref<Shift[]>([])
const stores=computed(()=>home.value?.stores.filter(item=>item.id!==store.value&&item.companyId===props.source.companyId)||[])
interface Transfer {sourceId:string;expectedVersion:number;targetStoreId:string;targetShiftId:string;splitAt:string|null;targetShiftVersion:number}
interface Result {source:Schedule;target:Schedule;sourcePlannedSeconds:number|null;targetPlannedSeconds:number|null}
const preview=ref<{body:Transfer;result:Result}|null>(null)
const pending=ref<{command:string;body:Transfer}|null>(null)
const blocked=computed(()=>busy.value||!!pending.value)
watch(blocked,value=>emit('blocked',value),{immediate:true,flush:'sync'})
watch([targetStore,targetShift,split,splitAt],()=>{preview.value=null})
watch(targetStore,id=>{
 targetShift.value='';shifts.value=[]
 if(id)void load(()=>workforceApi(id).get<Shift[]>('/shifts'),value=>{if(id===targetStore.value)shifts.value=value.filter(shift=>shift.enabled)})
})
const label=(zh:string,en:string)=>english()?en:zh
function showPreview(){
 if(busy.value||pending.value||loading.value)return
 let at:string|null=null
 try{if(split.value)at=localToInstant(splitAt.value,props.source.timezone)}
 catch{failure.value=p('invalid');return}
 const shift=shifts.value.find(item=>item.id===targetShift.value)
 if(!shift)return
 const body={sourceId:props.source.id,expectedVersion:props.source.version,targetStoreId:targetStore.value,
  targetShiftId:shift.id,splitAt:at,targetShiftVersion:shift.version}
 preview.value=null
 void load(()=>api().put<Result>('/schedule-transfer/preview',body),result=>{preview.value={body,result}})
}
function save(){
 if(busy.value||loading.value)return
 if(!pending.value&&preview.value)pending.value={command:crypto.randomUUID(),body:preview.value.body}
 const request=pending.value
 if(!request)return
 let result:Result
 void write(async()=>{
  try{result=await api().put<Result>('/schedule-transfer/'+request.command,request.body)}
  catch(error){
   if(error instanceof WorkforceApiError&&error.status>=400&&error.status<500){pending.value=null;preview.value=null}
   throw error
  }
 },()=>{pending.value=null;emit('saved',result.source)})
}
</script>

<template>
 <form @submit.prevent="showPreview">
  <p>{{source.employeeName}} · {{source.workDate}} · {{source.shift.name}}</p>
  <PhaseStatus :loading="loading" :failure="failure"/>
  <fieldset :disabled="blocked||loading">
   <label>{{label('目标门店','Destination store')}}
    <select v-model="targetStore" required><option value="" disabled>{{t('select')}}</option>
     <option v-for="item in stores" :key="item.id" :value="item.id">{{item.name}}</option>
    </select>
   </label>
   <label>{{label('目标班次','Destination shift')}}
    <select v-model="targetShift" required><option value="" disabled>{{t('select')}}</option>
     <option v-for="shift in shifts" :key="shift.id" :value="shift.id">{{shift.name}} · {{restaurantShiftRange(shift,english())}}</option>
    </select>
   </label>
   <label>{{label('调店方式','Transfer type')}}
    <select v-model="split"><option :value="false">{{label('整班调店','Whole shift')}}</option><option :value="true">{{label('只调剩余时段','Remaining hours')}}</option></select>
   </label>
   <label v-if="split">{{label('原店班次结束时间','End of original shift')}} · {{source.timezone}}
    <DateInput v-model="splitAt" type="datetime-local" :min="localInput(source.startAt,source.timezone)" :max="localInput(source.endAt,source.timezone)" required />
   </label>
   <p v-if="split" class="wf-hint">{{label('已签到的员工在原店签退后，再到目标店签到。','If already clocked in, clock out at the original store before clocking in at the destination.')}}</p>
  </fieldset>
  <template v-if="preview">
   <p v-if="preview.result.source.status==='cancelled'">{{label('原店班次将取消','The original shift will be cancelled')}}</p>
   <p v-else>{{label('原店','Original')}} · {{format(preview.result.source.startAt,preview.result.source.timezone)}}–{{format(preview.result.source.endAt,preview.result.source.timezone)}} · {{hours(preview.result.sourcePlannedSeconds)}}</p>
   <p>{{stores.find(item=>item.id===preview?.body.targetStoreId)?.name}} · {{format(preview.result.target.startAt,preview.result.target.timezone)}}–{{format(preview.result.target.endAt,preview.result.target.timezone)}} · {{hours(preview.result.targetPlannedSeconds)}}</p>
   <p class="wf-hint">{{label('工时为按计划和休息规则估算，最终以实际出勤为准。','Hours are estimated from the plan and break rules. Actual attendance determines the final total.')}}</p>
  </template>
  <p v-if="pending&&!busy" role="status">{{label('保存结果未确定，可重试同一笔调班。','Save result is uncertain. Retry the same transfer.')}}</p>
  <footer class="wf-form-actions">
   <button type="button" :disabled="blocked" @click="emit('close')">{{t('cancel')}}</button>
   <button v-if="!pending" :disabled="busy||loading||!targetShift">{{label('预览调店','Preview transfer')}}</button>
   <button v-if="preview||pending" type="button" class="primary" :disabled="busy||loading" @click="save">{{pending?label('重试保存','Retry save'):label('确认调店','Confirm transfer')}}</button>
  </footer>
 </form>
</template>
