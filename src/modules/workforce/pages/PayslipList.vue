<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {usePayrollPage} from '../payrollPage'
import {storeToday} from '../time'
import type {PayslipSummary} from '../payrollTypes'
import SupplementalPayslips from '../components/SupplementalPayslips.vue'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,r,t,base,loading,failure,load}=usePayrollPage(),year=ref(storeToday().slice(0,4)),items=ref<PayslipSummary[]>([])
function refresh(){if(!/^\d{4}$/.test(year.value)){failure.value=r('invalid');return}items.value=[];void load(()=>api().get<PayslipSummary[]>('/my-payslips?year='+year.value),v=>items.value=v)}
onMounted(refresh)
</script>
<template><section class="wf-panel"><h2>{{r('myPayslips')}}</h2><form class="wf-filters" @submit.prevent="refresh"><label>{{r('year')}}<input v-model="year" required inputmode="numeric" pattern="\d{4}" maxlength="4"></label><button :disabled="loading">{{t('refresh')}}</button></form><PhaseStatus :failure="failure" :loading="loading"/><div v-if="!loading" class="wf-table-wrap"><table><thead><tr><th>{{r('month')}}</th><th>{{r('company')}}</th><th>{{r('net')}}</th><th>{{r('paid')}}</th><th></th></tr></thead><tbody><tr v-for="v in items" :key="v.batchId"><td>{{v.month}}</td><td>{{v.companyName}}</td><td>{{v.netPay}}</td><td>{{v.paidAmount}}</td><td><RouterLink :to="base+'/payslips/'+v.batchId">{{r('details')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="5" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><SupplementalPayslips :year="year"/></section></template>
