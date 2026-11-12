<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import type {Company} from '../types'
const {api,home,store,current}=useWorkforce(),{t,error}=useWorkforceText(),items=ref<Company[]>([]),loading=ref(true),failure=ref('')
onMounted(async()=>{if(!home.value?.headOffice){loading.value=false;return}try{const data=await api().get<Company[]>('/companies');if(current())items.value=data}catch(e){if(current())failure.value=error(e)}finally{if(current())loading.value=false}})
</script>
<template><section v-if="home?.headOffice" class="wf-panel"><h2>{{t('companies')}}</h2><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="loading" role="status">{{t('loading')}}</p><div v-else class="wf-table-wrap"><table><thead><tr><th>{{t('company')}}</th><th>{{t('registrationNo')}}</th><th>{{t('payDay')}}</th><th>{{t('status')}}</th><th>{{t('manage')}}</th></tr></thead><tbody><tr v-for="c in items" :key="c.id"><td>{{c.name}}</td><td>{{c.registrationNo||'—'}}</td><td>{{c.payDay}} {{t('day')}}</td><td><span class="wf-badge">{{t(c.enabled?'enabledStatus':'disabled')}}</span></td><td><RouterLink :to="`/stores/${store}/workforce/companies/${c.id}`">{{t('manage')}}</RouterLink></td></tr><tr v-if="!items.length"><td colspan="5" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div></section><p v-else role="alert">{{t('noAccess')}}</p></template>
