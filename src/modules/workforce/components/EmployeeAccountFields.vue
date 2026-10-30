<script setup lang="ts">
import {ref,watch} from 'vue'
import type {EmployeeWrite,Option} from '../types'
import {useWorkforceText} from '../text'
const props=defineProps<{draft:EmployeeWrite;accounts:Option[]}>(),mode=ref('none'),{t}=useWorkforceText()
watch(mode,()=>{props.draft.accountId=null;props.draft.username='';props.draft.password=''})
</script>
<template><fieldset><legend>{{t('account')}}</legend><label>{{t('account')}}<select v-model="mode"><option value="none">{{t('noAccount')}}</option><option value="bind">{{t('bindAccount')}}</option><option value="create">{{t('createAccount')}}</option></select></label><template v-if="mode==='bind'"><label>{{t('account')}}<select v-model="draft.accountId" required><option :value="null" disabled>{{t('select')}}</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select></label><p class="wf-hint">{{t('accountHint')}}</p></template><template v-if="mode==='create'"><div class="wf-grid"><label>{{t('username')}}<input v-model="draft.username" required maxlength="40" pattern="[A-Za-z0-9._-]+" autocomplete="off"></label><label>{{t('password')}}<input v-model="draft.password" required type="password" minlength="6" maxlength="6" pattern="[A-Za-z0-9]{6}" autocomplete="new-password" :placeholder="t('passwordHint')"></label></div><p class="wf-hint">{{t('clockHint')}}</p></template></fieldset></template>
