<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {addDays,storeToday} from '../time'
import type {Correction,ScheduleResult} from '../types'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,home,t,p,base,loading,failure,load}=usePhasePage(),from=ref(addDays(storeToday(),-6)),to=ref(storeToday()),items=ref<Correction[]>([]);let initial=true
function refresh(){const span=(Date.parse(to.value)-Date.parse(from.value))/86400000;if(!(span>=0&&span<=30)){failure.value=p('invalid');return}void load(async()=>{const [items,schedule]=await Promise.all([api().get<Correction[]>(`/corrections?from=${from.value}&to=${to.value}`),api().get<ScheduleResult>(`/schedule?from=${from.value}&to=${to.value}`)]);return {items,timezone:schedule.timezone}},v=>{items.value=v.items;if(initial){initial=false;const actual=storeToday(v.timezone);if(actual!==to.value){to.value=actual;from.value=addDays(actual,-6);refresh()}}})}
onMounted(refresh)
</script>
<template><section class="wf-panel"><nav v-if="home?.employeeManage" class="wf-subnav"><RouterLink :to="base+'/attendance'">{{p('attendance')}}</RouterLink><RouterLink :to="base+'/corrections'">{{p('corrections')}}</RouterLink></nav><h2>{{p('corrections')}}</h2><form class="wf-filters" @submit.prevent="refresh"><label>{{p('from')}}<input v-model="from" type="date" required></label><label>{{p('to')}}<input v-model="to" type="date" :min="from" :max="addDays(from,30)" required></label><button :disabled="loading">{{p('filter')}}</button></form><PhaseStatus :failure="failure" :loading="loading"/><div v-if="!loading" class="wf-table-wrap"><table><thead><tr><th>{{p('employee')}}</th><th>{{p('reason')}}</th><th>{{p('status')}}</th><th></th></tr></thead><tbody><tr v-for="c in items" :key="c.id"><td>{{c.employeeName}}</td><td class="wf-wrap-text">{{c.reason}}</td><td><span class="wf-badge">{{p(c.status)}}</span></td><td><RouterLink :to="base+'/corrections/'+c.id+'?schedule='+c.scheduleId">{{p('detail')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="4" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div></section></template>
