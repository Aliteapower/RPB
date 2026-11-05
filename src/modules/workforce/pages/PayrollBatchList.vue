<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {usePayrollPage} from '../payrollPage'
import {chooseCompany} from '../payrollUi'
import type {PayrollHomeData,BatchSummary} from '../payrollTypes'
import PayrollBulkOperations from '../components/PayrollBulkOperations.vue'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),{api,r,t,base,loading,failure,load}=usePayrollPage(),company=ref(''),items=ref<BatchSummary[]>([])
function refresh(){void load(async()=>{const h=await api().get<PayrollHomeData>('/payroll-home'),c=chooseCompany(h,String(route.query.companyId||''));return {c,list:await api().get<BatchSummary[]>('/payroll-batches?companyId='+c)}},v=>{company.value=v.c;items.value=v.list})}
onMounted(refresh)
</script>
<template><section class="wf-panel"><header class="wf-toolbar"><h2>{{r('batches')}}</h2><RouterLink v-if="company" class="wf-button primary" :to="{path:base+'/payroll-batches/new',query:{companyId:company}}">{{r('newBatch')}}</RouterLink></header><PhaseStatus :failure="failure" :loading="loading"/><PayrollBulkOperations v-if="company" :items="items" :loading="loading" @completed="refresh"/><div v-if="company&&!loading" class="wf-table-wrap"><table><thead><tr><th>{{r('month')}}</th><th>{{r('company')}}</th><th>{{t('status')}}</th><th>{{r('count')}}</th><th>{{r('gross')}}</th><th>{{r('net')}}</th><th></th></tr></thead><tbody><tr v-for="b in items" :key="b.id"><td>{{b.month}}</td><td>{{b.companyName}}</td><td><span class="wf-badge">{{r(b.status)}}</span></td><td>{{b.employeeCount}}</td><td>{{b.totalGross||'—'}}</td><td>{{b.totalNet||'—'}}</td><td><RouterLink :to="base+'/payroll-batches/'+b.id">{{r('details')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="7" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{r('monthHint')}}</p></section></template>
