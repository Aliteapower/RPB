<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import type {Administrator} from '../types'
const {api,home,store,current}=useWorkforce(),{t,error}=useWorkforceText(),items=ref<Administrator[]>([]),loading=ref(true),failure=ref('')
onMounted(async()=>{if(!home.value?.headOffice){loading.value=false;return}try{const data=await api().get<Administrator[]>('/administrators');if(current())items.value=data}catch(e){if(current())failure.value=error(e)}finally{if(current())loading.value=false}})
function companyName(id:string|null){return home.value?.companies.find(c=>c.id===id)?.name||t('payrollOff')}
</script>
<template><section v-if="home?.headOffice" class="wf-panel"><header class="wf-toolbar"><h2>{{t('administrators')}}</h2><RouterLink class="wf-button primary" :to="`/stores/${store}/workforce/administrators/edit`">{{t('grant')}}</RouterLink></header><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="loading" role="status">{{t('loading')}}</p><div v-else class="wf-table-wrap"><table><thead><tr><th>{{t('name')}}</th><th>{{t('employeeManage')}}</th><th>{{t('payrollCompany')}}</th><th>{{t('manage')}}</th></tr></thead><tbody><tr v-for="a in items" :key="a.accountId"><td>{{a.name}}</td><td>{{t(a.employeeManage?'enabledStatus':'payrollOff')}}</td><td>{{companyName(a.payrollCompanyId)}}</td><td><RouterLink :to="{path:`/stores/${store}/workforce/administrators/edit`,query:{account:a.accountId}}">{{t('manage')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="4" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div></section><p v-else role="alert">{{t('noAccess')}}</p></template>
