<script setup lang="ts">
import {ref,watch} from 'vue'
import type {Home} from '../types'
import type {StorageApi} from '../api'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;api:StorageApi;disabled:boolean}>(),emit=defineEmits<{command:[()=>Promise<unknown>]}>(),{t}=useMerchantStorageText()
const mode=ref('customer_verification')
watch(()=>[props.home.operationMode,props.home.settingsVersion],()=>{mode.value=props.home.operationMode??'customer_verification'},{immediate:true})
function save(){const command={operationMode:mode.value,version:props.home.settingsVersion??0};emit('command',()=>props.api.command('/settings',command))}
</script>
<template><section class="storage-panel"><h2>{{t('operationMode')}}</h2><p>{{t('modeHint')}}</p><form @submit.prevent="save"><label>{{t('operationMode')}}<select v-model="mode" :disabled="disabled"><option value="customer_verification">{{t('verificationMode')}}</option><option value="staff_registration">{{t('registrationMode')}}</option></select></label><button :disabled="disabled||mode===(home.operationMode??'customer_verification')">{{t('saveMode')}}</button></form></section></template>
