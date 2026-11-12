<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import type {Company} from '../types'
const {api,home,store,current}=useWorkforce(),route=useRoute(),router=useRouter(),{t,error}=useWorkforceText(),draft=ref<Company|null>(null),loading=ref(true),busy=ref(false),failure=ref('')
onMounted(async()=>{if(!home.value?.headOffice){loading.value=false;return}try{const data=await api().get<Company[]>('/companies');if(current())draft.value=data.find(c=>c.id===route.params.companyId)||null}catch(e){if(current())failure.value=error(e)}finally{if(current())loading.value=false}})
async function save(){if(!draft.value||busy.value)return;busy.value=true;failure.value='';try{await api().put(`/companies/${draft.value.id}`,{expectedVersion:draft.value.version,registrationNo:draft.value.registrationNo,payDay:draft.value.payDay,enabled:draft.value.enabled});if(current())await router.push(`/stores/${store.value}/workforce/companies`)}catch(e){if(current())failure.value=error(e)}finally{if(current())busy.value=false}}
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{draft?.name||t('company')}}</h2><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="loading" role="status">{{t('loading')}}</p><form v-else-if="draft&&home?.headOffice" @submit.prevent="save"><fieldset :disabled="busy"><div class="wf-grid"><label>{{t('registrationNo')}}<input v-model="draft.registrationNo" maxlength="40"></label><label>{{t('payDay')}}<input v-model.number="draft.payDay" type="number" required min="1" max="28"></label></div><label class="wf-check"><input v-model="draft.enabled" type="checkbox">{{t('enabled')}}</label></fieldset><footer class="wf-form-actions"><RouterLink class="wf-button" :to="`/stores/${store}/workforce/companies`">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{t(busy?'saving':'save')}}</button></footer></form><p v-else-if="!loading" role="alert">{{t('notFound')}}</p></section></template>
