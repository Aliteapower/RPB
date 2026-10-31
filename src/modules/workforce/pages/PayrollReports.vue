<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {usePayrollPage} from '../payrollPage'
import {useReportText} from '../reportText'
import {reportFields,reportQuery} from '../reportUi'
import {fetchReportCsv,saveReportCsv} from '../reportDownload'
import {validMonth} from '../payrollUi'
import {storeToday,hours} from '../time'
import type {PayrollHomeData} from '../payrollTypes'
import type {PayrollReport} from '../reportTypes'
import PayBreakdownSummary from '../components/PayBreakdownSummary.vue'
import {detailFields} from '../payBreakdown'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,store,current,r,t,issue,base,loading,busy,failure,notice,load,write}=usePayrollPage(),{q}=useReportText()
const home=ref<PayrollHomeData|null>(null),selected=ref<string[]>([]),month=ref(storeToday().slice(0,7)),data=ref<PayrollReport|null>(null)
const key=computed(()=>reportQuery(month.value,selected.value))
watch(key,()=>{data.value=null;notice.value='';failure.value=''})
async function reload(){data.value=null;if(!selected.value.length||!validMonth(month.value)){failure.value=q('noSelection');return}const captured=key.value;await load(()=>api().get<PayrollReport>('/payroll-report?'+captured),v=>{if(captured===key.value)data.value=v})}
onMounted(async()=>{await load(()=>api().get<PayrollHomeData>('/payroll-home'),v=>{home.value=v;selected.value=v.companies.slice(0,50).map(c=>c.id)});if(current()&&selected.value.length)await reload()})
function download(kind:'companies'|'employees'|'payments'|'items'){
 const report=data.value;if(!report)return;const captured=key.value,shop=store.value
 void write(async()=>{const blob=await fetchReportCsv(shop,captured+'&kind='+kind+'&fingerprint='+encodeURIComponent(report.fingerprint));if(current()&&shop===store.value&&captured===key.value&&data.value?.fingerprint===report.fingerprint)saveReportCsv(blob,`workforce-${kind}-${report.month}.csv`)})
}
</script>
<template><section class="wf-panel">
 <header class="wf-toolbar"><h2>{{q('reports')}}</h2><RouterLink class="wf-button" :to="base+'/payroll-reconciliation'">{{q('reconciliation')}}</RouterLink></header>
 <PhaseStatus :failure="failure" :notice="notice ? r('saved') : ''" :loading="loading"/>
 <template v-if="home?.companies.length"><p class="wf-hint">{{q('scope')}}</p>
 <form class="wf-report-filter" @submit.prevent="reload"><label>{{r('month')}}<input v-model="month" :disabled="busy" type="month" min="2000-01" max="2100-12" required></label><fieldset :disabled="busy"><legend>{{q('selected')}} · {{selected.length}} / {{home.companies.length}}</legend><label v-for="c in home.companies" :key="c.id" class="wf-check"><input v-model="selected" type="checkbox" :value="c.id">{{c.name}}</label></fieldset><button :disabled="loading||busy||!selected.length">{{q('load')}}</button></form>
 </template><p v-else-if="home" class="wf-empty">{{r('unavailable')}}</p>
 <template v-if="data&&!loading"><p class="wf-hint">{{data.month}} · {{data.currency}} · {{q('selected')}} {{data.companies.length}}</p><p class="wf-hint">{{q('frozenHint')}}</p>
 <div class="wf-report-totals"><section v-for="entry in [{name:'frozen' as const,value:data.frozen},{name:'forecast' as const,value:data.forecast}]" :key="entry.name"><h3>{{q(entry.name)}}</h3><p class="wf-hint">{{q('companyCount')}} {{entry.value.companyCount}} · {{q('employeeCount')}} {{entry.value.employeeCount}} · {{q('payrollRows')}} {{entry.value.payrollRows}}</p><dl class="wf-details"><template v-for="field in reportFields" :key="field"><dt>{{q(field)}}</dt><dd>{{entry.value.totals[field]}}</dd></template></dl><details v-if="entry.value.breakdown"><summary>{{r('breakdownTitle')}}</summary><PayBreakdownSummary :value="entry.value.breakdown" details aggregated/></details></section></div>
 <div class="wf-inline wf-section"><button data-export="items" :disabled="busy" @click="download('items')">{{r('exportItems')}}</button><button data-export="companies" :disabled="busy" @click="download('companies')">{{q('exportCompanies')}}</button><button data-export="employees" :disabled="busy" @click="download('employees')">{{q('exportEmployees')}}</button><button data-export="payments" :disabled="busy" @click="download('payments')">{{q('payments')}}</button></div><p class="wf-hint">{{q('exportHint')}}</p>
 <h3>{{q('companies')}}</h3><div class="wf-table-wrap"><table><thead><tr><th>{{r('company')}}</th><th>{{t('status')}}</th><th>{{r('count')}}</th><th v-for="field in reportFields" :key="field">{{q(field)}}</th><th>{{r('issues')}}</th></tr></thead><tbody><tr v-for="c in data.companies" :key="c.companyId"><td><RouterLink v-if="c.batchId" :to="base+'/payroll-batches/'+c.batchId">{{c.companyName}}</RouterLink><span v-else>{{c.companyName}}</span></td><td>{{c.status==='missing'?q('missing'):r(c.status)}}<div v-if="c.sourceChanged" class="wf-issue">{{q('sourceChanged')}}</div></td><td>{{c.employeeCount}}</td><td v-for="field in reportFields" :key="field">{{c.totals?.[field]??'—'}}</td><td><template v-if="c.totals"><div v-for="code in c.issues" :key="code" class="wf-issue">{{issue(code)}}</div></template><span v-else>—</span></td></tr></tbody></table></div>
 <h3 class="wf-section">{{q('employees')}}</h3><div class="wf-table-wrap"><table><thead><tr><th colspan="5">{{r('employee')}}</th><th colspan="5">{{r('incomeGroup')}}</th><th>{{r('reimbursements')}}</th><th colspan="3">{{r('deductionGroup')}}</th><th colspan="3">{{r('employerGroup')}}</th><th colspan="2">{{r('amount')}}</th><th colspan="2">{{r('paymentPlan')}}</th><th colspan="2">{{r('paymentHistory')}}</th></tr><tr><th>{{r('employee')}}</th><th>{{r('company')}}</th><th>{{t('status')}}</th><th>{{r('work')}}</th><th>{{r('overtime')}}</th><th v-for="field in detailFields" :key="field">{{r(({basic:'basicPay',overtime:'overtimePay',employerCharges:'employerBorne'} as Record<string,string>)[field] as Parameters<typeof r>[0]||field as Parameters<typeof r>[0])}}</th><th>{{q('gross')}}</th><th>{{q('net')}}</th><th>{{r('plannedMethod')}}</th><th>{{r('plannedBank')}}</th><th>{{q('paid')}}</th><th>{{q('remaining')}}</th></tr></thead><tbody><tr v-for="e in data.employees" :key="e.companyId+e.employeeId"><td><RouterLink :to="base+'/payroll-batches/'+e.batchId+'/payslips/'+e.employeeId">{{e.employeeName}} · {{e.employeeNo}}</RouterLink></td><td>{{e.companyName}}</td><td>{{r(e.status as 'calculated')}}</td><td>{{hours(e.workSeconds)}}</td><td>{{hours(e.overtimeSeconds)}}</td><td v-for="field in detailFields" :key="field">{{e.breakdown?.[field]??'—'}}</td><td>{{e.totals.gross}}</td><td>{{e.totals.net}}</td><td>{{e.paymentMethod?r(e.paymentMethod as 'bank'):r('paymentUnknown')}}</td><td>{{e.bank||'—'}}</td><td>{{e.totals.paid}}</td><td>{{e.totals.remaining}}</td></tr><tr v-if="!data.employees.length"><td colspan="23" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{q('sdlHint')}}</p>
 </template>
</section></template>
