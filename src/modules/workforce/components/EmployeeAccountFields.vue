<script setup lang="ts">
import {ref,watch} from 'vue'
import type {EmployeeWrite,Option} from '../types'
import {useWorkforceText} from '../text'
import {useI18n} from 'vue-i18n'
import {employeeLoginText} from '../employeeLogin'
import EmployeePasswordInput from './EmployeePasswordInput.vue'
const props=defineProps<{draft:EmployeeWrite;accounts:Option[]}>(),mode=ref('none'),{t}=useWorkforceText()
const {locale}=useI18n(),x=(key:Parameters<typeof employeeLoginText>[0])=>employeeLoginText(key,locale.value.startsWith('en'))
watch(mode,()=>{props.draft.accountId=null;props.draft.username='';props.draft.password=''})
watch([mode,()=>props.draft.employeeNo],([value,number])=>{if(value==='create')props.draft.username=number})
</script>
<template><fieldset><legend>{{t('account')}}</legend><label>{{t('account')}}<select v-model="mode"><option value="none">{{t('noAccount')}}</option><option value="bind">{{t('bindAccount')}}</option><option value="create">{{t('createAccount')}}</option></select></label><template v-if="mode==='bind'"><label>{{t('account')}}<select v-model="draft.accountId" required><option :value="null" disabled>{{t('select')}}</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select></label><p class="wf-hint">{{t('accountHint')}}</p></template><template v-if="mode==='create'"><div class="wf-grid"><label>{{x('numberLogin')}}<input :value="draft.employeeNo" readonly required maxlength="40" pattern="[A-Za-z0-9._-]+" autocomplete="off"></label><EmployeePasswordInput v-model="draft.password" :label="t('password')" required/></div><p class="wf-hint">{{x('numberHint')}}</p><p class="wf-hint">{{t('clockHint')}}</p></template></fieldset></template>
