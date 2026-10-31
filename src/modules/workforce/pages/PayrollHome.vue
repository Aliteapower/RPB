<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
const route=useRoute(),router=useRouter()
import {usePayrollPage} from '../payrollPage'
import type {PayrollHomeData} from '../payrollTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
import PayrollWorkbench from '../components/PayrollWorkbench.vue'
import PayrollMonthReconciliation from '../components/PayrollMonthReconciliation.vue'
const showReconciliation=ref(false)
import GuidedPayrollBoard from '../components/GuidedPayrollBoard.vue'
import {usePayrollWorkbenchText} from '../payrollWorkbenchText'
import {useReportText} from '../reportText'
import {storeToday} from '../time'
const {q}=useReportText(),w=usePayrollWorkbenchText(),month=ref(/^\d{4}-(0[1-9]|1[0-2])$/.test(String(route.query.month||''))?String(route.query.month):storeToday().slice(0,7)),locked=ref(false)
const {api,r,t,base,home,store,loading,failure,load,english}=usePayrollPage(),data=ref<PayrollHomeData|null>(null),company=ref(String(route.query.companyId||''))
const query=computed(()=>({companyId:company.value,month:month.value}))
watch([company,month],()=>{if(data.value?.companies.some(c=>c.id===company.value)&&typeof router.replace==='function')void router.replace({query:{...route.query,companyId:company.value,month:month.value}})})
onMounted(()=>void load(()=>api().get<PayrollHomeData>('/payroll-home'),v=>{data.value=v;if(company.value&&!v.companies.some(c=>c.id===company.value))throw new Error(english()?'No payroll access for this company':'无此公司薪资权限');company.value=company.value||v.companies.find(c=>c.id===home.value?.stores?.find(s=>s.id===store.value)?.companyId)?.id||v.companies[0]?.id||''}))
</script>
<template><section class="wf-panel wf-payroll-page">
 <header class="wf-management-header"><h2>{{r('payroll')}}</h2><label v-if="data&&!loading&&data.companies.length" class="wf-management-company">{{r('selectCompany')}}<select v-model="company" :disabled="locked"><option v-for="c in data.companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label></header>
 <PhaseStatus :failure="failure" :loading="loading"/>
 <template v-if="data&&!loading"><p class="wf-hint">{{r('scopeHint')}}</p><p v-if="!data.companies.length&&!data.employeeId" class="wf-hint">{{r('unavailable')}}</p>
  <nav v-if="data.employeeId" class="wf-management-links" :aria-label="r('myPayslips')"><RouterLink v-if="data.employeeId" :to="base+'/payslips'"><strong>{{r('myPayslips')}}</strong><span>{{r('myPayslipsEntryHint')}}</span></RouterLink></nav><template v-if="company"><label>{{w('month')}}<input v-model="month" type="month" :disabled="locked"></label><GuidedPayrollBoard :company-id="company" :month="month" :employee-id="data.employeeId" @locked="locked=$event"/></template><details v-if="data.companies.length"><summary>{{english()?'Other payroll tasks and reports':'其他薪资办理与报表'}}</summary><nav v-if="data.companies.length||data.employeeId" class="wf-management-links" :aria-label="r('payroll')">
   <template v-if="data.companies.length"><RouterLink :to="{path:base+'/payroll-segments',query}"><strong>{{english()?'Independent settlement':'提前／分段结薪'}}</strong><span>{{english()?'Settle a leaver or an employed person by period; reconcile the month':'离职或在职分段办理，月底统一归集对账'}}</span></RouterLink><RouterLink :to="{path:base+'/position-pay-plans',query}"><strong>{{english()?'Position salary standards':'岗位薪资标准'}}</strong><span>{{english()?'Monthly / hourly standards and personal adoption':'按岗位维护月薪、时薪标准，员工按需采用'}}</span></RouterLink><RouterLink :to="{path:base+'/supplemental-payroll',query}"><strong>{{english()?'Supplemental payroll':'独立补发工资'}}</strong><span>{{english()?'Approved new differences, independent cash and statutory settlement':'批准的新差额、独立现金与法定结算'}}</span></RouterLink><RouterLink :to="{path:base+'/payroll-batches',query}"><strong>{{r('batchOperations')}}</strong><span>{{r('batchOperationsHint')}}</span></RouterLink><RouterLink :to="base+'/payroll-reports'"><strong>{{q('reports')}}</strong><span>{{r('reportsEntryHint')}}</span></RouterLink><RouterLink :to="base+'/payroll-reconciliation'"><strong>{{q('reconciliation')}}</strong><span>{{r('reconciliationEntryHint')}}</span></RouterLink></template>

  </nav></details>
  <details v-if="company" @toggle="showReconciliation=($event.target as HTMLDetailsElement).open"><summary>{{english()?'Month-end reconciliation and supplements':'月底统计与个人补发对账'}}</summary><PayrollMonthReconciliation v-if="showReconciliation" :company-id="company" :month="month"/></details><details v-if="company"><summary>{{english()?'Detailed source checks and changes':'来源检查与变化明细'}}</summary><PayrollWorkbench :company-id="company" :month="month"/></details>
  <details class="wf-payroll-policy"><summary>{{r('policy')}}</summary><dl class="wf-details"><dt>{{r('policyYear')}}</dt><dd>{{data.policy.year}} · {{data.policy.version}}</dd><dt>{{r('ordinaryCeiling')}}</dt><dd>SGD {{data.policy.owCeiling}}</dd><dt>{{r('authority')}}</dt><dd class="wf-inline"><a v-if="data.policy.cpfSource" :href="data.policy.cpfSource" target="_blank" rel="noopener">CPF Board</a><a v-if="data.policy.sdlSource" :href="data.policy.sdlSource" target="_blank" rel="noopener">SDL</a></dd></dl><p class="wf-hint">{{r('supportHint')}}</p></details>
 </template>
</section></template>
