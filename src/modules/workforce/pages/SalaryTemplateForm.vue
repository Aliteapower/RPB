<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePayrollPage} from '../payrollPage'
import {blankRule,chooseCompany,copy,validRule} from '../payrollUi'
import type {PayrollHomeData,SalaryTemplate} from '../payrollTypes'
import PayRuleFields from '../components/PayRuleFields.vue'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),editing=!!route.params.id,id=String(route.params.id||crypto.randomUUID()),{api,r,t,base,current,loading,busy,failure,notice,load,write}=usePayrollPage(),company=ref(''),found=ref(false),draft=reactive({expectedVersion:0,name:'',enabled:true,rule:blankRule()})
onMounted(()=>void load(async()=>{const h=await api().get<PayrollHomeData>('/payroll-home'),c=chooseCompany(h,String(route.query.companyId||''));return {c,list:editing?await api().get<SalaryTemplate[]>('/salary-templates?companyId='+c):[]}},v=>{company.value=v.c;if(editing){const s=v.list.find(x=>x.id===id);if(!s){failure.value=t('notFound');return}Object.assign(draft,{expectedVersion:s.version,name:s.name,enabled:s.enabled,rule:copy(s.rule)})}found.value=true}))
function save(){if(!company.value||!found.value)return;if(!draft.name.trim()||draft.name.length>100||!validRule(draft.rule)){failure.value=r('invalid');return}void write(()=>api().put('/salary-templates/'+id,{...draft,name:draft.name.trim(),companyId:company.value}),async()=>{if(current())await router.push({path:base.value+'/salary-templates',query:{companyId:company.value}})})}
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{r(editing?'editTemplate':'newTemplate')}}</h2><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><form v-if="found&&!loading" @submit.prevent="save"><fieldset :disabled="busy"><label>{{r('name')}}<input v-model="draft.name" required maxlength="100"></label><PayRuleFields :rule="draft.rule" :disabled="busy"/><label class="wf-check"><input v-model="draft.enabled" type="checkbox">{{r('enabled')}}</label></fieldset><p class="wf-hint">{{r('templateHint')}} {{r('supportHint')}}</p><footer class="wf-form-actions"><RouterLink class="wf-button" :to="{path:base+'/salary-templates',query:{companyId:company}}">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{busy?t('saving'):t('save')}}</button></footer></form></section></template>
