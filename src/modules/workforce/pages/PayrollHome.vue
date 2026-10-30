<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {usePayrollPage} from '../payrollPage'
import type {PayrollHomeData} from '../payrollTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
import PayrollWorkbench from '../components/PayrollWorkbench.vue'
import {usePayrollWorkbenchText} from '../payrollWorkbenchText'
import {useReportText} from '../reportText'
import {storeToday} from '../time'
const {q}=useReportText(),w=usePayrollWorkbenchText(),month=ref(storeToday().slice(0,7))
const {api,r,t,base,loading,failure,load,english}=usePayrollPage(),data=ref<PayrollHomeData|null>(null),company=ref('')
const query=computed(()=>({companyId:company.value}))
onMounted(()=>void load(()=>api().get<PayrollHomeData>('/payroll-home'),v=>{data.value=v;company.value=v.companies[0]?.id||''}))
</script>
<template><section class="wf-panel wf-payroll-page">
 <header class="wf-management-header"><h2>{{r('payroll')}}</h2><label v-if="data&&!loading&&data.companies.length" class="wf-management-company">{{r('selectCompany')}}<select v-model="company"><option v-for="c in data.companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label></header>
 <PhaseStatus :failure="failure" :loading="loading"/>
 <template v-if="data&&!loading"><p class="wf-hint">{{r('scopeHint')}}</p><p v-if="!data.companies.length" class="wf-hint">{{r('unavailable')}}</p>
  <nav v-if="data.companies.length||data.employeeId" class="wf-management-links" :aria-label="r('payroll')">
   <template v-if="data.companies.length"><RouterLink :to="{path:base+'/supplemental-payroll',query}"><strong>{{english()?'Supplemental payroll':'独立补发工资'}}</strong><span>{{english()?'Approved new differences, independent cash and statutory settlement':'批准的新差额、独立现金与法定结算'}}</span></RouterLink><RouterLink :to="{path:base+'/payroll-batches',query}"><strong>{{r('batchOperations')}}</strong><span>{{r('batchOperationsHint')}}</span></RouterLink><RouterLink :to="base+'/payroll-reports'"><strong>{{q('reports')}}</strong><span>{{r('reportsEntryHint')}}</span></RouterLink><RouterLink :to="base+'/payroll-reconciliation'"><strong>{{q('reconciliation')}}</strong><span>{{r('reconciliationEntryHint')}}</span></RouterLink></template>
   <RouterLink v-if="data.employeeId" :to="base+'/payslips'"><strong>{{r('myPayslips')}}</strong><span>{{r('myPayslipsEntryHint')}}</span></RouterLink>
  </nav>
  <template v-if="company"><label>{{w('month')}}<input v-model="month" type="month"></label><PayrollWorkbench :company-id="company" :month="month"/></template>
  <details class="wf-payroll-policy"><summary>{{r('policy')}}</summary><dl class="wf-details"><dt>{{r('policyYear')}}</dt><dd>{{data.policy.year}} · {{data.policy.version}}</dd><dt>{{r('ordinaryCeiling')}}</dt><dd>SGD {{data.policy.owCeiling}}</dd><dt>{{r('authority')}}</dt><dd class="wf-inline"><a v-if="data.policy.cpfSource" :href="data.policy.cpfSource" target="_blank" rel="noopener">CPF Board</a><a v-if="data.policy.sdlSource" :href="data.policy.sdlSource" target="_blank" rel="noopener">SDL</a></dd></dl><p class="wf-hint">{{r('supportHint')}}</p></details>
 </template>
</section></template>
