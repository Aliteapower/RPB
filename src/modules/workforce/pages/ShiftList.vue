<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {shiftRuleSummary} from '../restPolicy'
import {restaurantShiftRange,restaurantText} from '../restaurantShifts'
import type {Shift} from '../types'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,home,t,p,base,loading,failure,load,english}=usePhasePage(),items=ref<Shift[]>([])
onMounted(()=>{if(home.value?.employeeManage)void load(()=>api().get<Shift[]>('/shifts'),v=>items.value=v)})
</script>
<template><section v-if="home?.employeeManage" class="wf-panel"><header class="wf-toolbar"><h2>{{p('shifts')}}</h2><RouterLink class="wf-button primary" :to="base+'/shifts/new'">{{p('newShift')}}</RouterLink></header><p class="wf-hint">{{restaurantText(english()).business}}</p><PhaseStatus :failure="failure" :loading="loading"/><div v-if="!loading" class="wf-table-wrap"><table><thead><tr><th>{{p('name')}}</th><th>{{p('plan')}}</th><th>{{p('breakMode')}}</th><th>{{p('status')}}</th><th></th></tr></thead><tbody><tr v-for="s in items" :key="s.id"><td>{{s.name}}</td><td>{{restaurantShiftRange(s,english())}}</td><td>{{shiftRuleSummary(s,p(s.breakMode),english())}} <small v-if="!s.rules&&s.breakPaid">{{p('breakPaid')}}</small></td><td>{{t(s.enabled?'enabledStatus':'disabled')}}</td><td><RouterLink :to="base+'/shifts/'+s.id">{{t('manage')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="5" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div></section><p v-else role="alert">{{t('noAccess')}}</p></template>

