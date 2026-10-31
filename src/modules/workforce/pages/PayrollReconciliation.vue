<script setup lang="ts">
import {computed,onMounted,reactive,ref,watch} from 'vue'
import {usePayrollPage} from '../payrollPage'
import {useReportText} from '../reportText'
import {reportFields,reportQuery,emptyExpected,validExpected} from '../reportUi'
import {validMonth} from '../payrollUi'
import {storeToday} from '../time'
import type {PayrollHomeData} from '../payrollTypes'
import type {PayrollReport,ReportComparison} from '../reportTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const {api,current,r,base,loading,busy,failure,notice,load,write}=usePayrollPage(),{q}=useReportText(),home=ref<PayrollHomeData|null>(null),company=ref(''),month=ref(storeToday().slice(0,7)),data=ref<PayrollReport|null>(null),result=ref<ReportComparison|null>(null),expected=reactive(emptyExpected())
const key=computed(()=>reportQuery(month.value,company.value?[company.value]:[])),frozen=computed(()=>data.value&&['confirmed','published'].includes(data.value.companies[0]?.status))
watch(key,()=>{data.value=null;result.value=null;Object.assign(expected,emptyExpected());failure.value='';notice.value=''})
watch(expected,()=>{result.value=null})
async function reload(){data.value=null;result.value=null;if(!company.value||!validMonth(month.value)){failure.value=r('invalid');return}const captured=key.value;await load(()=>api().get<PayrollReport>('/payroll-report?'+captured),v=>{if(captured===key.value)data.value=v})}
onMounted(async()=>{await load(()=>api().get<PayrollHomeData>('/payroll-home'),v=>{home.value=v;company.value=v.companies[0]?.id||''});if(current()&&company.value)await reload()})
function compare(){if(!data.value||!frozen.value||!validExpected(expected)){failure.value=r('invalid');return}const captured=key.value,body={companyId:company.value,month:month.value,fingerprint:data.value.fingerprint,expected:{...expected}};void write(async()=>{const response=await api().put<ReportComparison>('/payroll-reconciliation',body);if(current()&&captured===key.value)result.value=response})}
</script>
<template><section class="wf-panel"><header class="wf-toolbar"><h2>{{q('reconciliation')}}</h2><RouterLink class="wf-button" :to="base+'/payroll-reports'">{{q('reports')}}</RouterLink></header><p class="wf-hint">{{q('reconcileHint')}}</p><PhaseStatus :failure="failure" :notice="notice ? r('saved') : ''" :loading="loading"/>
 <form v-if="home?.companies.length" class="wf-filters" @submit.prevent="reload"><label>{{r('company')}}<select v-model="company" :disabled="busy"><option v-for="c in home.companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label><label>{{r('month')}}<input v-model="month" :disabled="busy" type="month" min="2000-01" max="2100-12" required></label><button :disabled="loading||busy">{{q('load')}}</button></form><p v-else-if="home" class="wf-empty">{{r('unavailable')}}</p>
 <template v-if="data&&!loading"><p>{{data.companies[0].companyName}} · {{data.month}} · SGD · {{data.companies[0].status==='missing'?q('missing'):r(data.companies[0].status)}}</p><p v-if="data.companies[0].sourceChanged" class="wf-issue">{{q('sourceChanged')}}</p><p v-if="!frozen" class="wf-hint">{{q('notFrozen')}}</p>
 <form v-else data-compare="1" @submit.prevent="compare"><fieldset :disabled="busy"><div class="wf-table-wrap"><table><thead><tr><th></th><th>{{q('actual')}}</th><th>{{q('expected')}}</th></tr></thead><tbody><tr v-for="field in reportFields" :key="field"><th><label :for="'report-'+field">{{q(field)}}</label></th><td>{{data.frozen.totals[field]}}</td><td><input :id="'report-'+field" v-model="expected[field]" :data-amount="field" inputmode="decimal" required maxlength="15" :aria-label="q(field)+' '+q('expected')"></td></tr></tbody></table></div></fieldset><button class="primary wf-section" :disabled="busy||!validExpected(expected)">{{q('compare')}}</button></form>
 <section v-if="result" class="wf-section"><h3 :class="result.matched?'wf-success':'wf-issue'">{{result.matched?q('matched'):q('different')}}</h3><div class="wf-table-wrap"><table><thead><tr><th></th><th>{{q('expected')}}</th><th>{{q('actual')}}</th><th>{{q('difference')}}</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.field"><th>{{q(item.field)}}</th><td>{{item.expected}}</td><td>{{item.actual}}</td><td :class="item.difference==='0.00'?'':'wf-issue'">{{item.difference}}</td></tr></tbody></table></div></section><p class="wf-hint">{{q('comparisonLimit')}}</p>
 </template></section></template>
