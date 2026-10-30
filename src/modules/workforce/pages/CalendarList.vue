<script setup lang="ts">
import AttendancePolicySettings from '../components/AttendancePolicySettings.vue'
import {useRestPolicyText} from '../restPolicy'
import RestSwapDialog from '../components/RestSwapDialog.vue'
import RestSwapLabels from '../components/RestSwapLabels.vue'
import {useRestSwapText} from '../restSwap'
import {useRoute} from 'vue-router'
import {useHolidayText} from '../holidayYears'
import StoreRestMonth from '../components/StoreRestMonth.vue'
import {onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import {dateRangeValid,monthEnd} from '../timekeepingUi'
import {addDays,storeToday} from '../time'
import type {CalendarPage} from '../phaseThreeTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const {h}=useHolidayText(),{r}=useRestSwapText(),{r:policyText}=useRestPolicyText(),route=useRoute(),swapOpen=ref(false),policyOpen=ref(false)
const {api,home,t,p,base,loading,failure,load}=usePhasePage(),{q}=usePhaseThreeText(),from=ref((typeof route.query.month==='string'&&/^\d{4}-\d{2}$/.test(route.query.month)?route.query.month:storeToday().slice(0,7))+'-01'),to=ref(monthEnd(from.value)),data=ref<CalendarPage|null>(null)
function refresh(){if(!dateRangeValid(from.value,to.value)){failure.value=p('invalid');return}data.value=null;void load(()=>api().get<CalendarPage>(`/calendar?from=${from.value}&to=${to.value}`),v=>data.value=v)}
function restChanged(month:string){from.value=month+'-01';to.value=monthEnd(from.value);refresh()}
function swapped(restDate:string,makeupDate:string){swapOpen.value=false;from.value=restDate<makeupDate?restDate:makeupDate;to.value=restDate>makeupDate?restDate:makeupDate;refresh()}
onMounted(refresh)
</script>
<template><section class="wf-panel"><nav v-if="home?.employeeManage" class="wf-subnav"><RouterLink :to="base+'/leave-types'">{{q('leaveTypes')}}</RouterLink><RouterLink :to="base+'/public-holidays'">{{h('annual')}}</RouterLink><RouterLink :to="base+'/calendar'">{{q('calendar')}}</RouterLink></nav><div v-if="home?.employeeManage" class="wf-form-actions"><button type="button" :aria-expanded="policyOpen" @click="policyOpen=!policyOpen">{{policyText('templates')}}</button></div><AttendancePolicySettings v-if="policyOpen&&home?.employeeManage"/><StoreRestMonth v-if="home?.employeeManage" @changed="restChanged"/><div v-if="home?.employeeManage" class="wf-form-actions"><button type="button" @click="swapOpen=true">{{r('open')}}</button></div><RestSwapDialog v-if="swapOpen&&home?.employeeManage" @close="swapOpen=false" @saved="swapped"/><header class="wf-toolbar"><h2>{{q('calendar')}}</h2><RouterLink v-if="home?.employeeManage" class="wf-button primary" :to="base+'/calendar/new'">{{q('newEvent')}}</RouterLink></header><form class="wf-filters" @submit.prevent="refresh"><label>{{p('from')}}<input v-model="from" required type="date"></label><label>{{p('to')}}<input v-model="to" required type="date" :min="from" :max="addDays(from,30)"></label><button :disabled="loading">{{p('filter')}}</button></form><PhaseStatus :failure="failure" :loading="loading"/><div v-if="data&&!loading" class="wf-table-wrap"><table><thead><tr><th>{{q('date')}}</th><th>{{q('name')}}</th><th>{{q('scheduled')}}</th><th>{{q('attended')}}</th><th></th></tr></thead><tbody><tr v-for="day in data.days" :key="day.date"><td>{{day.date}}</td><td><RestSwapLabels :items="data.restSwaps" :date="day.date" show-name/><div v-for="event in data.items.filter(e=>e.date===day.date&&(e.enabled||home?.employeeManage))" :key="event.id" class="wf-calendar-event"><span class="wf-badge">{{q(event.kind)}}</span> {{event.name}}<small v-if="event.observed"> · {{h('inLieu')}} {{event.originDate}}</small><small v-if="event.tentative"> · {{h('unconfirmed')}}</small><RouterLink v-if="event.scope==='tenant'&&home?.headOffice" :to="base+'/public-holidays?year='+event.year">{{t('edit')}}</RouterLink><small v-if="!event.enabled"> · {{q('disabled')}}</small><RouterLink v-if="home?.employeeManage&&event.scope!=='tenant'" :to="{path:base+'/calendar/'+event.id,query:{date:event.date}}">{{t('edit')}}</RouterLink></div></td><td>{{day.schedules}}</td><td>{{day.attended}}</td><td><RouterLink :to="{path:base+'/attendance',query:{date:day.date}}">{{q('detail')}}</RouterLink></td></tr><tr v-if="!data.days.length"><td colspan="5" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{q('calendarHint')}}<span v-if="data"> · {{p('timezone')}}: {{data.timezone}}</span></p></section></template>

