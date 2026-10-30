<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePayrollPage} from '../payrollPage'
import {chooseCompany,validMonth} from '../payrollUi'
import {storeToday} from '../time'
import type {PayrollHomeData} from '../payrollTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),id=crypto.randomUUID(),{api,r,t,base,current,loading,busy,failure,notice,load,write}=usePayrollPage(),company=ref(''),month=ref(storeToday().slice(0,7)),name=ref('')
onMounted(()=>void load(()=>api().get<PayrollHomeData>('/payroll-home'),h=>{company.value=chooseCompany(h,String(route.query.companyId||''));name.value=h.companies.find(c=>c.id===company.value)?.name||''}))
function save(){if(!company.value||!validMonth(month.value)){failure.value=r('invalid');return}void write(()=>api().put('/payroll-batches/'+id,{companyId:company.value,month:month.value}),async()=>{if(current())await router.push(base.value+'/payroll-batches/'+id)})}
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{r('newBatch')}}</h2><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><form v-if="company&&!loading" @submit.prevent="save"><p>{{r('company')}}: {{name}}</p><label>{{r('month')}}<input v-model="month" type="month" min="2000-01" max="2100-12" required :disabled="busy"></label><p class="wf-hint">{{r('autoBatchHint')}} {{r('monthHint')}} {{r('supportHint')}}</p><footer class="wf-form-actions"><RouterLink class="wf-button" :to="{path:base+'/payroll-batches',query:{companyId:company}}">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{busy?t('saving'):r('newBatch')}}</button></footer></form></section></template>
