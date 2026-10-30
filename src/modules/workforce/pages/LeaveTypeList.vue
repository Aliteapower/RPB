<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import type {LeaveType} from '../phaseThreeTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,home,t,base,loading,failure,load,p}=usePhasePage(),{q}=usePhaseThreeText(),items=ref<LeaveType[]>([])
onMounted(()=>{if(!home.value?.employeeManage){failure.value=p('noAccess');return}void load(()=>api().get<LeaveType[]>('/leave-types'),v=>items.value=v)})
</script>
<template><section class="wf-panel"><nav class="wf-subnav"><RouterLink :to="base+'/leave-types'">{{q('leaveTypes')}}</RouterLink><RouterLink :to="base+'/calendar'">{{q('calendar')}}</RouterLink></nav><header class="wf-toolbar"><h2>{{q('leaveTypes')}}</h2><RouterLink v-if="home?.employeeManage" class="wf-button primary" :to="base+'/leave-types/new'">{{q('newLeaveType')}}</RouterLink></header><PhaseStatus :failure="failure" :loading="loading"/><div v-if="!loading&&home?.employeeManage" class="wf-table-wrap"><table><thead><tr><th>{{q('typeName')}}</th><th>{{q('payTreatment')}}</th><th>{{q('enabled')}}</th><th></th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td>{{item.name}}</td><td>{{q(item.paid?'paid':'unpaid')}}</td><td>{{q(item.enabled?'enabled':'disabled')}}</td><td><RouterLink :to="base+'/leave-types/'+item.id">{{t('edit')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="4" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{q('leaveTypeHint')}}</p></section></template>
