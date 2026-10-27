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
<template><section class="storage-panel storage-mode-settings"><h2>{{t('operationMode')}}</h2><div class="storage-mode-body"><p class="cards-muted">{{t('modeHint')}}</p><form class="storage-mode-form" @submit.prevent="save"><label><span class="storage-mode-label">{{t('operationMode')}}</span><select v-model="mode" :disabled="disabled"><option value="customer_verification">{{t('verificationMode')}}</option><option value="staff_registration">{{t('registrationMode')}}</option></select></label><button :disabled="disabled||mode===(home.operationMode??'customer_verification')">{{t('saveMode')}}</button></form></div></section></template>
