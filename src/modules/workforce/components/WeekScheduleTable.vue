<script setup lang="ts">
import {restaurantScheduleRange} from '../restaurantShifts'
import RestSwapLabels from './RestSwapLabels.vue'
import CalendarDateLabels from './CalendarDateLabels.vue'
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Schedule} from '../types'
import type {ScheduleBoard} from '../bulkScheduleTypes'
import {addDays,storeToday} from '../time'
import {useScheduleText} from '../scheduleText'
const {locale}=useI18n(),english=()=>locale.value.startsWith('en')
const props=defineProps<{data:ScheduleBoard;week:string;base:string;to?:string}>(),{s,days}=useScheduleText()
const emit=defineEmits<{create:[employeeId:string,date:string];edit:[schedule:Schedule];staffing:[shiftId:string,shiftName:string,date:string]}>()
const dates=computed(()=>{const dates:string[]=[];for(let day=props.week;day<=(props.to||addDays(props.week,6))&&dates.length<31;day=addDays(day,1))dates.push(day);return dates})
function weekday(date:string){return days()[(new Date(date+'T12:00:00Z').getUTCDay()+6)%7]}
const slots=computed(()=>{const index=new Map<string,typeof props.data.items>();for(const row of props.data.items){const key=row.employeeId+':'+row.workDate;index.set(key,[...(index.get(key)||[]),row])}return index})
function headcount(shiftId:string,date:string){return props.data.staffing?.days.find(row=>row.shiftId===shiftId&&row.date===date)?.people||0}
function canAdd(date:string){const today=storeToday(props.data.timezone);return date>=today&&date<=addDays(today,90)}
</script>
<template><div class="wf-table-wrap wf-roster-wrap"><table class="wf-roster" :class="{'wf-roster-month':dates.length>7}" :style="dates.length>7?{width:(155+dates.length*112)+'px'}:{}"><thead><tr><th scope="col">{{s('count')}}</th><th v-for="(date,i) in dates" :key="date" scope="col">{{weekday(date)}}<small>{{date.slice(5)}}</small><CalendarDateLabels :events="data.calendar" :date="date"/></th></tr></thead><tbody><tr v-for="employee in data.employees" :key="employee.id"><th scope="row">{{employee.name}}<small>{{employee.employeeNo}}</small></th><td v-for="date in dates" :key="date"><RestSwapLabels :items="data.restSwaps" :date="date" :employee-id="employee.id"/><button type="button" v-for="row in slots.get(employee.id+':'+date)||[]" :key="row.id" :data-schedule-id="row.id" @click="emit('edit',row)" :aria-label="s('editInCell')+' · '+employee.name+' · '+date+' · '+row.shift.name" class="wf-roster-shift" :class="{'is-cancelled':row.status==='cancelled'}"><strong>{{row.shift.name}}</strong><span>{{restaurantScheduleRange(row,english())}}</span><small v-if="row.status==='cancelled'">{{s('cancelled')}}</small></button><template v-if="!slots.get(employee.id+':'+date)?.length"><button type="button" v-if="canAdd(date)" @click="emit('create',employee.id,date)" class="wf-roster-empty wf-roster-add" :aria-label="s('newInCell')+' · '+employee.name+' · '+date" :title="s('newInCell')">＋</button><span v-else class="wf-roster-empty" :aria-label="s('emptyCell')" :title="s('outsideRange')">—</span></template></td></tr><tr v-if="!data.employees.length"><td :colspan="dates.length+1" class="wf-empty">—</td></tr></tbody><tfoot v-if="data.staffing"><tr v-if="data.staffing.dailyTotals" class="wf-roster-counts"><th scope="row">{{english()?'Daily total (unique)':'每日总人数（去重）'}}</th><td v-for="date in dates" :key="date">{{data.staffing.dailyTotals.find(d=>d.date===date)?.people||0}} {{s('peopleUnit')}}</td></tr><tr v-for="shift in data.staffing.shifts" :key="shift.shiftId" class="wf-roster-counts"><th scope="row">{{shift.name}}<small>{{s('dailyStaffing')}}</small></th><td v-for="date in dates" :key="date"><button type="button" v-if="headcount(shift.shiftId,date)>0" class="wf-count-button" :aria-label="s('viewStaffing')+' · '+shift.name+' · '+date" @click="emit('staffing',shift.shiftId,shift.name,date)">{{headcount(shift.shiftId,date)}} {{s('peopleUnit')}}</button><template v-else>0 {{s('peopleUnit')}}</template></td></tr></tfoot></table></div></template>


