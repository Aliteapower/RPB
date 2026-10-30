<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePayrollPage} from '../payrollPage'
import {chooseCompany} from '../payrollUi'
import type {PayrollHomeData,SalaryTemplate} from '../payrollTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),{api,r,t,base,loading,failure,load}=usePayrollPage(),company=ref(''),companies=ref<PayrollHomeData['companies']>([]),items=ref<SalaryTemplate[]>([])
function refresh(){items.value=[];void load(()=>{const c=chooseCompany({companies:companies.value},company.value);return api().get<SalaryTemplate[]>('/salary-templates?companyId='+encodeURIComponent(c))},v=>items.value=v)}
function changeCompany(){void router.replace({query:{...route.query,companyId:company.value}});refresh()}
onMounted(async()=>{await load(()=>api().get<PayrollHomeData>('/payroll-home'),h=>{company.value=chooseCompany(h,String(route.query.companyId||''));companies.value=h.companies});if(company.value)refresh()})
</script>
<template><section class="wf-panel"><header class="wf-toolbar"><h2>{{r('templates')}}</h2><RouterLink v-if="company" class="wf-button primary" :to="{path:base+'/salary-templates/new',query:{companyId:company}}">{{r('newTemplate')}}</RouterLink></header><div v-if="companies.length" class="wf-filters"><label>{{r('company')}}<select v-model="company" @change="changeCompany"><option v-for="c in companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label></div><PhaseStatus :failure="failure" :loading="loading"/><div v-if="company&&!loading" class="wf-table-wrap"><table><thead><tr><th>{{r('name')}}</th><th>{{r('basis')}}</th><th>{{r('rate')}}</th><th>{{t('status')}}</th><th></th></tr></thead><tbody><tr v-for="s in items" :key="s.id"><td>{{s.name}}</td><td>{{r(s.rule.basis)}}</td><td>{{s.rule.rate}}</td><td>{{r(s.enabled?'enabled':'disabled')}}</td><td><RouterLink :to="{path:base+'/salary-templates/'+s.id,query:{companyId:company}}">{{t('edit')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="5" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{r('templateHint')}}</p></section></template>
