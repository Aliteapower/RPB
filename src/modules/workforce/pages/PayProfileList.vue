<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePayrollPage} from '../payrollPage'
import {chooseCompany} from '../payrollUi'
import type {PayrollHomeData,PayProfile,PayProfilePage} from '../payrollTypes'
import type {Option} from '../types'
import CdacBulkDialog from '../components/CdacBulkDialog.vue'
import {useCdacText} from '../cdac'
import {useCdacBulkText} from '../cdacBulk'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),{api,r,t,base,loading,failure,load}=usePayrollPage(),company=ref(''),companies=ref<PayrollHomeData['companies']>([]),items=ref<PayProfile[]>([])
const people=ref<Option[]>([]),bulk=ref(false),{d}=useCdacText(),{b}=useCdacBulkText()
function refresh(){items.value=[];people.value=[];void load(()=>{const c=chooseCompany({companies:companies.value},company.value);return api().get<PayProfilePage>('/pay-profiles?companyId='+encodeURIComponent(c))},v=>{items.value=v.items;people.value=v.employees})}
function changeCompany(){bulk.value=false;void router.replace({query:{...route.query,companyId:company.value}});refresh()}
onMounted(async()=>{await load(()=>api().get<PayrollHomeData>('/payroll-home'),h=>{company.value=chooseCompany(h,String(route.query.companyId||''));companies.value=h.companies});if(company.value)refresh()})
</script>
<template><section class="wf-panel"><header class="wf-toolbar"><h2>{{r('profiles')}}</h2><button v-if="company" type="button" :disabled="loading||!people.length" @click="bulk=true">{{b('title')}}</button><RouterLink v-if="company" class="wf-button primary" :to="{path:base+'/pay-profiles/new',query:{companyId:company}}">{{r('newProfile')}}</RouterLink></header><div v-if="companies.length" class="wf-filters"><label>{{r('company')}}<select v-model="company" @change="changeCompany"><option v-for="c in companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label></div><PhaseStatus :failure="failure" :loading="loading"/><div v-if="company&&!loading" class="wf-table-wrap"><table><thead><tr><th>{{r('employee')}}</th><th>{{r('effectiveFrom')}}</th><th>{{r('basis')}}</th><th>{{r('rate')}}</th><th>{{d('status')}}</th><th></th></tr></thead><tbody><tr v-for="s in items" :key="s.id"><td>{{s.employeeName}}</td><td>{{s.effectiveFrom}}</td><td>{{r(s.rule.basis)}}</td><td>{{s.rule.rate}}</td><td>{{d(s.cdac?.status||'legacy')}}</td><td><RouterLink :to="{path:base+'/pay-profiles/'+s.id,query:{companyId:company}}">{{t('edit')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="6" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><p class="wf-hint">{{r('recurringHint')}} {{r('profileHint')}}</p><CdacBulkDialog v-if="bulk" :key="company" :company-id="company" :employees="people" @close="bulk=false" @saved="bulk=false;refresh()"/></section></template>
