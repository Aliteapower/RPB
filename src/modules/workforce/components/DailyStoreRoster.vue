<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import DateInput from '../../../components/common/DateInput.vue'
import PhaseStatus from './PhaseStatus.vue'
import {usePhasePage} from '../phasePage'
import {addDays} from '../time'

interface Roster {
 date:string
 people:number
 stores:{id:string;name:string;employees:{id:string;name:string;employeeNo:string;position:string|null;
  segments:{id:string;shiftName:string;startAt:string;endAt:string;timezone:string}[]}[]}[]
}
const props=defineProps<{initialDate:string}>()
const {api,store,t,p,english,loading,failure,load,format}=usePhasePage()
const date=ref(props.initialDate),roster=ref<Roster|null>(null)
function refresh(){
 roster.value=null
 const selected=date.value
 if(!selected||addDays(selected,0)!==selected)return
 void load(()=>api().get<Roster>('/schedule-daily?date='+encodeURIComponent(selected)),result=>{
  if(result.date===date.value)roster.value=result
 })
}
watch([date,store],refresh)
onMounted(refresh)
</script>

<template>
 <section aria-labelledby="wf-daily-roster-title">
  <header class="wf-toolbar">
   <h3 id="wf-daily-roster-title">{{english()?'Daily roster by store':'当日各店名单'}}</h3>
   <label>{{p('workDate')}}<DateInput v-model="date" type="date" required /></label>
   <button type="button" :disabled="loading||!date" @click="refresh">{{english()?'Refresh':'刷新'}}</button>
  </header>
  <PhaseStatus :loading="loading" :failure="failure" notice=""/>
  <template v-if="roster">
   <p>{{english()?'Unique employees':'员工总人数（去重）'}} · {{roster.people}}</p>
   <article v-for="group in roster.stores" :key="group.id">
    <h4>{{group.name}} · {{group.employees.length}} {{english()?'people':'人'}}</h4>
    <div v-if="group.employees.length" class="wf-table-wrap">
     <table>
      <thead><tr><th>{{p('employee')}}</th><th>{{english()?'Position':'岗位'}}</th><th>{{p('plan')}}</th></tr></thead>
      <tbody>
       <tr v-for="employee in group.employees" :key="employee.id">
        <td>{{employee.name}} <small>{{employee.employeeNo}}</small></td>
        <td>{{employee.position||'—'}}</td>
        <td>
         <div v-for="segment in employee.segments" :key="segment.id">
          {{segment.shiftName}} · {{format(segment.startAt,segment.timezone)}} – {{format(segment.endAt,segment.timezone)}}
          <RouterLink :to="'/stores/'+group.id+'/workforce/schedule/'+segment.id">{{t('manage')}}</RouterLink>
         </div>
        </td>
       </tr>
      </tbody>
     </table>
    </div>
   </article>
  </template>
 </section>
</template>
